package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class TheatreBarrierTest {
    @Test fun `every standard arena barrier preserves its lane and the correct floor in both directions`() {
        for (room in 0..4) {
            for (barrier in TheatreData.roomsList[room].barriers) for (entering in listOf(true, false)) {
                val world = TheatreEncounterIntegrationTest.Fixture()
                val run = world.party(room)
                val angle = when (room) {
                    0 -> 1
                    1 -> if (barrier.x == 3287) 3 else 1
                    2 -> 0
                    3 -> 2
                    else -> if (barrier.z == 4379) 2 else 0
                }
                val outside = when (angle) {
                    0 -> barrier.translate(0, 1)
                    1 -> barrier.translate(1, 0)
                    2 -> barrier.translate(0, -1)
                    else -> barrier.translate(-1, 0)
                }
                val inside = when (angle) {
                    0 -> barrier.translate(0, -1)
                    1 -> barrier.translate(-1, 0)
                    2 -> barrier.translate(0, 1)
                    else -> barrier.translate(1, 0)
                }.copy(level = run.roomrow.entry.level)
                val player = run.members.single()
                player.coords = world.manager.coord(run, if (entering) outside else inside)
                pass(world, run, player, barrier, angle)
                player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
                walk(world, run, player)
                assertEquals(if (entering) inside else outside, world.manager.source(run, player.coords),
                    "${run.roomrow.name}: $barrier entering=$entering")
                assertFalse(player.forcedRoute)
                assertTrue(player.routeDestination.isEmpty())
            }
        }
    }

    @Test fun `Maiden preserves all four lanes and force walks one tile per tick in both directions`() {
        for (z in 4445..4448) for (entering in listOf(true, false)) {
            val world = TheatreEncounterIntegrationTest.Fixture()
            val run = world.party(0)
            world.encounters.start(run)
            val player = run.members.single()
            val start = CoordGrid(if (entering) 3186 else 3184, z, 0)
            player.coords = world.manager.coord(run, start)
            pass(world, run, player, CoordGrid(3185, z, 0), 1)
            assertEquals(start, world.manager.source(run, player.coords))
            assertFalse(player.forcedRoute)
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
            val steps = walk(world, run, player)
            assertEquals(listOf(CoordGrid(3185, z, 0), CoordGrid(if (entering) 3184 else 3186, z, 0)), steps)
            assertFalse(player.forcedRoute)
            assertTrue(player.routeDestination.isEmpty())
            assertSame(run, world.manager.run(player))
        }
    }

    @Test fun `declining or closing barrier confirmation cannot start or move a party`() {
        for (decline in listOf(true, false)) {
            val world = TheatreEncounterIntegrationTest.Fixture()
            val run = world.party(0, lobby = true)
            val player = run.members.single()
            val source = CoordGrid(3186, 4447, 0)
            player.coords = world.manager.coord(run, source)
            pass(world, run, player, CoordGrid(3185, 4447, 0), 1)
            if (decline) player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 2))
            else player.cancelActiveCoroutine()
            assertEquals(source, world.manager.source(run, player.coords))
            assertEquals(TheatreManager.LOBBY, run.stage)
            assertNull(run.boss)
            assertFalse(player.forcedRoute)
        }
    }

    @Test fun `Bloat room arrival waits outside and its accepted barrier begins the fight`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0)
        world.encounters.start(run)
        world.manager.roomCleared(run)
        val player = run.members.single()
        world.manager.advanceRoom(player)
        assertEquals(CoordGrid(3322, 4448, 0), world.manager.source(run, player.coords))
        assertEquals(TheatreManager.WAITING, run.stage)
        assertNull(run.boss)
        player.coords = world.manager.coord(run, CoordGrid(3305, 4448, 0))
        pass(world, run, player, CoordGrid(3304, 4448, 0), 1)
        player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
        walk(world, run, player)
        assertEquals(CoordGrid(3303, 4448, 0), world.manager.source(run, player.coords))
        assertEquals(TheatreManager.ACTIVE, run.stage)
        assertNotNull(run.boss)
    }

    @Test fun `outside and dead players cannot be selected or receive delayed raid hits`() {
        for (room in listOf(0, 1)) for (dead in listOf(false, true)) {
            val world = TheatreEncounterIntegrationTest.Fixture()
            val run = world.party(room)
            world.encounters.start(run)
            val player = run.members.single()
            world.manager.damage(run, player, 50)
            assertTrue(world.hits.isEmpty())
            if (dead) TheatreManager.set(player, TheatreManager.DEAD, 1)
            else player.coords = world.manager.coord(run, run.roomrow.arrival)
            world.advanceQueues()
            assertTrue(world.hits.isEmpty())
            assertTrue(world.manager.combatants(run).isEmpty())
            world.manager.damage(run, player, 50)
            world.advanceQueues()
            assertTrue(world.hits.isEmpty())
        }
    }

    @Test fun `leaving before an incoming raid hit prevents damage without counting a death`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0)
        world.encounters.start(run)
        val player = run.members.single()
        world.manager.damage(run, player, 50)
        world.manager.leave(player)
        world.advanceQueues()
        assertTrue(world.hits.isEmpty())
        assertEquals(0, player.vars[TheatreManager.DEATHS])
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
    }

    private fun pass(world: TheatreEncounterIntegrationTest.Fixture, run: TheatreRun,
        player: Player, source: CoordGrid, angle: Int) {
        val type = checkNotNull(ServerCacheManager.getObject("loc.tob_arena_barrier".asRSCM(RSCMType.LOC)))
        val barrier = BoundLocInfo(LocInfo(2, world.manager.coord(run, source), LocEntity(type.id, 10, angle)), type)
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { world.events }, getCollision = { world.flags }, getAlignment = { TextAlignment() },
        )
        player.processedMapClock = player.currentMapClock
        player.launch { world.events.publish(ProtectedAccess(player, this, context), LocEvents.Op1(barrier, barrier, type)) }
        assertTrue(player.activeCoroutine?.isSuspended == true)
    }

    private fun walk(world: TheatreEncounterIntegrationTest.Fixture, run: TheatreRun, player: Player): List<CoordGrid> {
        val movement = PlayerMovementProcessor(world.flags, RouteFactory(world.flags), StepFactory(world.flags), world.events)
        val steps = mutableListOf<CoordGrid>()
        repeat(8) {
            if (player.activeCoroutine?.isSuspended != true) return steps
            val before = player.coords
            movement.process(player)
            if (player.coords != before) steps += world.manager.source(run, player.coords)
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
        }
        error("Barrier crossing did not finish")
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
