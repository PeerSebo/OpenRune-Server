package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.draynor.npcs.*
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
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
@ResourceLock("ServerCacheManager")
@ResourceLock("QuestRequirements")
class DraynorNpcOwnershipTest {
    @Test fun `merchants retain direct trade interfaces after separation`() {
        for ((symbol, title) in listOf(
            "npc.rag_wine_merchant" to "Wine Shop.",
            "npc.seed_merchant" to "Draynor Seed Market",
            "npc.aprilfoolshorsesalesman" to "Diango",
        )) {
            val f = Fixture()
            f.start(symbol, InteractionOp.Op3)
            assertTrue(f.player.ui.containsModal("interface.shopmain"), symbol)
            assertTrue(f.output().contains(title), f.output())
        }
    }

    @Test fun `extracted NPCs retain their talk and dye handlers alongside existing owners`() {
        val cases = listOf(
            "npc.wgs_lucien_spy" to "Hello there", "npc.wgs_spy2" to "Hello there",
            "npc.wom_bankguard" to "Yes?", "npc.rag_wine_merchant" to "",
            "npc.seed_merchant" to "", "npc.forestry_forester" to "",
            "npc.diary_queen" to "Hey there", "npc.aprilfoolshorsesalesman" to "Howdy",
            "npc.martin_the_master_farmer" to "What is that cape",
        )
        for ((symbol, greeting) in cases) {
            val f = Fixture()
            f.start(symbol)
            assertTrue(f.output().contains(greeting), symbol)
            f.cancel()
        }
        val dye = Fixture()
        dye.start("npc.aggie", InteractionOp.Op3)
        assertTrue(dye.output().contains("What sort of dye"))
        dye.cancel()
    }

    @Test fun `known bank robbery state still selects the suspect conversation`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.wom_bankjob", 1)
        f.start("npc.wom_bankguard")
        f.advance()
        assertTrue(f.output().contains("Do you have any idea who robbed the bank yet?"))
        f.cancel()
    }

    @Test fun `optional quest access does not invent Martin's post quest dialogue`() {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(mode = QuestRequirementMode.AssumeCompleted))
            val f = Fixture()
            f.start("npc.martin_the_master_farmer")
            assertTrue(f.output().contains("What is that cape"))
            assertFalse(f.output().contains("Ask about the quest."))
            f.cancel()
        } finally {
            QuestRequirements.install(previous)
        }
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
            getNpcInteractions = { NpcInteractions(events) },
        )
        private var coroutine = GameCoroutine()
        private var completion: Result<Unit>? = null
        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            val shops = Shops(events)
            val plugins = listOf(
                DraynorStrangers(), AggieDyes(), DraynorBankGuard(), Fortunato(shops),
                Olivia(shops), FriendlyForester(), TwiggyOKorn(), DiangoShop(shops),
                MartinTheMasterFarmer(), Diango(), MissSchism(),
            )
            for (plugin in plugins) with(plugin) { scripts.startup() }
        }
        fun output() = client.messages.joinToString("\n")
        fun start(symbol: String, op: InteractionOp = InteractionOp.Op1) {
            client.messages.clear()
            completion = null
            coroutine = GameCoroutine("draynor-npc-test")
            player.activeCoroutine = coroutine
            val npc = Npc(symbol, player.coords.translateZ(1))
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, op))
            val action: suspend () -> Unit = {
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), event))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
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
            completion?.getOrThrow()
        }
        fun cancel() {
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, completion?.exceptionOrNull())
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
