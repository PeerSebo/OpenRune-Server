package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.definition.type.ObjectType
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Paths

object MapLocQuery {

    private const val MAX_MAP_GROUP = 25286

    private class Placement(
        val id: Int,
        val x: Int,
        val z: Int,
        val level: Int,
        val shape: Int,
        val angle: Int,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val liveDir =
            Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get(".data", "cache", "LIVE"))
                .normalize()
        GameValProvider.load()

        val cache = Cache.load(liveDir)
        val provider = OsrsCacheProvider(cache, 240)
        provider.init()
        val objects = provider.objects

        val near = System.getProperty("near")
        val loc = System.getProperty("loc")
        val op = System.getProperty("op")

        when {
            near != null -> queryNear(cache, objects, near, System.getProperty("radius")?.toInt() ?: 5)
            loc != null -> queryLoc(cache, objects, loc)
            op != null -> queryOp(cache, objects, op, System.getProperty("limit")?.toInt() ?: 30)
            System.getProperty("climbables") != null -> climbables(cache, objects)
            else -> {
                println("Pass one of -Dnear=x,z[,level]  -Dloc=<symbol|id>  -Dop=<text>  -Dclimbables")
                println("Optional: -Dradius=N (near), -Dlimit=N (op)")
            }
        }
    }

    private inline fun forEachPlacement(cache: Cache, action: (Placement) -> Unit) {
        for (groupId in cache.archives(MAPS)) {
            if (groupId > MAX_MAP_GROUP) continue
            val locData = cache.data(MAPS, groupId, 1) ?: continue
            val squareX = (groupId shr 8) shl 6
            val squareZ = (groupId and 0xFF) shl 6

            val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns
            for (i in 0 until spawns.size) {
                val def = MapLocDefinition(spawns.getLong(i))
                action(
                    Placement(
                        id = def.id,
                        x = squareX + def.localX,
                        z = squareZ + def.localZ,
                        level = def.level,
                        shape = def.shape,
                        angle = def.angle,
                    )
                )
            }
        }
    }

    private fun symbolOf(id: Int): String =
        runCatching { RSCM.getReverseMapping(RSCMType.LOC, id) }.getOrNull()?.takeIf { it != "-1" }
            ?: "loc:$id"

    private fun describe(objects: Map<Int, ObjectType>, id: Int): String {
        val symbol = symbolOf(id)
        val ops = objectOps(objects, id)
        return if (ops.isEmpty()) symbol else "$symbol [${ops.joinToString(" | ")}]"
    }

    private fun objectOps(objects: Map<Int, ObjectType>, id: Int): List<String> {
        val type = objects[id] ?: return emptyList()
        // EntityOpsDefinition indexes ops from 0: reading 1..5 silently dropped op1, which
        // hid every loc whose only climb option is its first (loc.ladder, loc.spiralstairs).
        return (0..4).mapNotNull { type.actions.getOpOrNull(it) }
    }

    private fun queryNear(cache: Cache, objects: Map<Int, ObjectType>, spec: String, radius: Int) {
        val parts = spec.split(",").map { it.trim().toInt() }
        val (cx, cz) = parts[0] to parts[1]
        val level = parts.getOrNull(2)

        println("Locs within $radius tiles of ($cx, $cz)${level?.let { ", level $it" } ?: ""}:")
        val hits = mutableListOf<Placement>()
        forEachPlacement(cache) { p ->
            if (level != null && p.level != level) return@forEachPlacement
            if (Math.abs(p.x - cx) > radius || Math.abs(p.z - cz) > radius) return@forEachPlacement
            hits += p
        }
        hits.sortedWith(compareBy({ Math.abs(it.x - cx) + Math.abs(it.z - cz) }, { it.id }))
            .forEach { p ->
                println(
                    "  (${p.x}, ${p.z}, ${p.level}) shape=${p.shape} angle=${p.angle}  ${describe(objects, p.id)}"
                )
            }
        if (hits.isEmpty()) println("  (none)")
    }

    private fun queryLoc(cache: Cache, objects: Map<Int, ObjectType>, spec: String) {
        val id = spec.toIntOrNull() ?: runCatching { RSCM.getRSCM(spec) }.getOrNull()
        if (id == null) {
            println("Unknown loc: $spec")
            return
        }
        println("Placements of ${describe(objects, id)}:")
        var count = 0
        forEachPlacement(cache) { p ->
            if (p.id != id) return@forEachPlacement
            count++
            if (count <= 60) println("  (${p.x}, ${p.z}, ${p.level}) shape=${p.shape} angle=${p.angle}")
        }
        if (count > 60) println("  ... and ${count - 60} more")
        println("total: $count")
    }

    private val CLIMB_OPS = setOf("climb", "climb-up", "climb-down", "climb up", "climb down")

    private fun climbables(cache: Cache, objects: Map<Int, ObjectType>) {
        val placements = mutableListOf<Placement>()
        forEachPlacement(cache) { p ->
            val ops = objectOps(objects, p.id)
            if (ops.any { it.lowercase() in CLIMB_OPS }) placements += p
        }

        val occupied = HashSet<Long>(placements.size * 2)
        fun key(x: Int, z: Int, level: Int): Long =
            (level.toLong() shl 34) or (x.toLong() shl 17) or z.toLong()
        for (p in placements) occupied += key(p.x, p.z, p.level)

        fun partnerWithin(p: Placement, levelDelta: Int, radius: Int): Boolean {
            val level = p.level + levelDelta
            if (level !in 0..3) return false
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (key(p.x + dx, p.z + dz, level) in occupied) return true
                }
            }
            return false
        }

        class Stats {
            var total = 0
            var above = 0
            var below = 0
            var sample: Placement? = null
        }

        val byType = HashMap<Int, Stats>()
        for (p in placements) {
            val s = byType.getOrPut(p.id) { Stats() }
            s.total++
            if (partnerWithin(p, 1, 2)) s.above++
            if (partnerWithin(p, -1, 2)) s.below++
            if (s.sample == null) s.sample = p
        }

        println("climbable loc types placed on the map: ${byType.size} (${placements.size} placements)")
        println("columns: placements, % with a climbable above, % with one below")
        println()
        val rows =
            byType.entries.sortedWith(compareByDescending { it.value.total })
        for ((id, s) in rows) {
            val up = s.above * 100 / s.total
            val down = s.below * 100 / s.total
            val p = s.sample!!
            println(
                "${s.total.toString().padStart(4)}  up=${up.toString().padStart(3)}%  down=${down.toString().padStart(3)}%  " +
                    "${describe(objects, id)}  e.g. (${p.x}, ${p.z}, ${p.level})"
            )
        }
    }

    private fun queryOp(cache: Cache, objects: Map<Int, ObjectType>, op: String, limit: Int) {
        val matching =
            objects.keys.filter { id -> objectOps(objects, id).any { it?.equals(op, true) == true } }
                .toHashSet()
        println("${matching.size} loc type(s) with op \"$op\"; counting placements...")

        val counts = HashMap<Int, Int>()
        val sample = HashMap<Int, Placement>()
        forEachPlacement(cache) { p ->
            if (p.id !in matching) return@forEachPlacement
            counts[p.id] = (counts[p.id] ?: 0) + 1
            sample.putIfAbsent(p.id, p)
        }

        val placed = counts.entries.sortedByDescending { it.value }
        println("${placed.size} of them are placed on the map:")
        for ((id, count) in placed.take(limit)) {
            val p = sample[id]!!
            println(
                "  ${count.toString().padStart(4)}x  ${describe(objects, id)}  e.g. (${p.x}, ${p.z}, ${p.level})"
            )
        }
        if (placed.size > limit) println("  ... and ${placed.size - limit} more (raise -Dlimit)")
        val unplaced = matching.size - placed.size
        if (unplaced > 0) println("$unplaced type(s) with the op are never placed.")
    }
}
