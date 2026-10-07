package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Paths

object MapPlaneAudit {

    private const val MAX_MAP_GROUP = 25286

    private const val LINK_BELOW = MapTileSimpleDefinition.LINK_BELOW

    @JvmStatic
    fun main(args: Array<String>) {
        val liveDir =
            Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get(".data", "cache", "LIVE"))
                .normalize()
        GameValProvider.load()
        val cache = Cache.load(liveDir)

        val tile = System.getProperty("tile")
        when {
            tile != null -> dumpTile(cache, tile, System.getProperty("radius")?.toIntOrNull() ?: 1)
            System.getProperty("zones") != null -> auditZones(cache)
            else -> audit(cache, System.getProperty("blocking") != null)
        }
    }

    /**
     * Unallocated zones read fully blocked (-1). Skipping a negative-plane write can remove a
     * zone's only allocation, so audit allocation as well as flags.
     */
    private fun auditZones(cache: Cache) {
        val provider = OsrsCacheProvider(cache, 240)
        provider.init()
        val solid = provider.objects.mapValues { (_, type) -> type.solid != 0 }

        val oldMapZones = HashSet<Int>()
        val newMapZones = HashSet<Int>()
        val guardedMapZones = HashSet<Int>()
        val oldZonesFromWrap = HashSet<Int>()
        val oldZonesFromReal = HashSet<Int>()
        val locZonesAny = HashSet<Int>()
        val locZonesSolid = HashSet<Int>()

        for (groupId in cache.archives(MAPS)) {
            if (groupId > MAX_MAP_GROUP) continue
            val mapData = cache.data(MAPS, groupId, 0) ?: continue
            val square = MapTileDecoder.decode(InlineByteBuf(mapData))
            val squareX = (groupId shr 8) shl 6
            val squareZ = (groupId and 0xFF) shl 6

            for (level in 0..3) {
                for (lx in 0 until 64) {
                    for (lz in 0 until 64) {
                        // ROOF-only tiles also allocate zones in putMaps.
                        val flags = square[lx, lz, level].toInt()
                        if (flags == 0) continue
                        val absX = squareX + lx
                        val absZ = squareZ + lz
                        val old = if ((flags and LINK_BELOW) != 0) level - 1 else level
                        val oldZone = zoneIndex(absX, absZ, old)
                        oldMapZones += oldZone
                        if (old < 0) oldZonesFromWrap += oldZone else oldZonesFromReal += oldZone
                        val new = proposedRule(square, lx, lz, level, (flags and LINK_BELOW) != 0)
                        newMapZones += zoneIndex(absX, absZ, new)
                        if (new >= 0) guardedMapZones += zoneIndex(absX, absZ, new)
                    }
                }
            }

            val locData = cache.data(MAPS, groupId, 1) ?: continue
            val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns
            for (i in 0 until spawns.size) {
                val def = MapLocDefinition(spawns.getLong(i))
                val legacy =
                    (square[def.localX, def.localZ, def.level].toInt() and LINK_BELOW) != 0 ||
                        (def.level < 3 &&
                            (square[def.localX, def.localZ, def.level + 1].toInt() and
                                LINK_BELOW) != 0)
                val level = proposedRule(square, def.localX, def.localZ, def.level, legacy)
                if (level < 0) continue
                val zone = zoneIndex(squareX + def.localX, squareZ + def.localZ, level)
                locZonesAny += zone
                if (solid[def.id] == true) locZonesSolid += zone
            }
        }

        fun coords(zone: Int): String =
            "(${(zone and 0x7FF) shl 3}, ${((zone shr 11) and 0x7FF) shl 3}, ${zone ushr 22})"

        println(
            "putMaps zone allocations - old rule: ${oldMapZones.size}, as shipped: ${newMapZones.size}"
        )
        println()

        // Shifted content can leave unallocated zones, which read as fully blocked (-1).
        val lostToShift = HashSet(oldMapZones).also { it.removeAll(newMapZones) }
        val shiftAfterLocs = HashSet(lostToShift).also { it.removeAll(locZonesAny) }
        val shiftAfterSolid = HashSet(lostToShift).also { it.removeAll(locZonesSolid) }
        println("SHIPPED: zones that lose their putMaps allocation: ${lostToShift.size}")
        println("  by world plane: ${lostToShift.groupingBy { it ushr 22 }.eachCount()}")
        println("  still unallocated after locs are placed:")
        println("    upper bound (locs allocate nothing): ${lostToShift.size}")
        println("    counting locs with solid != 0:       ${shiftAfterSolid.size}")
        println("    lower bound (every loc allocates):   ${shiftAfterLocs.size}")
        val shiftLowPlane = lostToShift.filter { (it ushr 22) <= 1 }
        println("  on world plane <= 1 (the reachable ones): ${shiftLowPlane.size}")
        shiftLowPlane.forEach { println("    ${coords(it)}") }
        println()

        val wrapOnly = HashSet(oldZonesFromWrap).also { it.removeAll(oldZonesFromReal) }
        val lostToGuard = HashSet(newMapZones).also { it.removeAll(guardedMapZones) }
        val guardAfterLocs = HashSet(lostToGuard).also { it.removeAll(locZonesAny) }
        println("NOT SHIPPED - the negative-level guard would additionally de-allocate:")
        println("  zones: ${lostToGuard.size} (${guardAfterLocs.size} with no loc in them either)")
        println("  by world plane: ${lostToGuard.groupingBy { it ushr 22 }.eachCount()}")
        println(
            "  of which reached only via the level wrap: ${lostToGuard.count { it in wrapOnly }}"
        )
        guardAfterLocs.take(10).forEach { println("    e.g. ${coords(it)}") }
    }

    private fun zoneIndex(x: Int, z: Int, level: Int): Int =
        ((x shr 3) and 0x7FF) or (((z shr 3) and 0x7FF) shl 11) or ((level and 0x3) shl 22)

    private fun dumpTile(cache: Cache, spec: String, radius: Int) {
        val parts = spec.split(",").map { it.trim().toInt() }
        val cx = parts[0]
        val cz = parts[1]

        println("tile rule bits around ($cx, $cz), radius $radius")
        println("  bits: 1=BLOCK_MAP_SQUARE 2=LINK_BELOW 4=REMOVE_ROOFS 8=COLOURED")
        println()

        for (z in (cz + radius) downTo (cz - radius)) {
            for (x in (cx - radius)..(cx + radius)) {
                val square = squareOf(cache, x, z) ?: continue
                val flags =
                    (0..3).map { level -> square[x and 0x3F, z and 0x3F, level].toInt() and 0xFF }
                println(
                    "  ($x, $z)  p0=${flags[0]}  p1=${flags[1]}  p2=${flags[2]}  p3=${flags[3]}" +
                        "   linkBelow@p1=${(flags[1] and LINK_BELOW) != 0}"
                )
            }
        }

        println()
        println("loc placements in the same window (archive plane, then both candidate rules):")
        forEachPlacementIn(cache, cx, cz, radius) { p, square ->
            val current = currentRule(square, p.localX, p.localZ, p.level)
            val client = clientRule(square, p.localX, p.localZ, p.level)
            val shipped = proposedRule(square, p.localX, p.localZ, p.level)
            val flag = if (current != shipped) "  <-- MOVED" else ""
            println(
                "  (${p.x}, ${p.z}) archive=${p.level} shape=${p.shape} angle=${p.angle}" +
                    "  old=$current client=$client shipped=$shipped  ${symbolOf(p.id)}$flag"
            )
        }
    }

    private fun audit(cache: Cache, withBlocking: Boolean) {
        val blockWalk: Map<Int, Boolean> =
            if (!withBlocking) {
                emptyMap()
            } else {
                val provider = OsrsCacheProvider(cache, 240)
                provider.init()
                // `solid` is the client-side field the server's `blockWalk` is packed from.
                provider.objects.mapValues { (_, type) -> type.solid != 0 }
            }
        var blockingDisagree = 0
        var blockingDisagreeHigh = 0
        var undergroundHigh = 0
        var surfaceHigh = 0
        var placements = 0
        var disagree = 0
        val byDelta = HashMap<Int, Int>()
        val byLoc = HashMap<Int, Int>()
        val samples = HashMap<Int, Placement>()
        val byArchiveLevel = HashMap<Int, Int>()

        var blockedTiles = 0
        var blockedDisagree = 0
        var negativeCurrent = 0
        var negativeClient = 0
        val linkBelowPerPlane = IntArray(4)
        var droppedCurrent = 0
        var droppedClient = 0
        var droppedProposed = 0
        var proposedDisagree = 0
        val proposedByLevel = HashMap<Int, Int>()
        val movedSymbols = HashSet<String>()
        var raisedByShipped = 0
        val raisedSymbols = HashSet<String>()
        var blockedHighDisagree = 0
        var blockedZeroUnderBridge = 0
        var blockedZeroKeptOnBridge = 0

        for (groupId in cache.archives(MAPS)) {
            if (groupId > MAX_MAP_GROUP) continue
            val mapData = cache.data(MAPS, groupId, 0) ?: continue
            val square = MapTileDecoder.decode(InlineByteBuf(mapData))
            val squareX = (groupId shr 8) shl 6
            val squareZ = (groupId and 0xFF) shl 6

            for (level in 0..3) {
                for (lx in 0 until 64) {
                    for (lz in 0 until 64) {
                        val flags = square[lx, lz, level].toInt()
                        if ((flags and LINK_BELOW) != 0) linkBelowPerPlane[level]++
                        if ((flags and MapTileSimpleDefinition.BLOCK_MAP_SQUARE) == 0) continue
                        blockedTiles++
                        val current = if ((flags and LINK_BELOW) != 0) level - 1 else level
                        val client =
                            if ((square[lx, lz, 1].toInt() and LINK_BELOW) != 0) level - 1
                            else level
                        if (current != client) {
                            blockedDisagree++
                            if (level >= 2) blockedHighDisagree++
                            if (level == 0) blockedZeroUnderBridge++
                        }
                        if (current < 0) negativeCurrent++
                        if (client < 0) negativeClient++
                        // Blocked terrain on plane 0 of a bridged column: the client drops it so
                        // the deck (authored on plane 1, drawn on 0) stays walkable. We keep it.
                        if (
                            level == 0 &&
                                current == 0 &&
                                (square[lx, lz, 1].toInt() and LINK_BELOW) != 0
                        ) {
                            blockedZeroKeptOnBridge++
                        }
                    }
                }
            }

            val locData = cache.data(MAPS, groupId, 1) ?: continue
            val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns
            for (i in 0 until spawns.size) {
                val def = MapLocDefinition(spawns.getLong(i))
                placements++
                val current = currentRule(square, def.localX, def.localZ, def.level)
                val client = clientRule(square, def.localX, def.localZ, def.level)
                val proposed = proposedRule(square, def.localX, def.localZ, def.level)
                if (current < 0) droppedCurrent++
                if (client < 0) droppedClient++
                if (proposed < 0) droppedProposed++
                if (proposed != current) {
                    proposedDisagree++
                    proposedByLevel[def.level] = (proposedByLevel[def.level] ?: 0) + 1
                    movedSymbols += symbolOf(def.id)
                    // The gap-in-the-stack class: p2/p3 carries LINK_BELOW where p1 does not, so
                    // the old rule sank this loc and the shipped rule leaves it up. Same shape as
                    // the Piscarilius bug, inverted - world plane 1 is left empty under it.
                    if (proposed > current) {
                        raisedByShipped++
                        raisedSymbols += symbolOf(def.id)
                    }
                }
                if (current == client) continue
                disagree++
                byDelta[client - current] = (byDelta[client - current] ?: 0) + 1
                val blocking = blockWalk[def.id] == true
                if (blocking) blockingDisagree++
                if (def.level >= 2) {
                    if (blocking) blockingDisagreeHigh++
                    if ((squareZ + def.localZ) >= 6400) undergroundHigh++ else surfaceHigh++
                }
                byLoc[def.id] = (byLoc[def.id] ?: 0) + 1
                byArchiveLevel[def.level] = (byArchiveLevel[def.level] ?: 0) + 1
                samples.putIfAbsent(
                    def.id,
                    Placement(
                        def.id,
                        squareX + def.localX,
                        squareZ + def.localZ,
                        def.level,
                        def.shape,
                        def.angle,
                        def.localX,
                        def.localZ,
                    ),
                )
            }
        }

        println("loc placements decoded: $placements")
        println("placements where the two rules disagree: $disagree")
        println("  by level delta (client - current): $byDelta")
        println("  by archive level: $byArchiveLevel")
        println("  distinct loc types affected: ${byLoc.size}")
        println()
        println(
            "SHIPPED RULE vs today: $proposedDisagree placements move, by archive level $proposedByLevel"
        )
        println(
            "placements dropped (< 0): current=$droppedCurrent client=$droppedClient proposed=$droppedProposed"
        )
        println(
            "  of the moved placements, $raisedByShipped rise a plane (p2/p3 bridged, p1 not) and " +
                "leave world plane 1 empty under them; ${raisedSymbols.size} distinct types"
        )
        raisedSymbols.sorted().forEach { println("    $it") }
        if (withBlocking) {
            println("  of the disagreements, blockWalk types: $blockingDisagree")
            println("  archive level >= 2: blockWalk=$blockingDisagreeHigh")
            println("  archive level >= 2: underground=$undergroundHigh surface=$surfaceHigh")
        }
        println("tiles carrying LINK_BELOW, by plane: ${linkBelowPerPlane.toList()}")
        println()
        println("blocked map tiles: $blockedTiles")
        println(
            "  where the two rules disagree: $blockedDisagree (of those, plane >= 2: $blockedHighDisagree)"
        )
        println("  resolving to a negative level: current=$negativeCurrent client=$negativeClient")
        println("  SHIPPED RULE vs today: $blockedHighDisagree tiles move (plane >= 2 only)")
        println("  plus $negativeCurrent that stopped being written onto plane 3")
        println(
            "  plane-0 blocked tiles on a bridged column (we keep, client drops): $blockedZeroKeptOnBridge"
        )
        println("  plane-0 blocked tiles where the rules disagree: $blockedZeroUnderBridge")
        println()
        if (System.getProperty("symbols") != null) {
            println("--- symbols moved by the shipped rule ---")
            movedSymbols.sorted().forEach(::println)
            println("--- end symbols ---")
        }
        println("top affected loc types:")
        byLoc.entries
            .sortedByDescending { it.value }
            .take(40)
            .forEach { (id, count) ->
                val p = samples[id]!!
                println(
                    "  ${count.toString().padStart(5)}x  ${symbolOf(id)}" +
                        "  e.g. (${p.x}, ${p.z}) archive level ${p.level} shape=${p.shape}"
                )
            }
    }

    /** Legacy loc rule: LINK_BELOW on the loc's own plane or the plane above. */
    private fun currentRule(
        square: MapTileSimpleDefinition,
        localX: Int,
        localZ: Int,
        level: Int,
    ): Int {
        val tileFlags = square[localX, localZ, level].toInt()
        val tileAboveFlags =
            if (level >= 3) tileFlags else square[localX, localZ, level + 1].toInt()
        val resolved = if ((tileAboveFlags and LINK_BELOW) != 0) tileAboveFlags else tileFlags
        return if ((resolved and LINK_BELOW) != 0) level - 1 else level
    }

    /** Client rule: read the bridge bit only from plane 1. */
    private fun clientRule(
        square: MapTileSimpleDefinition,
        localX: Int,
        localZ: Int,
        level: Int,
    ): Int = if ((square[localX, localZ, 1].toInt() and LINK_BELOW) != 0) level - 1 else level

    /**
     * Partial-migration candidate: use the plane-1 bridge bit above plane 1, preserving the
     * legacy rule on planes 0 and 1 so no low-plane loc is newly registered or dropped.
     */
    private fun proposedRule(
        square: MapTileSimpleDefinition,
        localX: Int,
        localZ: Int,
        level: Int,
    ): Int {
        if (level <= 1) return currentRule(square, localX, localZ, level)
        return clientRule(square, localX, localZ, level)
    }

    private fun proposedRule(
        square: MapTileSimpleDefinition,
        localX: Int,
        localZ: Int,
        level: Int,
        legacySink: Boolean,
    ): Int {
        val sink =
            if (level <= 1) {
                legacySink
            } else {
                (square[localX, localZ, 1].toInt() and LINK_BELOW) != 0
            }
        return if (sink) level - 1 else level
    }

    private class Placement(
        val id: Int,
        val x: Int,
        val z: Int,
        val level: Int,
        val shape: Int,
        val angle: Int,
        val localX: Int,
        val localZ: Int,
    )

    private fun squareOf(cache: Cache, x: Int, z: Int): MapTileSimpleDefinition? {
        val groupId = ((x shr 6) shl 8) or (z shr 6)
        if (groupId > MAX_MAP_GROUP) return null
        val mapData = cache.data(MAPS, groupId, 0) ?: return null
        return MapTileDecoder.decode(InlineByteBuf(mapData))
    }

    private inline fun forEachPlacementIn(
        cache: Cache,
        cx: Int,
        cz: Int,
        radius: Int,
        action: (Placement, MapTileSimpleDefinition) -> Unit,
    ) {
        val squares = HashSet<Int>()
        for (x in (cx - radius)..(cx + radius)) {
            for (z in (cz - radius)..(cz + radius)) {
                squares += ((x shr 6) shl 8) or (z shr 6)
            }
        }
        for (groupId in squares) {
            if (groupId > MAX_MAP_GROUP) continue
            val mapData = cache.data(MAPS, groupId, 0) ?: continue
            val locData = cache.data(MAPS, groupId, 1) ?: continue
            val square = MapTileDecoder.decode(InlineByteBuf(mapData))
            val squareX = (groupId shr 8) shl 6
            val squareZ = (groupId and 0xFF) shl 6
            val spawns = MapLocListDecoder.decode(InlineByteBuf(locData)).spawns
            for (i in 0 until spawns.size) {
                val def = MapLocDefinition(spawns.getLong(i))
                val absX = squareX + def.localX
                val absZ = squareZ + def.localZ
                if (Math.abs(absX - cx) > radius || Math.abs(absZ - cz) > radius) continue
                action(
                    Placement(
                        def.id,
                        absX,
                        absZ,
                        def.level,
                        def.shape,
                        def.angle,
                        def.localX,
                        def.localZ,
                    ),
                    square,
                )
            }
        }
    }

    private fun symbolOf(id: Int): String =
        runCatching { RSCM.getReverseMapping(RSCMType.LOC, id) }.getOrNull()?.takeIf { it != "-1" }
            ?: "loc:$id"
}
