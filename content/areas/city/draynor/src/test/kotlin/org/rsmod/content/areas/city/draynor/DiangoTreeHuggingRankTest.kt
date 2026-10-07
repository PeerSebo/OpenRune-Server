package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.WornObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DiangoTreeHuggingRankTest {
    @Test fun `both held check rank options respond without consuming or equipping the item`() {
        for (symbol in listOf("obj.wreath", "obj.summer2015_mask")) {
            val f = Fixture()
            val type = checkNotNull(ServerCacheManager.getItem(symbol.asRSCM()))
            assertEquals("Check rank", type.interfaceOptions[2])
            val item = InvObj(symbol)
            f.player.inv[4] = item
            f.invoke(HeldObjEvents.Op3(4, item, type, f.player.inv))
            f.assertRank()
            assertSame(item, f.player.inv[4])
            assertTrue(f.player.worn.objs.all { it == null })
        }
    }

    @Test fun `both worn check rank options respond without removing the equipped item`() {
        for (symbol in listOf("obj.wreath", "obj.summer2015_mask")) {
            val f = Fixture()
            val item = InvObj(symbol)
            val slot = Wearpos.Hat.slot
            f.player.worn[slot] = item
            f.invoke(WornObjEvents.Op2(slot, item))
            f.assertRank()
            assertSame(item, f.player.worn[slot])
            assertTrue(f.player.inv.objs.all { it == null })
        }
    }

    private class Fixture {
        private val client = RecordingClient()
        val player = Player().apply {
            client = this@Fixture.client
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
        }
        private val events = EventBus()

        init {
            with(DiangoTreeHuggingRank()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun invoke(event: SuspendEvent<ProtectedAccess>) {
            val coroutine = GameCoroutine()
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty())
            var result: Result<Unit>? = null
            val action: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            checkNotNull(result).getOrThrow()
        }

        fun assertRank() {
            assertEquals(1, client.messages.size)
            assertTrue(client.messages.single().toString().contains("Your tree-hugging rank is: What's a tree?"))
            assertTrue(player.attr.toPersistentMap().isEmpty())
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
