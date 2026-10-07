package org.rsmod.content.bosses.giants

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GiantMapTest {
    private val collision = CollisionFlagMap()

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            val squares = GiantBoss.entries.flatMap { listOf(it.entry, it.exit) }.map { it.x / 64 to it.z / 64 }.toSet()
            for ((mx, mz) in squares) {
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8) for (z in mz * 64 until (mz + 1) * 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                val square = MapSquareKey(mx, mz)
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
            }
        } finally { cache.close() }
    }

    @Test fun `both entrances exits ledge landings and spawn footprints are open native ground`() {
        val points = GiantBoss.entries.flatMap { listOf(it.entry, it.exit) }.toMutableList()
        points += listOf(CoordGrid(3092, 9804), CoordGrid(3092, 9807), CoordGrid(3218, 9931), CoordGrid(3224, 9932), CoordGrid(3221, 9937))
        for (boss in GiantBoss.entries) for (x in 0 until if (boss == GiantBoss.OBOR) 2 else 3) {
            for (z in 0 until if (boss == GiantBoss.OBOR) 2 else 3) points += boss.spawn.translate(x, z)
        }
        val blocked = points.filter { collision[it.x, it.z, it.level] and WALK_BLOCK != 0 }
        val near = blocked.associateWith { point -> buildList {
            for (x in -3..3) for (z in -3..3) {
                val p = point.translate(x, z)
                if (collision[p.x, p.z, p.level] and WALK_BLOCK == 0) add(p)
            }
        } }
        assertTrue(blocked.isEmpty(), "Blocked giant arena tiles: $blocked; open nearby $near")
    }

    @Test fun `Obor lower arena connects the fight chest and climbing rocks`() {
        reachable(CoordGrid(3092, 9804), CoordGrid(3091, 9797), 2, 2)
        reachable(CoordGrid(3092, 9804), CoordGrid(3085, 9797), 1, 1)
        reachable(GiantBoss.OBOR.entry, CoordGrid(3092, 9807), 1, 1)
    }

    @Test fun `Obor pursues across native ground each tick without double movement`() {
        val movement = NpcMovementProcessor(collision, StepFactory(collision), EventBus())
        val npc = Npc(GiantBoss.OBOR.npc, GiantBoss.OBOR.spawn)
        val player = Player().apply { coords = CoordGrid(3092, 9804) }
        val before = GiantRules.distance(npc.coords, npc.size, player.coords)
        repeat(6) { tick ->
            npc.currentMapClock = 100 + tick
            npc.processedMapClock = 100 + tick
            npc.previousCoords = npc.coords
            val start = npc.coords
            npc.abortRoute()
            pursueObor(npc, player, movement)
            assertTrue(start.chebyshevDistance(npc.coords) in 0..1)
            val firstPass = npc.coords
            pursueObor(npc, player, movement)
            assertEquals(firstPass, npc.coords)
        }
        assertTrue(before > 1)
        assertEquals(1, GiantRules.distance(npc.coords, npc.size, player.coords))
        val stopped = npc.coords
        player.coords = CoordGrid(3087, 9802)
        npc.currentMapClock++
        npc.processedMapClock++
        npc.movementLocked = true
        pursueObor(npc, player, movement)
        assertEquals(stopped, npc.coords)
        npc.movementLocked = false
        player.coords = CoordGrid(3087, 9802, 1)
        pursueObor(npc, player, movement)
        assertEquals(stopped, npc.coords)
        npc.removeBlockWalkCollision(collision, npc.coords)
    }

    @Test fun `Bryophyta arrival connects the boss axe log chest and exit`() {
        val entry = GiantBoss.BRYOPHYTA.entry
        reachable(entry, GiantBoss.BRYOPHYTA.spawn, 3, 3)
        reachable(entry, CoordGrid(3228, 9932), 1, 1)
        reachable(entry, CoordGrid(3229, 9934), 1, 1)
        reachable(CoordGrid(3221, 9931), CoordGrid(3212, 9937), 2, 2)
    }

    @Test fun `growthlings occupy one tile and ordinary melee requires an adjacent tile`() {
        val npc = Npc("npc.gb_growthling", CoordGrid(3218, 9931))
        val player = Player().apply { coords = npc.coords.translateX(2) }
        val bounds = BoundValidator(collision)
        assertEquals(1, npc.size)
        assertFalse(bounds.touches(player.avatar, npc.avatar))
        player.coords = npc.coords.translateX(1)
        assertTrue(bounds.touches(player.avatar, npc.avatar))
    }

    private fun reachable(from: CoordGrid, to: CoordGrid, width: Int, length: Int) {
        val route = RouteFinding(collision).findRoute(level = 0, srcX = from.x, srcZ = from.z,
            destX = to.x, destZ = to.z, destWidth = width, destLength = length, locShape = -2, moveNear = false)
        assertTrue(route.success, "No native path $from -> $to ($width x $length)")
    }

    private companion object {
        const val WALK_BLOCK = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR
    }
}
