package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DiangoTest {
    @Test fun `a full inventory does not claim the hat and retry succeeds exactly once`() {
        val f = Fixture()
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.bucket_empty")
        f.start()
        f.finish(listOf(1))
        assertFalse(DiangoState.claimedBananaHat(f.player))
        assertEquals(0, f.player.inv.count(Hat))

        f.player.inv[0] = null
        f.start()
        f.finish(listOf(1))
        assertTrue(DiangoState.claimedBananaHat(f.player))
        assertEquals(1, f.player.inv.count(Hat))
        f.start()
        f.finish(emptyList())
        assertTrue(f.output().contains("already have everything"))
        assertEquals(1, f.player.inv.count(Hat))
    }

    @Test fun `declining or cancelling before accepting leaves the gift unclaimed`() {
        val f = Fixture()
        f.start()
        f.cancel()
        assertFalse(DiangoState.claimedBananaHat(f.player))
        f.start()
        f.finish(listOf(2))
        assertFalse(DiangoState.claimedBananaHat(f.player))
        assertEquals(0, f.player.inv.count(Hat))
    }

    @Test fun `closing the thank you line preserves the already granted gift`() {
        val f = Fixture()
        f.start()
        f.untilMenu()
        f.advance(1)
        f.advance()
        assertTrue(DiangoState.claimedBananaHat(f.player))
        assertEquals(1, f.player.inv.count(Hat))
        f.cancel()
        f.login()
        f.start()
        f.finish(emptyList())
        assertEquals(1, f.player.inv.count(Hat))
        assertTrue(f.output().contains("already have everything"))
    }

    @Test fun `login imports an old claim before showing holiday dialogue`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf("diango_claimed_banana_hat" to true))
        f.login()
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.start()
        f.finish(emptyList())
        assertEquals(0, f.player.inv.count(Hat))
        assertTrue(f.output().contains("already have everything"))
    }

    private class Fixture {
        private val events = EventBus()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3080, 3250)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )
        private var coroutine = GameCoroutine()
        private var result: Result<Unit>? = null
        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(Diango()) { scripts.startup() }
        }
        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }
        fun output() = client.messages.joinToString("\n")
        fun start() {
            client.messages.clear()
            result = null
            coroutine = GameCoroutine("diango-test")
            player.activeCoroutine = coroutine
            val npc = Npc("npc.aprilfoolshorsesalesman", player.coords.translateZ(1))
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, InteractionOp.Op4))
            val action: suspend () -> Unit = {
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), event))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            result?.getOrThrow()
        }
        fun advance(choice: Int = -1) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            val button = when {
                player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                else -> error("Unexpected dialogue interface")
            }
            coroutine.resumeWith(ResumePauseButtonInput(button, choice))
            result?.getOrThrow()
        }
        fun untilMenu() {
            repeat(20) {
                if (player.ui.containsModal("interface.chatmenu")) return
                advance()
            }
            error("Diango did not offer the gift")
        }
        fun finish(choices: List<Int>) {
            val pending = ArrayDeque(choices)
            repeat(30) {
                result?.let { it.getOrThrow(); assertTrue(pending.isEmpty()); return }
                advance(if (player.ui.containsModal("interface.chatmenu")) pending.removeFirst() else -1)
            }
            error("Diango dialogue did not finish")
        }
        fun cancel() {
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
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
        private const val Hat = "obj.osb8_banana_hat"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
