package org.rsmod.content.generic.locs.banks

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BankCurrencyLocDispatchTest {
    @Test fun `native bank booths route both currency items to the quantity prompt`() {
        for (symbol in listOf("loc.bankbooth", "loc.dwarf_keldagrim_bankbooth", "loc.newbiebankbooth")) {
            for (currency in listOf("obj.coins", "obj.platinum")) Fixture().checkPrompt(symbol, currency)
        }
    }

    @Test fun `every packed bank chest category routes both currencies to the prompt`() {
        val category = "category.bank_chest".asRSCM()
        val chests = ServerCacheManager.getObjects().values.filter { it.category == category }
        assertTrue(chests.size >= 40)
        for (chest in chests) {
            for (currency in listOf("obj.coins", "obj.platinum")) Fixture().checkPrompt(chest.id, currency)
        }
    }

    @Test fun `full backpacks still reach the exchange prompt`() {
        val f = Fixture()
        for (slot in 0..27) f.player.inv[slot] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.bones".asRSCM())))
        f.checkPrompt("loc.blast_bank_chest", "obj.coins")
    }

    private class Fixture {
        val player = Player().apply {
            currentMapClock = 100; processedMapClock = 100; coords = CoordGrid(3200, 3200)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        private val events = EventBus()
        private val coroutine = GameCoroutine("bank-currency-loc-dispatch")
        private val interactions = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(BankBooth()) { context.startup() }
            with(BankChest()) { context.startup() }
        }
        fun checkPrompt(symbol: String, currency: String) = checkPrompt(symbol.asRSCM(), currency)
        fun checkPrompt(id: Int, currency: String) {
            val type = checkNotNull(ServerCacheManager.getObject(id))
            val loc = BoundLocInfo(LocInfo(2, CoordGrid(3201, 3200), LocEntity(id, 10, 0)), type)
            val obj = checkNotNull(ServerCacheManager.getItem(currency.asRSCM()))
            player.inv[0] = InvObj(obj, 5_000)
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty())
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { interactions.interactOp(access, loc, obj, player.inv, 0) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
            assertTrue(coroutine.isAwaiting(ResumePCountDialogInput::class), "loc $id with $currency")
            coroutine.resumeWith(ResumePCountDialogInput(0))
            checkNotNull(completion).getOrThrow()
            assertEquals(5_000, player.inv[0]?.count)
        }
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
