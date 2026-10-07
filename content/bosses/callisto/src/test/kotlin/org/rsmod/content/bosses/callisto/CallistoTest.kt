package org.rsmod.content.bosses.callisto

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallistoTest {
    private lateinit var collision: CollisionFlagMap
    private val movingNpcs = mutableListOf<Npc>()

    @AfterEach fun removeNpcCollision() {
        for (npc in movingNpcs) npc.removeBlockWalkCollision(collision, npc.coords)
        movingNpcs.clear()
    }

    @BeforeAll fun nativeMap() {
        collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            val coords = CallistoLair.Arenas.flatMap { listOf(it.arrival, it.surface) } +
                CallistoLair.EscapeLandings + CallistoLair.EscapeExits.values.flatMap { it.values }
            val squares = coords.map { it.x / 64 to it.z / 64 }.toSet()
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

    @Test fun `both arena arrivals exits and whole boss footprints are on native open ground`() {
        val candidates = mutableListOf<Pair<String, CoordGrid>>()
        for (arena in CallistoLair.Arenas) {
            candidates += "${arena.variant} arrival" to arena.arrival
            candidates += "${arena.variant} surface" to arena.surface
            val size = checkNotNull(ServerCacheManager.getNpc(arena.variant.npc.asRSCM(RSCMType.NPC))).size
            for (x in 0 until size) for (z in 0 until size) candidates += "${arena.variant} footprint" to arena.bossSpawn.translate(x, z)
        }
        for (tile in CallistoLair.EscapeLandings) candidates += "escape landing" to tile
        for (dest in CallistoLair.EscapeExits.values.flatMap { it.values }) candidates += "escape surface" to dest
        val blocked = candidates.filter { collision.bearBlocked(it.second) }
        val openNearby = blocked.associate { (name, tile) ->
            name to buildList { for (dx in -16..16) for (dz in -16..16) {
                val candidate = tile.translate(dx, dz)
                if (!collision.bearBlocked(candidate)) add(candidate)
            } }.sortedBy { it.chebyshevDistance(tile) }.take(16)
        }
        assertTrue(blocked.isEmpty(), "Native collision blocks: $blocked; nearby open tiles: $openNearby")
        val finder = RouteFinding(collision)
        val returns = CallistoLair.Arenas.map { it.surface } + CallistoLair.EscapeExits.values.flatMap { it.values }
        for (surface in returns) {
            val canLeave = (-1..1).any { dx -> (-1..1).any { dz ->
                if (dx == 0 && dz == 0) false else {
                    val outside = surface.translate(dx * 8, dz * 8)
                    !collision.bearBlocked(outside) && finder.findRoute(level = surface.level,
                        srcX = surface.x, srcZ = surface.z, destX = outside.x, destZ = outside.z,
                        moveNear = false).success
                }
            } }
            assertTrue(canLeave, "Surface return $surface is trapped inside a collision pocket")
        }
    }

    @Test fun `bears pursue using real arena collision on consecutive ticks and obey movement lock`() {
        val movement = NpcMovementProcessor(collision, StepFactory(collision), EventBus())
        for (arena in CallistoLair.Arenas) {
            val npc = Npc(arena.variant.npc, arena.bossSpawn).also { movingNpcs += it }
            val player = Player().apply { coords = arena.arrival }
            val before = CallistoRules.footprintDistance(npc.coords, npc.size, player.coords)
            repeat(6) { tick ->
                npc.currentMapClock = 100 + tick
                npc.processedMapClock = 100 + tick
                npc.previousCoords = npc.coords
                val old = npc.coords
                chaseBear(npc, player, movement)
                assertTrue(old.chebyshevDistance(npc.coords) in 0..2)
                val after = npc.coords
                chaseBear(npc, player, movement)
                assertEquals(after, npc.coords, "Repeated AP callback moved ${arena.variant} twice")
            }
            assertTrue(CallistoRules.footprintDistance(npc.coords, npc.size, player.coords) < before - 4, "${arena.variant} never chased: ${npc.coords}")
            npc.movementLocked = true
            val frozen = npc.coords
            chaseBear(npc, player, movement)
            assertEquals(frozen, npc.coords)
        }
    }

    @Test fun `orb cannot knock a player into the native exit model`() {
        val steps = StepValidator(collision)
        for (arena in CallistoLair.Arenas) {
            val victim = arena.exit.translate(0, 2)
            assertFalse(collision.bearBlocked(victim), "Victim fixture must be outside exit model")
            val path = CallistoRules.knockbackPath(victim.translateZ(3), victim) { a, b ->
                steps.canTravel(a.level, a.x, a.z, b.x - a.x, b.z - a.z, 1, 0, CollisionStrategy.Normal)
            }
            assertEquals(0, path.size)
            assertEquals(50, CallistoRules.knockbackDamage(path.size))
        }
    }

    @Test fun `both bears stop chasing while frozen then resume without bypassing immunity`() {
        val movement = NpcMovementProcessor(collision, StepFactory(collision), EventBus())
        for (arena in CallistoLair.Arenas) {
            val npc = Npc(arena.variant.npc, arena.bossSpawn).apply {
                movingNpcs += this
                currentMapClock = 100
                processedMapClock = 100
            }
            val player = Player().apply { coords = arena.arrival }
            assertTrue(CombatEffects.freeze(npc, 8))
            val frozen = npc.coords
            repeat(8) { tick ->
                npc.currentMapClock = 100 + tick
                npc.processedMapClock = 100 + tick
                chaseBear(npc, player, movement)
                assertEquals(frozen, npc.coords)
                assertFalse(CombatEffects.freeze(npc, 32))
            }
            npc.currentMapClock = 108
            npc.processedMapClock = 108
            assertFalse(CombatEffects.freeze(npc, 8))
            chaseBear(npc, player, movement)
            assertNotEquals(frozen, npc.coords)
            npc.currentMapClock = 113
            assertTrue(CombatEffects.freeze(npc, 8))
        }
    }

    @Test fun `every arena arrival can walk to its native exit`() {
        val finder = RouteFinding(collision)
        for (arena in CallistoLair.Arenas) {
            val route = finder.findRoute(level = 0, srcX = arena.arrival.x, srcZ = arena.arrival.z,
                destX = arena.exit.x, destZ = arena.exit.z, destWidth = 2, destLength = 2,
                locShape = 10, locAngle = 1, moveNear = false)
            assertTrue(route.success, "${arena.variant} arrival has no walk route to the native exit")
        }
    }

    @Test fun `all escape landings connect to a native cave exit without crossing collision`() {
        val finder = RouteFinding(collision)
        val exits = listOf(
            Triple(CoordGrid(3358, 10244), 2 to 3, 1),
            Triple(CoordGrid(3336, 10287), 2 to 2, 2),
            Triple(CoordGrid(3382, 10287), 2 to 2, 3),
            Triple(CoordGrid(3361, 10273), 1 to 1, 3),
        )
        val connected = mutableSetOf<CoordGrid>()
        for (landing in CallistoLair.EscapeLandings) {
            val reachable = exits.filter { (exit, size, angle) ->
                finder.findRoute(level = 0, srcX = landing.x, srcZ = landing.z,
                    destX = exit.x, destZ = exit.z, destWidth = size.first, destLength = size.second,
                    locShape = 10, locAngle = angle, moveNear = false).success
            }
            val floor = if (reachable.isEmpty()) buildString {
                for (z in 10303 downTo 10240) {
                    append(z).append(' ')
                    for (x in 3328..3391) append(if (collision.bearBlocked(CoordGrid(x, z))) '#' else '.')
                    append('\n')
                }
            } else ""
            assertTrue(reachable.isNotEmpty(), "Escape landing $landing has no reachableexit; floor x3328..3391:\n$floor")
            connected.addAll(reachable.map { it.first })
        }
        assertEquals(exits.map { it.first }.toSet(), connected, "Every bound cave exit must be reachable from at least one landing")
    }

    @Test fun `trap and wall collisions stop the orb at their first blocked square`() {
        val from = CoordGrid(100, 100)
        for (free in 0..3) {
            val path = CallistoRules.knockbackPath(CoordGrid(99, 100), from) { _, next -> next.x <= 100 + free }
            assertEquals(free, path.size)
            assertEquals(listOf(50, 35, 20, 5)[free], CallistoRules.knockbackDamage(path.size))
            assertTrue(path.all { it.z == 100 })
        }
        assertTrue(CallistoRules.knockbackPath(CoordGrid(99, 100, 1), from) { _, _ -> true }.isEmpty())
    }

    @Test fun `melee reaches the whole native footprint including diagonals but not other floors`() {
        val origin = CoordGrid(100, 100)
        assertEquals(1, CallistoRules.footprintDistance(origin, 5, CoordGrid(105, 105)))
        assertEquals(3, CallistoRules.footprintDistance(origin, 3, CoordGrid(105, 105)))
        assertEquals(0, CallistoRules.footprintDistance(origin, 5, CoordGrid(103, 102)))
        assertEquals(Int.MAX_VALUE, CallistoRules.footprintDistance(origin, 5, CoordGrid(103, 102, 1)))
    }

    @Test fun `enrage thresholds scale to both variants without treating death as enrage`() {
        assertEquals(0, CallistoRules.enragedStage(667, 1000))
        assertEquals(1, CallistoRules.enragedStage(666, 1000))
        assertEquals(2, CallistoRules.enragedStage(333, 1000))
        assertEquals(1, CallistoRules.enragedStage(300, 450))
        assertEquals(2, CallistoRules.enragedStage(150, 450))
        assertEquals(0, CallistoRules.enragedStage(0, 450))
        assertEquals(0, CallistoRules.enragedStage(1, 0))
    }

    @Test fun `protection halves the bears minimum and maximum melee damage`() {
        assertEquals(listOf(17, 27), listOf(35, 55).map { CallistoRules.meleeDamage(it, true) })
        assertEquals(listOf(7, 17), listOf(15, 35).map { CallistoRules.meleeDamage(it, true) })
        assertEquals(55, CallistoRules.meleeDamage(55, false))
        assertEquals(0, CallistoRules.meleeDamage(0, true))
    }

    @Test fun `delayed mechanics cannot carry into a respawn or replacement encounter`() {
        assertTrue(CallistoRules.canResolve(true, 10, 10, 1))
        assertFalse(CallistoRules.canResolve(true, 11, 10, 450))
        assertFalse(CallistoRules.canResolve(false, 10, 10, 450))
        assertFalse(CallistoRules.canResolve(true, 10, 10, 0))
    }
}
