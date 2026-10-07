package org.rsmod.content.bosses.giantmole

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
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class MoleMapTest {
    @Test fun `burrow destinations and cave entrance are walkable on the packed cave map`() {
        val collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            for (mz in 80..81) {
                val square = MapSquareKey(27, mz)
                val group = (27 shl 8) or mz
                val terrain = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (plane in 0..3) for (x in 1728 until 1792 step 8) for (z in mz * 64 until mz * 64 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, plane)
                }
                GameMapDecoder.putMaps(collision, square, terrain)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, terrain, locs)
                assertTrue(locs.spawns.isNotEmpty(), "The test needs actual cave scenery.")
            }
        } finally { cache.close() }
        val spawn = CoordGrid(1759, 5189)
        val entrance = CoordGrid(1752, 5237)
        val finder = RouteFinding(collision, searchMapSize = 256, ringBufferSize = 16384)
        val destinations = GiantMole.openBurrowSites(spawn, 3, collision)
        assertTrue(destinations.size >= 3, "Need several reachable 3x3 destinations: $destinations")
        assertTrue(destinations.all { it.chebyshevDistance(spawn) >= 8 })
        for (destination in destinations) {
            assertTrue(GiantMole.openBurrowSites(destination, 3, collision).isNotEmpty(), "Mole cannot burrow back from $destination")
            for (dx in 0..2) for (dz in 0..2) assertTrue(GiantMole.openLanding(collision, destination.translate(dx, dz)))
            val route = finder.findRoute(level = 0, srcX = entrance.x, srcZ = entrance.z,
                destX = destination.x, destZ = destination.z, destWidth = 3, destLength = 3,
                locShape = -2, moveNear = false)
            assertTrue(route.success, "Public entrance cannot reach Mole landing $destination")
        }
        assertTrue(GiantMole.openLanding(collision, entrance), "Public entrance must not land inside scenery.")
    }
}
