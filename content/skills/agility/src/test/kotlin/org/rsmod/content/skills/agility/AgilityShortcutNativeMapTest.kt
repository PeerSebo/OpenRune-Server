package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.skills.agility.shortcuts.AgilityShortcuts
import org.rsmod.content.skills.agility.shortcuts.scripts.DraynorManorFenceScript
import org.rsmod.game.loc.*
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgilityShortcutNativeMapTest {
    private val collision = CollisionFlagMap()
    private val locs = mutableListOf<BoundLocInfo>()

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            val sides = AgilityShortcuts.all.flatMap { listOf(it.sideA, it.sideB) } +
                listOf(DraynorManorFenceScript.West, DraynorManorFenceScript.East)
            val squares = sides.flatMap { p -> listOf(p, p.translate(-3, -3), p.translate(3, 3)) }
                .map { it.mx to it.mz }.toSet()
            for ((mx, mz) in squares) {
                val group = (mx shl 8) or mz
                val tileBytes = cache.data(MAPS, group, 0) ?: continue
                val locBytes = cache.data(MAPS, group, 1) ?: continue
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileBytes))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(locBytes))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                    for (z in mz * 64 until (mz + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, MapSquareKey(mx, mz), tiles)
                GameMapDecoder.putLocs(builder, collision, MapSquareKey(mx, mz), tiles, spawns)
            }
            for ((packed, zone) in builder.zoneBuilders) {
                val base = ZoneKey(packed).toCoords()
                for (entry in zone.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val entity = LocEntity(entry.intValue)
                    val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                    locs += BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id)))
                }
            }
        } finally { cache.close() }
    }

    @Test fun `every shortcut lands on open native floor on both sides`() {
        val errors = mutableListOf<String>()
        for (shortcut in AgilityShortcuts.all) for (side in listOf(shortcut.sideA, shortcut.sideB)) {
            if (blocked(side)) {
                val nearby = buildList {
                    for (dx in -2..2) for (dz in -2..2) {
                        val point = side.translate(dx, dz)
                        if (!blocked(point)) add(point)
                    }
                }
                errors += "${shortcut.name} level ${shortcut.level} blocked $side; nearby floor $nearby"
            }
        }
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    @Test fun `broken manor fence can be reached from both sides and lands on open ground`() {
        val fence = locs.single { it.id == DraynorManorFenceScript.Fence.asRSCM() }
        for (side in listOf(DraynorManorFenceScript.West, DraynorManorFenceScript.East)) {
            assertTrue(!blocked(side), "Blocked fence landing $side")
            assertTrue(RouteFinding(collision).findRoute(level = side.level, srcX = side.x, srcZ = side.z,
                destX = fence.x, destZ = fence.z, locShape = fence.shapeId,
                locAngle = fence.angleId, moveNear = false).success, "Fence unreachable from $side")
        }
    }

    @Test fun `both shortcut landings can reach a matching native click target`() {
        val errors = mutableListOf<String>()
        for (shortcut in AgilityShortcuts.all) {
            val ids = shortcut.locs.map { it.asRSCM() }.toSet()
            for (side in listOf(shortcut.sideA, shortcut.sideB)) {
                val near = locs.filter { it.id in ids && it.level == side.level &&
                    it.coords.chebyshevDistance(side) <= 16 }
                val reachable = near.any { loc ->
                    if ("loc.lumbridge_sc_stepstone" in shortcut.locs) {
                        side.chebyshevDistance(loc.coords) <= 3 &&
                            RayCastValidator(collision).hasLineOfSight(side, loc.coords,
                                destWidth = loc.adjustedWidth, destLength = loc.adjustedLength)
                    } else {
                        RouteFinding(collision).findRoute(level = side.level, srcX = side.x, srcZ = side.z,
                            destX = loc.x, destZ = loc.z, destWidth = loc.width, destLength = loc.length,
                            locShape = loc.shapeId, locAngle = loc.angleId,
                            blockAccessFlags = loc.forceApproachFlags, moveNear = false).success
                    }
                }
                if (!reachable) errors += "${shortcut.name} level ${shortcut.level} cannot click from $side; targets $near"
            }
        }
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    private fun blocked(tile: CoordGrid): Boolean = collision[tile.x, tile.z, tile.level] and
        (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0
}
