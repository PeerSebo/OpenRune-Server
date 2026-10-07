package org.rsmod.content.generic.npcs

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
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.content.generic.npcs.person.Barbarian
import org.rsmod.content.generic.npcs.person.Druid
import org.rsmod.content.generic.npcs.person.GenericPerson
import org.rsmod.content.generic.npcs.person.TownCrier
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
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TownCrierDialogueTest {
    @Test fun `all seventeen village barbarians answer without a player greeting or initiating combat`() {
        for (symbol in Barbarian.VillageBarbarians) {
            val (output, pages) = play(Barbarian(), symbol)
            assertTrue(output.contains("Grrr!"), symbol)
            assertEquals(1, pages, symbol)
            assertFalse(output.contains("Hello"), symbol)
        }
    }

    @Test fun `additional common people and original content group dispatch the same conversation`() {
        for (symbol in GenericPerson.AdditionalCommonPeople + listOf("npc.man", "npc.woman")) {
            val (output, pages) = play(GenericPerson(), symbol)
            assertTrue(output.contains("I'm fine, how are you?"), symbol)
            assertTrue(output.contains("Very well thank you."), symbol)
            assertEquals(3, pages, symbol)
        }
    }

    @Test fun `common dialogue quest enemy and trading topics no longer add an NPC greeting`() {
        for (branch in listOf(9, 10, 11)) {
            val (_, pages) = play(GenericPerson(), "npc.man", branch)
            assertEquals(3, pages, "branch=$branch")
        }
    }

    @Test fun `ordinary druid completes both sides of its greeting`() {
        val (output, pages) = play(Druid(), "npc.druid")
        assertTrue(output.contains("may nature smile upon you"))
        assertEquals(2, pages)
    }

    private fun play(script: PluginScript, symbol: String, branch: Int = 0): Pair<String, Int> {
        val events = EventBus()
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        val client = RecordingClient()
        val player = Player().apply {
            this.client = client
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val npc = Npc(symbol, CoordGrid(3200, 3201))
        val interactions = NpcInteractions(events)
        assertTrue(interactions.hasOp(npc, player.vars, InteractionOp.Op1), symbol)
        val event = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op1)) { symbol }
        val coroutine = GameCoroutine("common-npc-dialogue")
        player.activeCoroutine = coroutine
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = if (maxExclusive == 22) branch else 1
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.5
        }
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() }, getRandom = { random },
            getNpcInteractions = { interactions },
        )
        val access = ProtectedAccess(player, coroutine, context)
        var completion: Result<Unit>? = null
        val block: suspend () -> Unit = { assertTrue(events.publish(access, event), symbol) }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        var pages = 0
        while (completion == null && pages++ < 15) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class), symbol)
            val button = if (player.ui.containsModal("interface.chat_left")) {
                "component.chat_left:continue"
            } else {
                "component.chat_right:continue"
            }
            coroutine.resumeWith(ResumePauseButtonInput(button, -1))
        }
        checkNotNull(completion) { "$symbol did not finish" }.getOrThrow()
        assertNull(player.interaction, "$symbol should not initiate a different interaction")
        return client.messages.joinToString("\n") to pages
    }

    @Test fun `every native town crier completes the conversation including the transformed Kingstown spawn`() {
        val events = EventBus()
        with(TownCrier()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        for ((symbol, choice) in TownCrier.TownCriers.flatMap { symbol -> listOf(symbol to 1, symbol to 2) }) {
            val client = RecordingClient()
            val player = Player().apply {
                this.client = client
                coords = CoordGrid(3200, 3200)
                currentMapClock = 100; processedMapClock = 100
                inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
                worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            }
            val npc = Npc(symbol, CoordGrid(3200, 3201))
            if (npc.type.multiVarBit > 0) {
                val visibleStage = checkNotNull(npc.type.transforms).indexOfFirst { it >= 0 }
                VarPlayerIntMapSetter.set(player, checkNotNull(ServerCacheManager.getVarbit(npc.type.multiVarBit)), visibleStage)
            }
            val interactions = NpcInteractions(events)
            assertTrue(interactions.hasOp(npc, player.vars, InteractionOp.Op1), symbol)
            val event = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op1)) { symbol }
            val coroutine = GameCoroutine("town-crier-dialogue")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getAlignment = { TextAlignment() },
                getNpcInteractions = { interactions },
            )
            val access = ProtectedAccess(player, coroutine, context)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event), symbol) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            var pages = 0
            while (completion == null && pages++ < 15) {
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class), symbol)
                val button = when {
                    player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                    player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                    else -> "component.chat_right:continue"
                }
                coroutine.resumeWith(ResumePauseButtonInput(button, if (button.endsWith(":options")) choice else -1))
            }
            checkNotNull(completion) { "$symbol did not finish" }.getOrThrow()
            val output = client.messages.joinToString("\n")
            assertTrue(output.contains("Hello citizen!"), symbol)
            assertEquals(choice == 1, output.contains("It's my job"), symbol)
            assertTrue(output.contains("Until next time."), symbol)
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
