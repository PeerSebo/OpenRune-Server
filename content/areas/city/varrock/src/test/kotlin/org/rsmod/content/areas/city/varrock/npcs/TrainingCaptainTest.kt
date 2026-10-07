package org.rsmod.content.areas.city.varrock.npcs

import dev.openrune.ServerCacheManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TrainingCaptainTest {
    @Test
    fun `talking to the captain opens his conversation and the goodbye branch completes`() {
        val f = Fixture()
        f.talk()
        assertTrue(f.player.ui.containsModal("interface.chat_left"))
        assertTrue(f.output().contains("What do you want, civilian?"))
        f.finish(3)
        assertTrue(f.output().contains("Lucky you. Go away."))
        f.assertSafeMessages()
    }

    @Test
    fun `recruitment checks base Thieving at level thirty one instead of boosted or drained levels`() {
        for (level in listOf(30, 31)) {
            val f = Fixture()
            f.player.statMap.setBaseLevel("stat.thieving", level.toByte())
            f.player.statMap.setCurrentLevel("stat.thieving", if (level == 30) 99 else 1)
            f.talk()
            f.finish(2, 1)
            assertEquals(level >= 31, f.output().contains("criminal record"))
            if (level == 31) assertTrue(f.output().contains("Thieving level of 31"))
            assertTrue(f.output().contains("failed the intelligence test"))
            f.assertSafeMessages()
        }
    }

    @Test
    fun `recruits conversation can end after a contradiction or continue into recruitment`() {
        val contradictory = Fixture()
        contradictory.talk()
        contradictory.finish(1, 1, 2)
        assertTrue(contradictory.output().contains("Earlier you said they looked fit!"))
        contradictory.assertSafeMessages()

        val recruit = Fixture()
        recruit.talk()
        recruit.finish(1, 2, 1, 2)
        assertTrue(recruit.output().contains("Show some respect"))
        assertTrue(recruit.output().contains("Only guards of Varrock"))
        assertTrue(recruit.output().contains("Go away, coward."))
        recruit.assertSafeMessages()
    }

    private class Fixture {
        private val events = EventBus()
        private val messages = mutableListOf<Any>()
        val player = Player().apply {
            coords = CoordGrid(3215, 3497, 2)
            currentMapClock = 100
            processedMapClock = 100
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }
        private val captain = Npc("npc.fai_varrock_guard_trainer02", player.coords.translateX(1))
        private val coroutine = GameCoroutine("training-captain-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events },
            getAlignment = { TextAlignment() },
        )

        init {
            with(TrainingCaptain()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun talk() {
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val action: suspend () -> Unit = { assertTrue(events.publish(access, NpcEvents.Op1(captain))) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun finish(vararg choices: Int) {
            val remaining = choices.iterator()
            var steps = 0
            while (coroutine.isSuspended && steps++ < 40) {
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
                val input = when {
                    player.ui.containsModal("interface.chatmenu") ->
                        ResumePauseButtonInput("component.chatmenu:options", remaining.nextInt())
                    player.ui.containsModal("interface.chat_left") ->
                        ResumePauseButtonInput("component.chat_left:continue", -1)
                    player.ui.containsModal("interface.chat_right") ->
                        ResumePauseButtonInput("component.chat_right:continue", -1)
                    else -> error("Unexpected Captain dialogue: ${player.ui.modals}")
                }
                coroutine.resumeWith(input)
                result?.getOrThrow()
            }
            assertTrue(coroutine.isIdle)
            assertFalse(remaining.hasNext())
        }

        fun output() = messages.filterIsInstance<MessageGame>().joinToString(" ") {
            it.message.replace("<br>", " ")
        }

        fun assertSafeMessages() {
            val chat = messages.filterIsInstance<MessageGame>()
            assertTrue(chat.isNotEmpty())
            for (message in chat) assertTrue(message.estimateSize() <= 255, message.toString())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
