package org.rsmod.content.other.windmill

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.interact.LocInteractions
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
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WindmillMillingTest {
    @Test fun `cancelling the deposit animation preserves consumed grain across saving and milling once`() {
        val first = Fixture()
        first.player.inv[0] = InvObj("obj.grain")
        first.op(first.hopper)
        assertEquals(0, first.player.inv.count("obj.grain"))
        assertTrue(WindmillState.hasGrain(first.player))
        first.cancel()

        val restored = Fixture()
        restored.player.vars.backing.putAll(first.player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        })
        restored.login()
        restored.op(restored.lever)
        assertFalse(WindmillState.hasGrain(restored.player))
        assertEquals(1, restored.player.vars["varbit.mill_flour"])
        assertEquals(1, restored.player.vars["varbit.mill_showflour"])
        restored.tick(3)
        restored.op(restored.lever)
        assertEquals(1, restored.player.vars["varbit.mill_flour"])
        assertTrue(restored.output().contains("empty hopper"))
    }

    @Test fun `filling a hopper that already owns grain does not consume another item`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.grain")
        f.op(f.hopper)
        f.tick()
        f.player.inv[0] = InvObj("obj.grain")
        f.op(f.hopper)
        assertEquals(1, f.player.inv.count("obj.grain"))
        assertTrue(WindmillState.hasGrain(f.player))
        assertTrue(f.output().contains("already grain"))
    }

    @Test fun `an empty inventory cannot create grain in the hopper`() {
        val f = Fixture()
        f.op(f.hopper)
        assertFalse(WindmillState.hasGrain(f.player))
        assertEquals(0, f.player.vars["varbit.mill_flour"])
        assertTrue(f.output().contains("haven't got anything"))
        f.cancel()
    }

    @Test fun `login migrates old hopper grain before operating the lever`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf("windmill_hopper_has_grain" to true))
        f.login()
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.op(f.lever)
        assertFalse(WindmillState.hasGrain(f.player))
        assertEquals(1, f.player.vars["varbit.mill_flour"])
        f.player.attr.putAllFromPersistence(mapOf("windmill_hopper_has_grain" to true))
        f.tick(3)
        f.op(f.lever)
        assertEquals(1, f.player.vars["varbit.mill_flour"])
    }

    private class Fixture {
        private val events = EventBus()
        private val clock = MapClock(100)
        private val collision = CollisionFlagMap()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3165, 3306, 2)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
        }
        val hopper = loc("loc.hopper1", CoordGrid(3165, 3307, 2))
        val lever = loc("loc.hopperlevers1", CoordGrid(3164, 3306, 2))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision },
            getAlignment = { TextAlignment() },
        )
        private val repo: LocRepository
        private var coroutine = GameCoroutine()
        private var result: Result<Unit>? = null

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            for (loc in listOf(hopper, lever)) {
                storage.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(loc.coords.x and 7, loc.coords.z and 7, loc.layer)] = loc.entity
            }
            for (x in 3152..3176 step 8) for (z in 3296..3320 step 8) {
                collision.allocateIfAbsent(x, z, 2)
            }
            val normal = LocRegistryNormal(updates, collision, storage)
            val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
                NpcRegistry(NpcList(), collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            repo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(WindmillMillingScript(repo)) { scripts.startup() }
        }

        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }
        fun output() = client.messages.joinToString("\n")
        fun op(loc: BoundLocInfo) {
            result = null
            coroutine = GameCoroutine("windmill-milling-test")
            player.activeCoroutine = coroutine
            val event = checkNotNull(LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1))
            val action: suspend () -> Unit = {
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), event))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            result?.getOrThrow()
        }
        fun tick(count: Int = 1) {
            repeat(count) {
                clock.tick()
                player.currentMapClock = clock.cycle
                player.processedMapClock = clock.cycle
                coroutine.advance()
                result?.getOrThrow()
                repo.javaClass.declaredMethods.single { it.name.startsWith("processDurations") }.invoke(repo)
            }
        }
        fun cancel() {
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
        }
        private fun loc(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(LocLayerConstants.of(10), coords, LocEntity(type.id, 10, 0)), type)
        }
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
