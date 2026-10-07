package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.NpcServerType
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
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.interact.NpcUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.generic.npcs.banker.Banker
import org.rsmod.content.interfaces.bank.scripts.BankTutorialScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BankCurrencyNpcDispatchTest {
    @Test fun `bankers and the bank tutor accept both currencies through native item on NPC dispatch`() {
        for (symbol in listOf("npc.banker1_new", "npc.aide_tutor_banker")) {
            for (currency in listOf("obj.coins", "obj.platinum")) {
                val f = Fixture(symbol, currency)
                f.start(approach = false)
                f.assertPrompt()
            }
        }
    }

    @Test fun `bankers exchange across counters using their existing approach range`() {
        for (symbol in listOf("npc.banker1_new", "npc.aide_tutor_banker")) {
            val f = Fixture(symbol, "obj.coins")
            f.player.coords = CoordGrid(3200, 3203)
            f.start(approach = true)
            f.assertPrompt()
        }
    }

    @Test fun `distant bankers do not open a remote exchange prompt`() {
        val f = Fixture("npc.banker1_new", "obj.platinum")
        f.player.coords = CoordGrid(3200, 3210)
        f.start(approach = true)
        assertFalse(f.coroutine.isSuspended)
        assertEquals(5_000, f.player.inv[0]?.count)
    }

    @Test fun `every ordinary native banker accepts both currencies even without a server content tag`() {
        val bankers = ServerCacheManager.getNpcs().values.filter { type ->
            type.name == "Banker" && (0..4).any { type.actions.getOpOrNull(it) == "Bank" }
        }
        assertTrue(bankers.size > 10)
        for (type in bankers) {
            for (currency in listOf("obj.coins", "obj.platinum")) {
                val f = Fixture(type, currency)
                f.start(approach = false)
                f.assertPrompt()
            }
        }
    }

    private class Fixture(type: NpcServerType, currency: String) {
        constructor(symbol: String, currency: String) : this(
            checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), currency,
        )
        val player = Player().apply {
            coords = CoordGrid(3200, 3201); currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        private val npc = Npc(type, CoordGrid(3200, 3200))
        private val obj = checkNotNull(ServerCacheManager.getItem(currency.asRSCM()))
        val coroutine = GameCoroutine("bank-currency-npc-dispatch")
        private val events = EventBus()
        private val interactions = NpcUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private var completion: Result<Unit>? = null
        init {
            val shopClass = Class.forName("org.rsmod.content.generic.npcs.banker.BankSpaceShop")
            val shop = shopClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            val banker = Banker::class.java.getDeclaredConstructor(shopClass, BankTutorialScript::class.java)
                .apply { isAccessible = true }.newInstance(shop, BankTutorialScript(events))
            with(banker) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            player.inv[0] = InvObj(obj, 5_000)
        }
        fun start(approach: Boolean) {
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
            val access = ProtectedAccess(player, coroutine, context)
            val block: suspend () -> Unit = {
                if (approach) interactions.interactAp(access, npc, player.inv, 0, obj)
                else interactions.interactOp(access, npc, player.inv, 0, npc.type, obj)
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }
        fun assertPrompt() {
            assertTrue(coroutine.isAwaiting(ResumePCountDialogInput::class), "npc=${npc.type.id} content=${npc.type.contentGroup}, obj=${obj.id}, coroutine=$coroutine")
            coroutine.resumeWith(ResumePCountDialogInput(0))
            checkNotNull(completion).getOrThrow()
        }
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
