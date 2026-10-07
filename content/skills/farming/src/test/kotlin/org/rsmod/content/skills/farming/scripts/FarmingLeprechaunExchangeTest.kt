package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunInterface
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.type.cert
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingLeprechaunExchangeTest {
    @Test
    fun `Exchange on each native leprechaun opens populated storage with saved tools`() {
        for (symbol in listOf(
            "npc.farming_tools_leprechaun", "npc.farming_tools_leprechaun_draynor",
            "npc.fossil_leprechaun_underwater", "npc.farming_tools_leprechaun_varlamore",
        )) {
            val f = Fixture()
            val npc = Npc(symbol, CoordGrid(3052, 3304))
            assertEquals("Exchange", npc.type.actions.getOpOrNull(2), symbol)
            f.start(npc, InteractionOp.Op3)
            f.assertStorage()
            assertEquals(1, f.player.vars["varbit.farming_tools_rake"])
            assertEquals(3, f.player.vars["varbit.farming_tools_extrarakes"])
            assertEquals(5, f.player.vars["varbit.farming_tools_wateringcan"])
            assertFalse(f.coroutine.isSuspended)
        }
    }

    @Test
    fun `Exchange and the check supplies dialogue send identical native storage initializers`() {
        val exchange = Fixture()
        exchange.start(op = InteractionOp.Op3)
        val talk = Fixture()
        talk.start(op = InteractionOp.Op1)
        assertTrue(talk.coroutine.isAwaiting(ResumePauseButtonInput::class))
        talk.choose(1)
        talk.assertStorage()
        assertEquals(exchange.initializers().map { it.id to it.values }, talk.initializers().map { it.id to it.values })
    }

    @Test
    fun `declining the conversation does not open a shop or storage`() {
        val f = Fixture()
        f.start(op = InteractionOp.Op1)
        f.choose(2)
        assertTrue(f.initializers().isEmpty())
        assertFalse(f.player.ui.containsModal("interface.farming_tools"))
        assertFalse(f.player.ui.containsModal("interface.shopmain"))
    }

    @Test
    fun `tool deposits preserve native saved count bits and do not exceed the cap`() {
        val f = Fixture()
        f.set("varbit.farming_tools_rake", 1)
        f.set("varbit.farming_tools_extrarakes", 49)
        f.set("varbit.farming_tools_selectedquantity", 2)
        f.give("obj.rake", 2)
        f.button("farming_tools_side:rake")
        assertEquals(1, f.player.inv.count("obj.rake"))
        assertEquals(0, f.player.vars["varbit.farming_tools_rake"])
        assertEquals(50, f.player.vars["varbit.farming_tools_extrarakes"])

        f.give("obj.bones", 27)
        f.button("farming_tools:rake")
        assertEquals(50, f.player.vars["varbit.farming_tools_extrarakes"])
        f.player.inv[(0 until 28).first { f.player.inv[it]?.id == "obj.bones".asRSCM() }] = null
        f.set("varbit.farming_tools_selectedquantity", 0)
        f.button("farming_tools:rake")
        assertEquals(2, f.player.inv.count("obj.rake"))
        assertEquals(1, f.player.vars["varbit.farming_tools_rake"])
        assertEquals(49, f.player.vars["varbit.farming_tools_extrarakes"])
    }

    @Test
    fun `native quantity buttons enable parent clicks and control deposits and withdrawals`() {
        for ((button, mode, amount) in listOf(
            Triple("quantity_1", 0, 1), Triple("quantity_5", 1, 5),
            Triple("quantity_all", 2, 12), Triple("quantity_x", 3, 7),
        )) {
            val f = Fixture()
            f.start(op = InteractionOp.Op3)
            f.set("varbit.farming_tools_selectedquantity", 2)
            f.set("varbit.farming_tools_rake", 0)
            f.set("varbit.farming_tools_extrarakes", 0)
            f.give("obj.rake", 12)

            val component = ServerCacheManager.fromComponent("component.farming_tools:$button")
            assertTrue(f.player.ui.hasEvent(component, -1, IfEvent.Op1), button)
            f.button("farming_tools:$button")
            assertEquals(mode, f.player.vars["varbit.farming_tools_selectedquantity"], button)
            f.button("farming_tools_side:rake")
            if (mode == 3) f.enterCount(amount)
            assertEquals(12 - amount, f.player.inv.count("obj.rake"), button)
            assertEquals(amount and 1, f.player.vars["varbit.farming_tools_rake"], button)
            assertEquals(amount shr 1, f.player.vars["varbit.farming_tools_extrarakes"], button)

            f.button("farming_tools:rake")
            if (mode == 3) f.enterCount(amount)
            assertEquals(12, f.player.inv.count("obj.rake"), button)
            assertEquals(0, f.player.vars["varbit.farming_tools_rake"], button)
            assertEquals(0, f.player.vars["varbit.farming_tools_extrarakes"], button)
        }
    }

    @Test
    fun `right click quantities override All and custom deposits clamp noted stacks to capacity`() {
        val f = Fixture()
        f.start(op = InteractionOp.Op3)
        f.set("varbit.farming_tools_rake", 0)
        f.set("varbit.farming_tools_extrarakes", 0)
        val notedRake = cert("obj.rake").internalName
        f.give(notedRake, 120)
        f.button("farming_tools:quantity_all")

        f.button("farming_tools_side:rake", op = IfButtonOp.Op2)
        assertEquals(119, f.player.inv.count(notedRake))
        f.button("farming_tools_side:rake", op = IfButtonOp.Op3)
        assertEquals(114, f.player.inv.count(notedRake))
        f.button("farming_tools_side:rake", op = IfButtonOp.Op4)
        f.enterCount(0)
        assertEquals(114, f.player.inv.count(notedRake))
        f.button("farming_tools_side:rake", op = IfButtonOp.Op4)
        f.enterCount(200)
        assertEquals(20, f.player.inv.count(notedRake))
        assertEquals(0, f.player.vars["varbit.farming_tools_rake"])
        assertEquals(50, f.player.vars["varbit.farming_tools_extrarakes"])

        f.button("farming_tools:rake", op = IfButtonOp.Op3)
        assertEquals(5, f.player.inv.count("obj.rake"))
        f.button("farming_tools:rake", op = IfButtonOp.Op2)
        assertEquals(6, f.player.inv.count("obj.rake"))
    }

    @Test
    fun `magic and regular secateurs keep separate storage on the shared component`() {
        val f = Fixture()
        f.set("varbit.farming_tools_selectedquantity", 2)
        f.give("obj.secateurs", 1)
        f.give("obj.fairy_enchanted_secateurs", 1)
        f.button("farming_tools_side:secateurs")
        assertEquals(0, f.player.inv.count("obj.secateurs"))
        assertEquals(0, f.player.inv.count("obj.fairy_enchanted_secateurs"))
        assertEquals(1, f.player.vars["varbit.farming_tools_secateurs"])
        assertEquals(1, f.player.vars["varbit.farming_tools_fairysecateurs"])
        f.button("farming_tools:secateurs")
        assertEquals(1, f.player.inv.count("obj.secateurs"))
        assertEquals(1, f.player.inv.count("obj.fairy_enchanted_secateurs"))
        assertEquals(0, f.player.vars["varbit.farming_tools_secateurs"])
        assertEquals(0, f.player.vars["varbit.farming_tools_fairysecateurs"])
    }

    @Test
    fun `watering can storage round trips every native dose including empty`() {
        for (dose in 0..8) {
            val f = Fixture()
            f.set("varbit.farming_tools_wateringcan", 0)
            val item = "obj.watering_can_$dose"
            f.give(item, 1)
            f.button("farming_tools_side:wateringcan")
            assertEquals(0, f.player.inv.count(item))
            assertEquals(dose + 1, f.player.vars["varbit.farming_tools_wateringcan"])
            f.button("farming_tools:wateringcan")
            assertEquals(1, f.player.inv.count(item))
            assertEquals(0, f.player.vars["varbit.farming_tools_wateringcan"])
        }
    }

    @Test
    fun `item on leprechaun notes the full root stack but excludes cabbage`() {
        val f = Fixture()
        f.give("obj.oak_roots", 3)
        f.give("obj.cabbage", 2)
        f.use("obj.oak_roots")
        assertEquals(0, f.player.inv.count("obj.oak_roots"))
        assertEquals(3, f.player.inv.count(cert("obj.oak_roots").internalName))
        f.use("obj.cabbage")
        assertEquals(2, f.player.inv.count("obj.cabbage"))
        assertEquals(0, f.player.inv.count(cert("obj.cabbage").internalName))
    }

    private class Fixture {
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3052, 3305)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        var coroutine = GameCoroutine("leprechaun-exchange-test")
        private val events = EventBus()
        private var completion: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )

        init {
            with(FarmingToolLeprechaunScript()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            VarPlayerIntMapSetter.set(player, "varbit.farming_tools_rake", 1)
            VarPlayerIntMapSetter.set(player, "varbit.farming_tools_extrarakes", 3)
            VarPlayerIntMapSetter.set(player, "varbit.farming_tools_wateringcan", 5)
        }

        fun start(npc: Npc = Npc("npc.farming_tools_leprechaun", CoordGrid(3052, 3304)), op: InteractionOp) {
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, op))
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }

        fun choose(option: Int) {
            coroutine.resumeWith(ResumePauseButtonInput("component.chatmenu:options", option))
            checkNotNull(completion).getOrThrow()
        }

        fun enterCount(count: Int) {
            assertTrue(coroutine.isAwaiting(ResumePCountDialogInput::class))
            coroutine.resumeWith(ResumePCountDialogInput(count))
            checkNotNull(completion).getOrThrow()
        }

        fun set(name: String, value: Int) { VarPlayerIntMapSetter.set(player, name, value) }

        fun give(name: String, count: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
            if (type.isStackable) player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type, count)
            else repeat(count) { player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type) }
        }

        fun button(component: String, comsub: Int = -1, op: IfButtonOp = IfButtonOp.Op1) = run {
            val event = IfModalButton(ServerCacheManager.fromComponent("component.$component"), comsub, null, op)
            assertTrue(events.publish(this, event))
        }

        fun use(item: String) = run {
            val npc = Npc("npc.farming_tools_leprechaun", CoordGrid(3052, 3304))
            val type = checkNotNull(ServerCacheManager.getItem(item.asRSCM()))
            val slot = (0 until 28).first { player.inv[it]?.id == type.id }
            assertTrue(events.publish(this, NpcUDefaultEvents.OpContent(npc, slot, type, npc.type.contentGroup)))
        }

        private fun run(action: suspend ProtectedAccess.() -> Unit) {
            coroutine = GameCoroutine("leprechaun-transfer-test")
            player.activeCoroutine = coroutine
            completion = null
            val access = ProtectedAccess(player, coroutine, context)
            val block: suspend () -> Unit = { access.action() }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
            assertTrue(completion != null || coroutine.isAwaiting(ResumePCountDialogInput::class))
        }

        fun initializers(): List<RunClientScript> = client.messages.filterIsInstance<RunClientScript>()
            .filter { it.id == MAIN_INIT || it.id == SIDE_INIT }

        fun assertStorage() {
            checkNotNull(completion).getOrThrow()
            assertTrue(player.ui.containsModal("interface.farming_tools"))
            assertTrue(player.ui.containsModal("interface.farming_tools_side"))
            assertFalse(player.ui.containsModal("interface.shopmain"))
            assertEquals(FarmingLeprechaunInterface.mainArguments.map { it.asRSCM() }, initializers().single { it.id == MAIN_INIT }.values)
            assertEquals(FarmingLeprechaunInterface.sideArguments.map { it.asRSCM() }, initializers().single { it.id == SIDE_INIT }.values)
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
        private const val MAIN_INIT = 1055
        private const val SIDE_INIT = 1056

        private val transactionsField = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
        private val storageField = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
            .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
        private var oldTransactions: Any? = null
        private var oldStorage: Any? = null
        private var oldVirtualStorage: InvVirtualStorage? = null

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            oldTransactions = transactionsField.get(null)
            oldStorage = storageField.get(null)
            oldVirtualStorage = InvVirtualStorageHolder.instance
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun restoreTransactions() {
            transactionsField.set(null, oldTransactions)
            storageField.set(null, oldStorage)
            InvVirtualStorageHolder.instance = oldVirtualStorage
        }
    }
}
