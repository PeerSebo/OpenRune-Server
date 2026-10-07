package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class MuseumGateTest {
    @Test fun `museum gate disappears while player walks across and restores in both directions`() {
        for (from in listOf(MuseumGate.Position, MuseumGate.Position.translate(0, 1))) {
            val f = Fixture(from)
            val target = if (from == MuseumGate.Position) from.translate(0, 1) else MuseumGate.Position
            assertEquals(CoordGrid.NULL, StepFactory(f.collision).validated(from, target, size = 1))
            f.operate()
            assertNull(f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
            assertTrue(f.player.forcedRoute)
            assertEquals(listOf(target), f.player.routeDestination.toList())
            assertTrue(f.client.messages.any { "The museum guard opens the gate for you." in it.toString() })
            f.tick()
            assertEquals(target, f.player.coords)
            assertNull(f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
            f.tick()
            assertEquals(f.gate, f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
            assertFalse(f.player.forcedRoute)
            assertTrue(f.player.routeDestination.isEmpty())
            assertEquals(CoordGrid.NULL, StepFactory(f.collision).validated(target, from, size = 1))
        }
    }

    @Test fun `diagonal approach crosses through the gate tile instead of the railings`() {
        val f = Fixture(MuseumGate.Position.translate(-1, 1))
        f.operate()
        assertEquals(listOf(MuseumGate.Position.translate(0, 1), MuseumGate.Position), f.player.routeDestination.toList())
        f.tick()
        assertEquals(MuseumGate.Position.translate(0, 1), f.player.coords)
        f.tick()
        assertEquals(MuseumGate.Position, f.player.coords)
        f.tick()
        assertEquals(f.gate, f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
    }

    @Test fun `cancelling a crossing restores the gate and clears forced walking`() {
        val f = Fixture(MuseumGate.Position)
        f.operate()
        assertNull(f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
        f.cancel()
        assertEquals(f.gate, f.locs.findExact(MuseumGate.Position, LocShape.WallStraight))
        assertFalse(f.player.forcedRoute)
        assertNull(f.player.tempMoveSpeed)
        assertTrue(f.player.routeDestination.isEmpty())
        assertEquals(MuseumGate.Position, f.player.coords)
    }

    @Test fun `museum floor guard explains the work area when spoken to directly`() {
        val f = Fixture(MuseumGate.Position)
        val guard = Npc("npc.vm_museum_guard", MuseumGate.Position.translate(-1, 1))
        val event = checkNotNull(NpcInteractions(f.events).opTrigger(f.player, guard, InteractionOp.Op1))
        f.action { assertTrue(f.events.publish(this, event)) }
        assertTrue(f.coroutine.isAwaiting(ResumePauseButtonInput::class))
        assertTrue(f.client.messages.any { "cleaning finds" in it.toString() })
        assertFalse(guard.isBusy)
        f.cancel()
    }

    private class Fixture(from: CoordGrid) {
        val clock = MapClock(100)
        val events = EventBus()
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val collision = CollisionFlagMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = from
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
        }
        val gate = LocInfo(0, MuseumGate.Position, LocEntity(MuseumGate.Gate.asRSCM(), 0, 1))
        private val interactions = LocInteractions(BoundValidator(collision), events)
        private val movement = PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events }, getAlignment = { TextAlignment() })
        lateinit var coroutine: GameCoroutine
        private var result: Result<Unit>? = null

        init {
            for (x in 3258..3264) for (z in 3442..3450) collision.allocateIfAbsent(x, z, 0)
            storage.mapLocs[ZoneKey.from(gate.coords), LocZoneKey(ZoneGrid.from(gate.coords), gate.layer)] = gate.entity
            assertTrue(locs.add(gate, Int.MAX_VALUE))
            with(MuseumGate(locs)) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun operate() {
            val bound = BoundLocInfo(gate, checkNotNull(ServerCacheManager.getObject(gate.id)))
            val event = checkNotNull(interactions.opTrigger(player, bound, InteractionOp.Op1))
            action { assertTrue(events.publish(this, event)) }
        }

        fun action(block: suspend ProtectedAccess.() -> Unit) {
            coroutine = GameCoroutine("museum-gate-test")
            player.activeCoroutine = coroutine
            val task: suspend () -> Unit = { ProtectedAccess(player, coroutine, context).block() }
            task.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            coroutine.advance()
            movement.process(player)
            result?.getOrThrow()
        }

        fun cancel() { coroutine.cancel(); player.activeCoroutine = null }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
