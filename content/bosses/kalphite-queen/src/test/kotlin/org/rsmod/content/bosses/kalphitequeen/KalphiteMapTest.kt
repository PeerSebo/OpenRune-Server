package org.rsmod.content.bosses.kalphitequeen

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
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

@ResourceLock("ServerCacheManager")
class KalphiteMapTest {
    @Test fun `all four public rope landings are walkable and have an unobstructed step out`() {
        val collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            for ((mx, mz) in listOf(50 to 48, 54 to 148)) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val terrain = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (plane in 0..3) for (x in mx * 64 until mx * 64 + 64 step 8) for (z in mz * 64 until mz * 64 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, plane)
                }
                GameMapDecoder.putMaps(collision, square, terrain)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, terrain, locs)
                assertTrue(locs.spawns.isNotEmpty(), "The test requires packed native scenery.")
            }
        } finally { cache.close() }
        val steps = StepValidator(collision)
        for (destination in listOf(KalphiteLairAccess.SURFACE, KalphiteLairAccess.UPPER_LAIR,
            KalphiteLairAccess.QUEEN_LAIR, KalphiteLairAccess.CHAMBER_TOP)) {
            assertFalse(collision.queenTileBlocked(destination), "Rope lands inside scenery at $destination")
            val canLeave = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) ->
                steps.canTravel(destination.level, destination.x, destination.z, dx, dz, 1, 0, CollisionStrategy.Normal)
            }
            assertTrue(canLeave, "Rope landing is enclosed by collision at $destination")
        }
    }
}
