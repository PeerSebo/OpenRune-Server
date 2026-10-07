package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
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
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MissSchismTest {
    @Test fun `news branch directs the player to the bank guard and finishes`() {
        val f = Fixture()
        f.start()
        f.finish(listOf(1))
        assertTrue(f.output().contains("bank guard"))
        assertFalse(f.output().contains("Restoration Fund"))
    }

    @Test fun `identity removes the answered topic and allows either remaining response`() {
        for (choice in listOf(1, 2)) {
            val f = Fixture()
            f.start()
            f.finish(listOf(2, choice))
            assertTrue(f.output().contains("Restoration Fund"))
            assertEquals(2, f.menus.size)
            assertTrue(f.menus[0].contains("Who are you?"))
            assertFalse(f.menus[1].contains("Who are you?"))
            assertEquals(choice == 1, f.output().contains("He'll tell you all about it"))
        }
    }

    @Test fun `refusal finishes without forcing the news or identity conversation`() {
        val f = Fixture()
        f.start()
        f.finish(listOf(3))
        assertTrue(f.output().contains("Oooh."))
        assertFalse(f.output().contains("bank guard"))
        assertFalse(f.output().contains("Restoration Fund"))
    }

    private class Fixture {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3094, 3256)
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val menus = mutableListOf<String>()
        private val events = EventBus()
        private val coroutine = GameCoroutine("miss-schism-dialogue")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )
        init { with(MissSchism()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() } }
        fun output() = client.messages.joinToString("\n")
        fun start() {
            player.activeCoroutine = coroutine
            val npc = Npc("npc.wom_gossip", CoordGrid(3094, 3257))
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, InteractionOp.Op1))
            val access = ProtectedAccess(player, coroutine, context)
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }
        fun finish(choices: List<Int>) {
            val pending = ArrayDeque(choices)
            repeat(35) {
                result?.let { it.getOrThrow(); assertTrue(pending.isEmpty()); return }
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
                val button = when {
                    player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                    player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                    player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                    else -> error("Unexpected dialogue interface")
                }
                val choice = if (button.endsWith(":options")) {
                    menus += client.messages.last { it.toString().contains("Select an option") }.toString()
                    pending.removeFirst()
                } else -1
                coroutine.resumeWith(ResumePauseButtonInput(button, choice))
            }
            error("Dialogue did not finish")
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
