package org.rsmod.content.minigames.giantsfoundry

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
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsStage
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class KovacInstructionsTest {
    @Test
    fun `Kovac explains each step in order using three protocol safe dialogue pages`() {
        for (npc in listOf("npc.giants_foundry_kovac", "npc.giants_foundry_kovac_1op")) {
            val f = Fixture()
            f.openInstructions(npc)
            repeat(3) { page ->
                assertEquals(page + 1, f.pages.size)
                assertTrue(f.player.ui.containsModal("interface.messagebox"))
                assertTrue(f.pages.last().estimateSize() <= 255, f.pages.last().toString())
                f.resume("component.messagebox:continue")
            }
            assertTrue(f.coroutine.isIdle)
            assertEquals(EXPECTED_TEXT, f.pages.joinToString(" ") { it.message.replace("<br>", " ") })
            assertEquals(0, f.player.sleepingGiantsStage)
            assertFalse(FoundryGame.hasCommission(f.player))
        }
    }

    @Test
    fun `closing an instructions page stops later pages and permits a fresh conversation`() {
        for (page in 1..3) {
            val f = Fixture()
            f.openInstructions("npc.giants_foundry_kovac")
            repeat(page - 1) { f.resume("component.messagebox:continue") }
            assertEquals(page, f.pages.size)
            f.coroutine.cancel()
            f.player.activeCoroutine = null
            assertFalse(f.coroutine.isSuspended)
            assertEquals(page, f.pages.size)
            assertEquals(0, f.player.sleepingGiantsStage)
            assertFalse(FoundryGame.hasCommission(f.player))
            f.openInstructions("npc.giants_foundry_kovac")
            assertEquals(page + 1, f.pages.size)
            assertTrue(f.pages.last().message.startsWith("Get a commission"))
            f.coroutine.cancel()
            f.player.activeCoroutine = null
        }
    }

    private class Fixture {
        private val events = EventBus()
        private val random = DefaultGameRandom(1)
        private val interfaces = FoundryInterfaces(events)
        private val game = FoundryGame(interfaces, events, XpModifiers(emptySet()))
        private val world = FoundryQuestTestWorld(game, events)
        private val messages = mutableListOf<Any>()
        val player = Player().apply {
            coords = SleepingGiantsWorkshop.ENTRANCE
            currentMapClock = 100
            processedMapClock = 100
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }
        val pages get() = messages.filterIsInstance<MessageGame>().filter { it.type == ChatType.Mesbox.id }
        lateinit var coroutine: GameCoroutine
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events },
            getRandom = { random },
            getAlignment = { TextAlignment() },
        )

        init {
            with(KovacScript(game, FoundryRewardService(interfaces, random), world.service)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun openInstructions(npc: String) {
            result = null
            coroutine = GameCoroutine("kovac-instructions-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val action: suspend () -> Unit = {
                assertTrue(events.publish(access, NpcEvents.Op1(Npc(npc, player.coords))))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            resume("component.chatmenu:options", 2)
            resume("component.chatmenu:options", 4)
        }

        fun resume(component: String, choice: Int = -1) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            coroutine.resumeWith(ResumePauseButtonInput(component, choice))
            result?.getOrThrow()
        }
    }

    companion object {
        private const val EXPECTED_TEXT =
            "Get a commission, then choose a forte, blade and tip at the mould jig. " +
                "Fill the crucible with 28 bars' worth of metal and pour it into the mould. " +
                "Cool the preform with water or ice gloves, then pick it up. " +
                "Follow the tools and temperature ranges shown on the display. " +
                "Heat the sword in the lava pool and cool it at the waterfall. " +
                "Bring me the finished sword for your reward."

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
