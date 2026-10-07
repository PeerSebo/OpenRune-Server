package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.rewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ApothecaryPotionsTest {
    @OptIn(InternalApi::class)
    @Test fun `asking for potions opens the menu after the player question without repeating the greeting`() {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = client
            coords = CoordGrid(3194, 3406)
            currentMapClock = 100
            processedMapClock = 100
        }
        val quest = Quest(
            id = 0, key = "quest_romeoandjuliet", rowID = 0, displayName = "Romeo & Juliet",
            mapElement = null, startCoord = null, maxSteps = RomeoAndJulietStage.Complete,
            questPoints = 5, questVarp = "varp.rjquest", rewards = rewards {}, itemDisplay = null,
        )
        with(RomeoAndJulietPotion(quest)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).register()
        }
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getRandom = { DefaultGameRandom(1) },
            getAlignment = { TextAlignment() },
        )
        val coroutine = GameCoroutine("apothecary-potions-dialogue-test")
        player.activeCoroutine = coroutine
        var completion: Result<Unit>? = null
        val access = ProtectedAccess(player, coroutine, context)
        val block: suspend () -> Unit = {
            assertTrue(events.publish(access, NpcEvents.Op1(Npc("npc.apothecary", CoordGrid(3195, 3406)))))
        }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        completion?.getOrThrow()
        assertTrue(player.ui.containsModal("interface.chat_left"))
        coroutine.resumeWith(ResumePauseButtonInput("component.chat_left:continue", -1))
        assertTrue(player.ui.containsModal("interface.chatmenu"))
        coroutine.resumeWith(ResumePauseButtonInput("component.chatmenu:options", 1))
        assertTrue(player.ui.containsModal("interface.chat_right"))
        coroutine.resumeWith(ResumePauseButtonInput("component.chat_right:continue", -1))
        checkNotNull(completion).getOrThrow()
        assertTrue(player.ui.containsModal("interface.apothecary_potions"))
        assertEquals(1, client.messages.filterIsInstance<IfSetText>().count {
            it.text.contains("I am the Apothecary")
        })
    }

    @Test fun `native potion menu receives all six text fields and the distinct ingredient lists`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client }
        player.populateApothecaryPotions()
        val text = client.messages.filterIsInstance<IfSetText>().associate { it.combinedId to it.text }
        assertEquals(6, text.size)
        for (recipe in ApothecaryRecipes.all) {
            assertEquals(recipe.label, text["${recipe.component}_title".asRSCM()])
            assertEquals(recipe.requirements, text["${recipe.component}_contents".asRSCM()])
            assertEquals(4, ServerCacheManager.fromComponent("${recipe.component}_contents").type)
        }
        assertEquals("1 limpwurt root<br>1 red spiders' eggs<br>5 coins", contents(text, "strength"))
        assertEquals("2 limpwurt roots<br>1 chocolate dust", contents(text, "energy"))
        assertEquals("1 limpwurt root<br>1 cadavaberries<br>5 coins", contents(text, "antipoison"))
    }

    @Test fun `each exchange consumes its ingredients and creates the correct four dose potion`() {
        for (recipe in ApothecaryRecipes.all) {
            val inv = inventory(recipe)
            assertTrue(transactions.transaction(true) { brewApothecaryPotion(select(inv), recipe) }.success)
            assertEquals(1, inv.count(recipe.product))
            for (ingredient in recipe.ingredients) assertEquals(0, inv.count(ingredient.obj))
        }
    }

    @Test fun `a missing ingredient rolls back earlier ingredients and coins`() {
        for (recipe in ApothecaryRecipes.all) {
            val inv = inventory(recipe)
            inv[1] = null
            val before = inv.objs.toList()
            assertTrue(transactions.transaction(true) { brewApothecaryPotion(select(inv), recipe) }.failure)
            assertEquals(before, inv.objs.toList())
            assertEquals(0, inv.count(recipe.product))
        }
    }

    @Test fun `an empty inventory reports every missing ingredient including the fee`() {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = client
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        for ((key, message) in mapOf(
            "strength" to "You still need: limpwurt root (1), red spiders' eggs (1), coins (5).",
            "energy" to "You still need: limpwurt roots (2), chocolate dust (1).",
            "antipoison" to "You still need: limpwurt root (1), cadavaberries (1), coins (5).",
        )) {
            client.messages.clear()
            val recipe = ApothecaryRecipes.all.first { it.component.endsWith(":$key") }
            assertTrue(player.reportMissingApothecaryIngredients(recipe))
            assertEquals(1, client.messages.size)
            assertTrue(client.messages.single().toString().contains(message))
            assertTrue(player.inv.objs.all { it == null })
        }
    }

    @Test fun `partial ingredients report only outstanding amounts and a complete recipe reports nothing`() {
        val recipe = ApothecaryRecipes.all.first { it.component.endsWith(":strength") }
        val client = RecordingClient()
        val player = Player().apply { this.client = client; inv = inventory(recipe) }
        player.inv[1] = null
        player.inv[2] = player.inv[2]!!.copy(count = 3)
        val before = player.inv.objs.toList()
        assertTrue(player.reportMissingApothecaryIngredients(recipe))
        assertTrue(client.messages.single().toString().contains(
            "You still need: red spiders' eggs (1), coins (2).",
        ))
        assertEquals(before, player.inv.objs.toList())
        client.messages.clear()
        player.inv = inventory(recipe)
        assertFalse(player.reportMissingApothecaryIngredients(recipe))
        assertTrue(client.messages.isEmpty())
    }

    private fun contents(text: Map<Int, String>, potion: String) =
        text["component.apothecary_potions:${potion}_contents".asRSCM()]

    private fun inventory(recipe: ApothecaryRecipe) = Inventory(
        InventoryServerType(size = 28), arrayOfNulls(28),
    ).apply {
        val inventory = this
        assertTrue(transactions.transaction(true) {
            val target = select(inventory)
            for (ingredient in recipe.ingredients) {
                insert { into = target; obj = ingredient.obj.asRSCM(); strictCount = ingredient.count }
            }
        }.success)
    }

    private val transactions by lazy { InvTransactions.from() }

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
