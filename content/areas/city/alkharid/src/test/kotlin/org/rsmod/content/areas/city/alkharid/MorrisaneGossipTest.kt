package org.rsmod.content.areas.city.alkharid

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
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.shops.Shops
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MorrisaneGossipTest {
    @Test fun `cancelling the story leaves it available from either clerk`() {
        val f = Fixture()
        f.start("npc.generalshopkeeper3")
        f.untilMenu()
        assertTrue(f.menu().contains(Topic))
        f.advance(3)
        f.advance()
        f.cancel()
        assertFalse(MorrisaneGossipState.heardGossip(f.player))

        f.start("npc.generalassistant3")
        f.untilMenu()
        assertTrue(f.menu().contains(Topic))
        f.advance(2)
        f.advance()
        assertFalse(MorrisaneGossipState.heardGossip(f.player))
    }

    @Test fun `finishing the story commits before the optional shop follow-up`() {
        val f = Fixture()
        f.start("npc.generalshopkeeper3")
        f.untilMenu()
        f.advance(3)
        repeat(5) { f.advance() }
        assertTrue(f.output().replace("<br>", " ").contains("Business as usual!"), f.output())
        assertFalse(MorrisaneGossipState.heardGossip(f.player))
        f.advance()
        assertTrue(MorrisaneGossipState.heardGossip(f.player))
        f.cancel()

        f.login()
        f.start("npc.generalassistant3")
        f.untilMenu()
        assertFalse(f.menu().contains(Topic))
        assertTrue(f.menu().contains("What are you selling?"))
        f.advance(2)
        f.advance()
        assertTrue(MorrisaneGossipState.heardGossip(f.player))
    }

    @Test fun `legacy saved gossip is imported on login before either clerk offers it again`() {
        for (clerk in listOf("npc.generalshopkeeper3", "npc.generalassistant3")) {
            val f = Fixture()
            f.player.attr.putAllFromPersistence(mapOf("alkharid_heard_morrisane_gossip" to true))
            f.login()
            assertTrue(f.player.attr.toPersistentMap().isEmpty())
            f.start(clerk)
            f.untilMenu()
            assertFalse(f.menu().contains(Topic))
            f.cancel()
        }
    }

    private class Fixture {
        private val events = EventBus()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3315, 3180)
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
            with(AlKharidShops(Shops(events))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }
        fun output() = client.messages.joinToString("\n")
        fun menu() = client.messages.last { it.toString().contains("Select an option") }.toString()
        fun start(symbol: String) {
            client.messages.clear()
            result = null
            coroutine = GameCoroutine("morrisane-gossip-test")
            player.activeCoroutine = coroutine
            val npc = Npc(symbol, player.coords.translateZ(1))
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, InteractionOp.Op1))
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
            repeat(10) {
                if (player.ui.containsModal("interface.chatmenu")) return
                advance()
            }
            error("General store dialogue did not offer its menu")
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
        private const val Topic = "What do you think of Ali Morrisane?"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
