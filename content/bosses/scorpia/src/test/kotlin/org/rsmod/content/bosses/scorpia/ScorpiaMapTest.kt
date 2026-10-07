package org.rsmod.content.bosses.scorpia

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
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScorpiaMapTest {
    private lateinit var collision: CollisionFlagMap

    @Test fun `offspring launch checks enforce attack range rather than cave-wide chase range`() {
        val flags = CollisionFlagMap()
        for (x in 3200..3248 step 8) for (z in 10320..10368 step 8) {
            flags.allocateIfAbsent(x, z, 0)
        }
        val offspring = Npc("npc.scorpia_minion", CoordGrid(3224, 10336))
        val ray = RayCastValidator(flags)
        assertEquals(7, offspring.attackRange)
        assertTrue(offspringCanReach(offspring, offspring.coords.translate(7, 0), ray))
        assertFalse(offspringCanReach(offspring, offspring.coords.translate(8, 0), ray))
        assertFalse(offspringCanReach(offspring, offspring.coords.translate(24, 0), ray))
        assertFalse(offspringCanReach(offspring, CoordGrid(3225, 10336, 1), ray))
        assertFalse(offspringCanReach(offspring, offspring.coords, ray))
        offspring.apRangeOverride = 30
        assertFalse(offspringCanReach(offspring, offspring.coords.translate(8, 0), ray))
        offspring.apRangeOverride = 3
        assertFalse(offspringCanReach(offspring, offspring.coords.translate(4, 0), ray))
        flags.add(3226, 10336, 0, CollisionFlag.LOC_PROJ_BLOCKER)
        assertFalse(offspringCanReach(offspring, offspring.coords.translate(3, 0), ray))
    }

    @BeforeAll fun nativeMap() {
        collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            val squares = ScorpiaCave.Passages.flatMap { listOf(it.inside, it.outside) }
                .map { it.x / 64 to it.z / 64 }.toSet()
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

    @Test fun `all six passage landings and full queen footprint use native open ground`() {
        val squares = ScorpiaCave.Passages.flatMap { listOf(it.inside, it.outside) }.toMutableList()
        val queen = Npc("npc.scorpia", ScorpiaCave.QueenSpawn)
        for (x in 0 until queen.size) for (z in 0 until queen.size) squares += queen.coords.translate(x, z)
        val blocked = squares.filter { collision.scorpionBlocked(it) }
        val nearby = blocked.associateWith { tile -> buildList {
            for (dx in -3..3) for (dz in -3..3) {
                val candidate = tile.translate(dx, dz)
                if (!collision.scorpionBlocked(candidate)) add(candidate)
            }
        } }
        assertTrue(blocked.isEmpty(), "Blocked native Scorpia tiles: $blocked; open nearby: $nearby")
    }

    @Test fun `native queen location has space for two distinct guardians outside her body`() {
        val queen = Npc("npc.scorpia", ScorpiaCave.QueenSpawn)
        val sites = ScorpiaRules.guardianSites(queen.coords, queen.size) { !collision.scorpionBlocked(it) }
        assertEquals(2, sites.size)
        assertEquals(2, sites.distinct().size)
        assertTrue(sites.all { ScorpiaRules.footprintDistance(queen.coords, queen.size, it) in 1..2 })
    }

    @Test fun `each cave arrival can walk to the queen and back to its own exit`() {
        val finder = RouteFinding(collision)
        for ((index, passage) in ScorpiaCave.Passages.withIndex()) {
            val queen = ScorpiaCave.QueenSpawn
            val toQueen = finder.findRoute(level = 0, srcX = passage.inside.x, srcZ = passage.inside.z,
                destX = queen.x, destZ = queen.z, destWidth = 5, destLength = 5,
                locShape = -2, moveNear = false)
            assertTrue(toQueen.success, "Cave entrance ${passage.entrance} cannot reach queen")
            val exit = passage.exit
            val toExit = finder.findRoute(level = 0, srcX = queen.x - 1, srcZ = queen.z,
                destX = exit.x, destZ = exit.z, locShape = 0, locAngle = if (index == 0) 1 else 3,
                moveNear = false)
            assertTrue(toExit.success, "Cave exit $exit cannot be reached from queen")
        }
    }

    @Test fun `guardian follows over native floor and stops at heal range while movement lock holds`() {
        val queen = Npc("npc.scorpia", ScorpiaCave.QueenSpawn)
        val guardian = Npc("npc.scorpia_guardian", CoordGrid(3223, 10340))
        val movement = NpcMovementProcessor(collision, StepFactory(collision), EventBus())
        val initial = ScorpiaRules.footprintDistance(queen.coords, queen.size, guardian.coords)
        repeat(10) { tick ->
            guardian.currentMapClock = 100 + tick
            guardian.processedMapClock = 100 + tick
            guardian.previousCoords = guardian.coords
            val inRange = ScorpiaRules.footprintDistance(queen.coords, queen.size, guardian.coords) <= 3
            val old = guardian.coords
            guideGuardian(guardian, queen, inRange)
            movement.process(guardian)
            assertTrue(old.chebyshevDistance(guardian.coords) <= 1)
            assertFalse(collision.scorpionBlocked(guardian.coords))
            if (inRange) assertEquals(old, guardian.coords)
        }
        val distance = ScorpiaRules.footprintDistance(queen.coords, queen.size, guardian.coords)
        assertTrue(distance <= 3 && distance < initial, "Guardian failed to follow: ${guardian.coords}, range=$distance")
        guardian.movementLocked = true
        val frozen = guardian.coords
        guideGuardian(guardian, queen, inRange = false)
        movement.process(guardian)
        assertEquals(frozen, guardian.coords)
    }
}
