package org.rsmod.content.bosses.derangedarchaeologist

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

@ResourceLock("ServerCacheManager")
class DerangedMapTest {
    @Test fun `trunk arrival floors avoid native scenery and southern arrival reaches the arena`() {
        val collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            val mx = 57
            for (mz in 57..58) {
                val group = (mx shl 8) or mz
                val terrain = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (plane in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8) for (z in mz * 64 until (mz + 1) * 64 step 8) {
                    collision.allocateIfAbsent(x, z, plane)
                }
                val square = MapSquareKey(mx, mz)
                GameMapDecoder.putMaps(collision, square, terrain)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, terrain, locs)
                assertTrue(locs.spawns.isNotEmpty(), "The test needs actual Fossil Island scenery.")
            }
        } finally { cache.close() }

        val steps = StepValidator(collision)
        for (origin in listOf(CoordGrid(3682, 3717), CoordGrid(3682, 3715))) {
            val landing = DerangedAccess.crossingDestination(origin)
            assertTrue(DerangedAccess.openLanding(collision, landing), "Trunk lands inside scenery at $landing")
            assertTrue(listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) ->
                steps.canTravel(landing.level, landing.x, landing.z, dx, dz, 1, 0, CollisionStrategy.Normal)
            }, "Trunk arrival cannot step out at $landing")
        }
        val route = RouteFinding(collision).findRoute(level = 0, srcX = 3682, srcZ = 3715,
            destX = 3683, destZ = 3706, locShape = -2, moveNear = false)
        assertTrue(route.success, "Southern trunk arrival cannot reach Deranged archaeologist")
    }
}
