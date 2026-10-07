package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.definition.type.ObjectType
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Paths

object PohTemplateDump {
    private const val HOTSPOT_CATEGORY = 207
    private const val BUILT_FURNITURE_CATEGORY = 206

    private const val MAX_MAP_GROUP = 25286

    @JvmStatic
    fun main(args: Array<String>) {
        val liveDir =
            Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get(".data", "cache", "LIVE"))
                .normalize()
        val cache = Cache.load(liveDir)
        val provider = OsrsCacheProvider(cache, 240)
        provider.init()
        val objects = provider.objects

        val target = System.getProperty("pohSquare")?.split(",")?.map { it.trim().toInt() }
        if (target != null && target.size == 2) {
            dumpSquare(cache, objects, target[0], target[1])
            return
        }

        println("=== Map squares containing build hotspots (category $HOTSPOT_CATEGORY) ===")
        val hits = mutableListOf<Triple<Int, Int, Int>>()
        for (groupId in cache.archives(MAPS)) {
            if (groupId > MAX_MAP_GROUP) continue
            val locData = cache.data(MAPS, groupId, 1) ?: continue
            val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns

            var hotspots = 0
            for (i in 0 until spawns.size) {
                val loc = MapLocDefinition(spawns.getLong(i))
                if (objects[loc.id]?.category == HOTSPOT_CATEGORY) {
                    hotspots++
                }
            }
            if (hotspots > 0) {
                hits += Triple(groupId, hotspots, spawns.size)
            }
        }

        for ((groupId, hotspots, total) in hits.sortedByDescending { it.second }) {
            val sqX = groupId shr 8
            val sqZ = groupId and 0xFF
            println(
                "square ($sqX,$sqZ) group=$groupId base=(${sqX * 64},${sqZ * 64}) " +
                    "hotspots=$hotspots totalLocs=$total"
            )
        }
        if (hits.isEmpty()) {
            println("(none found)")
        }
    }

    private fun dumpSquare(cache: Cache, objects: Map<Int, ObjectType>, sqX: Int, sqZ: Int) {
        val groupId = (sqX shl 8) or sqZ
        val baseX = sqX * 64
        val baseZ = sqZ * 64
        val locData = cache.data(MAPS, groupId, 1)
        if (locData == null) {
            println("square ($sqX,$sqZ) group=$groupId: NO LOC FILE")
            return
        }

        val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns
        println(
            "=== square ($sqX,$sqZ) group=$groupId base=($baseX,$baseZ): ${spawns.size} locs ==="
        )

        val byZone = sortedMapOf<Int, MutableList<MapLocDefinition>>()
        for (i in 0 until spawns.size) {
            val loc = MapLocDefinition(spawns.getLong(i))
            val zoneKey = (loc.level shl 16) or ((loc.localX / 8) shl 8) or (loc.localZ / 8)
            byZone.getOrPut(zoneKey) { mutableListOf() }.add(loc)
        }

        for ((zoneKey, zoneLocs) in byZone) {
            val level = zoneKey shr 16
            val zx = (zoneKey shr 8) and 0xFF
            val zz = zoneKey and 0xFF
            val notable =
                zoneLocs.count {
                    val category = objects[it.id]?.category
                    category == HOTSPOT_CATEGORY || category == BUILT_FURNITURE_CATEGORY
                }
            println(
                "\n  zone level=$level sourceOffset=(${zx * 8},${zz * 8}) " +
                    "abs=(${baseX + zx * 8},${baseZ + zz * 8}) locs=${zoneLocs.size} " +
                    "hotspotsOrFurniture=$notable"
            )
            for (loc in zoneLocs.sortedWith(compareBy({ it.localX }, { it.localZ }))) {
                val type = objects[loc.id]
                val ops =
                    (0..4)
                        .mapNotNull { i -> type?.actions?.getOpOrNull(i)?.let { "op${i + 1}=$it" } }
                        .joinToString(",")
                println(
                    "    id=${loc.id} \"${type?.name ?: "?"}\" cat=${type?.category ?: -1} " +
                        "ops=[$ops] shape=${loc.shape} angle=${loc.angle} " +
                        "inZone=(${loc.localX % 8},${loc.localZ % 8}) " +
                        "abs=(${baseX + loc.localX},${baseZ + loc.localZ},${loc.level})"
                )
            }
        }
    }
}
