package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.util.UncheckedType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import org.rsmod.content.generic.npcs.person.Gilbert
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GilbertTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
        for ((owner, name) in listOf(
            "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
            "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
        )) {
            val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
            val previous = field.get(null)
            restore += { field.set(null, previous) }
        }
        val previous = InvVirtualStorageHolder.instance
        restore += { InvVirtualStorageHolder.instance = previous }
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll fun cleanup() { restore.asReversed().forEach { it() } }

    @Test fun `captured meadow route supplies scarf and crown and switches the repeat conversation`() {
        val f = Fixture()
        f.talk(1, 1)
        assertEquals(setOf(21314, 27035), f.player.inv.filterNotNull().map { it.id }.toSet())
        assertEquals(1, f.player.vars[Gilbert.TakenCrown])
        assertTrue(f.output().contains("Thank you, I'll wear them with Pride!"))
        f.talk(1)
        assertEquals(2, f.player.inv.occupiedSpace())
        assertTrue(f.output().contains("eight extra colour choices"))
    }

    @OptIn(UncheckedType::class)
    @Test fun `reward grant closes its prompt before modifying a protected inventory`() {
        val f = Fixture()
        val source = f.player.inv
        val type = source.type.copy(flags = source.type.flags or InventoryServerType.PROTECT_FLAG)
        f.player.inv = Inventory(type, source.objs).also {
            it.owner = f.player
            f.player.invMap["inv.inv"] = it
        }
        assertTrue(f.player.inv.type.protect)
        f.talk(1, 1)
        assertEquals(setOf(21314, 27035), f.player.inv.filterNotNull().map { it.id }.toSet())
        assertEquals(1, f.player.vars[Gilbert.TakenCrown])
        assertTrue(f.output().contains("Thank you, I'll wear them with Pride!"))
    }

    @Test fun `all claims only missing reward families including banked and recoloured items`() {
        val f = Fixture()
        f.player.inv[0] = item("obj.pride17_scarf")
        f.player.worn[0] = item("obj.pride22_flower_crown_transgender")
        f.bank[0] = item("obj.pride24_cape_bisexual")
        f.talk(3, 1)
        assertEquals(setOf(21314, 29507, 28116), f.player.inv.filterNotNull().map { it.id }.toSet())
        assertEquals(1, f.player.vars[Gilbert.TakenCrown])
        f.talk(3, 1)
        assertEquals(3, f.player.inv.occupiedSpace())
        assertEquals(1, f.player.worn.occupiedSpace())
        assertEquals(1, f.bank.occupiedSpace())
    }

    @Test fun `insufficient room does not give partial rewards or set receipt flag`() {
        val f = Fixture()
        repeat(27) { f.player.inv[it] = item("obj.spade") }
        f.talk(1, 1)
        assertEquals(27, f.player.inv.occupiedSpace())
        assertEquals(0, f.player.vars[Gilbert.TakenCrown])
        assertTrue(f.output().contains("make room for 2 items"))
    }

    @Test fun `each individual reward and previous page work through native dialogue dispatch`() {
        for ((choices, expected) in listOf(
            listOf(3, 2) to 29489,
            listOf(3, 3) to 29507,
            listOf(3, 4) to 28116,
            listOf(3, 5, 1) to 27035,
            listOf(3, 5, 2) to 21314,
            listOf(3, 5, 3, 2) to 29489,
        )) {
            val f = Fixture()
            f.talk(*choices.toIntArray())
            assertEquals(listOf(expected), f.player.inv.filterNotNull().map { it.id })
            f.talk(*choices.toIntArray())
            assertEquals(1, f.player.inv.occupiedSpace())
            assertTrue(f.output().contains("already own"))
        }
    }

    @Test fun `declining crown and both parade conversations have no reward side effects`() {
        for (choices in listOf(listOf(1, 2), listOf(2, 1), listOf(2, 2))) {
            val f = Fixture()
            f.talk(*choices.toIntArray())
            assertTrue(f.player.inv.isEmpty())
            assertEquals(0, f.player.vars[Gilbert.TakenCrown])
            assertEquals(0, f.player.vars["varbit.pride22_progress"])
            assertEquals(0, f.player.vars["varbit.pride24_main"])
        }
    }

    private class Fixture {
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3127, 3399)
            currentMapClock = 100
            processedMapClock = 100
            inv = invMap.getOrPut("inv.inv").also { it.owner = this }
            worn = invMap.getOrPut("inv.worn").also { it.owner = this }
        }
        val bank = player.invMap.getOrPut("inv.bank").also { it.owner = player }
        private val events = EventBus()
        private val interactions = NpcInteractions(events)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getNpcInteractions = { interactions },
        )

        init { with(Gilbert()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() } }

        fun output() = client.messages.joinToString("\n")
        fun talk(vararg choices: Int) {
            val pending = ArrayDeque(choices.toList())
            val coroutine = GameCoroutine("gilbert-dialogue")
            player.activeCoroutine = coroutine
            val npc = Npc("npc.pride22_gilbert", player.coords.translateZ(1))
            val event = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op1))
            val access = ProtectedAccess(player, coroutine, context)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            repeat(45) {
                completion?.let { it.getOrThrow(); assertTrue(pending.isEmpty()); return }
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
                val button = when {
                    player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                    player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                    player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                    player.ui.containsModal("interface.messagebox") -> "component.messagebox:continue"
                    else -> error("Unexpected dialogue interface")
                }
                coroutine.resumeWith(ResumePauseButtonInput(button,
                    if (button.endsWith(":options")) pending.removeFirst() else -1))
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
        private fun item(symbol: String) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())), 1)
    }
}
