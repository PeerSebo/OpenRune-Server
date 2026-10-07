package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.shop.Shop
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SpecialShopScriptTest {
    @Test
    fun `Grace sells all seven rewards for the correct marks and refunds eighty percent`() {
        val f = Fixture()
        val player = f.player("obj.grace", 1000)
        val shop = f.trade(player, "npc.rooftops_grace")
        assertEquals("currency.marks_of_grace", shop.currency)
        assertEquals(7, shop.inv.type.stock.size)
        assertEquals(listOf(100, 100, 100, 100, 100, 100, 1000), shop.inv.type.stock.map { it.count })
        for ((item, price) in listOf(
            "obj.graceful_hood" to 35, "obj.graceful_top" to 55,
            "obj.graceful_legs" to 60, "obj.graceful_gloves" to 30,
            "obj.graceful_boots" to 40, "obj.graceful_cape" to 40,
            "obj.pack_amylase" to 10,
        )) {
            val before = player.inv.count("obj.grace")
            f.buy(player, shop, item)
            assertEquals(1, player.inv.count(item), item)
            assertEquals(before - price, player.inv.count("obj.grace"), item)
            f.sell(player, shop, item)
            assertEquals(0, player.inv.count(item), item)
            assertEquals(before - price / 5, player.inv.count("obj.grace"), item)
        }
    }

    @Test
    fun `Belona uses current glove prices and only her own pack variants`() {
        val f = Fixture()
        val player = f.player("obj.mguild_minerals", 1000)
        val shop = f.trade(player, "npc.mguild_rewardseller")
        assertEquals("currency.unidentified_minerals", shop.currency)
        assertEquals(4, shop.inv.type.stock.size)
        for ((item, price) in listOf(
            "obj.mguild_gloves" to 40, "obj.mguild_gloves_superior" to 100,
            "obj.pack_softclay_guild" to 10, "obj.reward_gem_bag_guild" to 20,
        )) {
            val before = player.inv.count("obj.mguild_minerals")
            f.buy(player, shop, item)
            assertEquals(before - price, player.inv.count("obj.mguild_minerals"), item)
            f.sell(player, shop, item)
            assertEquals(before - price / 5, player.inv.count("obj.mguild_minerals"), item)
        }
        for (item in listOf("obj.pack_softclay", "obj.reward_gem_bag", "obj.star_pack_softclay")) {
            player.inv[1] = InvObj(item)
            val before = player.inv.objs.toList()
            f.sell(player, shop, item)
            assertEquals(before, player.inv.objs.toList(), item)
        }
    }

    @Test
    fun `Alry sells his current rewards for pearls and rejects shop sellback`() {
        val f = Fixture()
        val player = f.player("obj.aerial_fishing_pearl", 1000)
        val shop = f.trade(player, "npc.fishing_npc_angler")
        assertEquals("currency.molch_pearls", shop.currency)
        assertEquals(8, shop.inv.type.stock.size)
        assertFalse(shop.allowSelling)
        for ((item, price) in listOf(
            "obj.fishingrod_pearl" to 100, "obj.fishingrod_pearl_fly" to 120,
            "obj.fish_sack" to 500, "obj.trawler_reward_hat" to 50,
            "obj.trawler_reward_top" to 50, "obj.trawler_reward_legs" to 50,
            "obj.trawler_reward_boots" to 50, "obj.shark_lure_pack" to 1,
        )) {
            val before = player.inv.count("obj.aerial_fishing_pearl")
            f.buy(player, shop, item)
            assertEquals(1, player.inv.count(item), item)
            assertEquals(before - price, player.inv.count("obj.aerial_fishing_pearl"), item)
            val inventory = player.inv.objs.toList()
            f.sell(player, shop, item)
            assertEquals(inventory, player.inv.objs.toList(), item)
        }
    }

    @Test
    fun `Alry gates his barbarian rod on Otto training and charges 150 pearls`() {
        val f = Fixture()
        for (stage in 0..3) {
            val player = f.player("obj.aerial_fishing_pearl", 150)
            VarPlayerIntMapSetter.set(player, "varbit.brut_fishing_r", stage)
            val shop = f.trade(player, "npc.fishing_npc_angler")
            if (stage == 0) {
                assertEquals(0, shop.inv.count("obj.fishingrod_pearl_brut"))
            } else {
                assertEquals(9, shop.inv.type.stock.size)
                f.buy(player, shop, "obj.fishingrod_pearl_brut")
                assertEquals(1, player.inv.count("obj.fishingrod_pearl_brut"))
                assertEquals(0, player.inv.count("obj.aerial_fishing_pearl"))
            }
        }
    }

    @Test
    fun `insufficient currency and full inventories do not lose items or stock`() {
        for (full in listOf(false, true)) {
            val f = Fixture()
            val player = f.player("obj.grace", if (full) 100 else 34)
            player.inv[1] = InvObj("obj.coins", 100000)
            if (full) for (slot in 2 until player.inv.size) player.inv[slot] = InvObj("obj.pot_empty")
            val shop = f.trade(player, "npc.rooftops_grace")
            val inventory = player.inv.objs.toList()
            val stock = shop.inv.objs.toList()
            f.buy(player, shop, "obj.graceful_hood")
            assertEquals(inventory, player.inv.objs.toList())
            assertEquals(stock, shop.inv.objs.toList())
            assertTrue(f.restock.modifiedShops.isEmpty())
        }
    }

    @Test
    fun `reward stock is shared and replenishes after one tick`() {
        val f = Fixture()
        val player = f.player("obj.grace", 100)
        val shop = f.trade(player, "npc.rooftops_grace")
        val other = f.trade(f.player("obj.grace", 100), "npc.rooftops_grace")
        assertSame(shop.inv, other.inv)
        f.buy(player, shop, "obj.graceful_hood")
        assertEquals(99, other.inv.count("obj.graceful_hood"))
        f.clock.cycle = 1
        f.restock.process()
        assertEquals(100, shop.inv.count("obj.graceful_hood"))
    }

    @Test
    fun `reward catalogue data shares stock within one service without sharing mutable inventories across services`() {
        val first = Fixture()
        val second = Fixture()
        val buyer = first.player("obj.grace", 100)
        val firstShop = first.trade(buyer, "npc.rooftops_grace")
        val secondShop = second.trade(second.player("obj.grace", 100), "npc.rooftops_grace")
        assertNotSame(firstShop.inv, secondShop.inv)

        first.buy(buyer, firstShop, "obj.graceful_hood")

        assertEquals(99, firstShop.inv.count("obj.graceful_hood"))
        assertEquals(100, secondShop.inv.count("obj.graceful_hood"))
        assertSame(firstShop.inv, first.trade(first.player("obj.grace", 100), "npc.rooftops_grace").inv)
    }

    private class Fixture {
        private val events = EventBus()
        private val interactions = NpcInteractions(events)
        private val operations = ShopOperationMap()
        val clock = MapClock(0)
        val restock = ShopRestockProcess(clock)

        init {
            operations.registerCurrenciesFromDb(restock, object : MarketPrices {
                override fun get(type: ItemServerType): Int? = null
            })
            with(SpecialShopScript(Shops(events), operations)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun player(currency: String, amount: Int) = Player().apply {
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            inv[0] = InvObj(currency, amount)
        }

        fun trade(player: Player, merchant: String): Shop {
            val npc = Npc(merchant, player.coords.translateZ(1))
            val event = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op3))
            val coroutine = GameCoroutine("special-shop-test")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getNpcInteractions = { interactions },
            )
            val access = ProtectedAccess(player, coroutine, context)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
            return checkNotNull(player.openedShop)
        }

        fun buy(player: Player, shop: Shop, item: String) {
            val slot = shop.inv.indexOfFirst { it?.id == item.asRSCM() }
            checkNotNull(operations[shop.currency]).shopInvOp(player, player.inv, shop, slot, IfButtonOp.Op2)
        }

        fun sell(player: Player, shop: Shop, item: String) {
            val slot = player.inv.indexOfFirst { it?.id == item.asRSCM() }
            checkNotNull(operations[shop.currency]).sideInvOp(player, player.inv, shop, slot, IfButtonOp.Op2)
        }
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
