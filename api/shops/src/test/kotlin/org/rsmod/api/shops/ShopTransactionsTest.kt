package org.rsmod.api.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.currency.InvObjShopCurrency
import org.rsmod.api.shops.operation.StandardCurrencyShopOperations
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShopTransactionsTest {
    @Test fun `clothes shop opens with native stock and its configured prices`() {
        val f = Fixture("inv.clotheshop")
        assertEquals(10, f.shop.inv.count("obj.leather_gloves"))
        assertEquals(100.0, f.shop.sellPercentage)
        assertEquals(55.0, f.shop.buyPercentage)
        assertEquals(1.0, f.shop.changePercentage)
        assertTrue(f.client.messages.any { it.toString().contains("Thessalia's Fine Clothes.") })
    }

    @Test fun `sword shop opens with ten bronze daggers and its own price settings`() {
        val f = Fixture("inv.swordshop")
        assertEquals(10, f.shop.inv.count("obj.bronze_dagger"))
        assertEquals(100.0, f.shop.sellPercentage)
        assertEquals(60.0, f.shop.buyPercentage)
        assertEquals(2.0, f.shop.changePercentage)
    }

    @Test fun `sold out standard item remains visible and replenishes to its initial count`() {
        for ((inventory, item) in listOf("inv.clotheshop" to "obj.leather_gloves", "inv.swordshop" to "obj.bronze_dagger")) {
            val f = Fixture(inventory)
            assertFalse(f.shop.infiniteStock)
            val slot = f.shop.inv.indexOfFirst { it?.id == item.asRSCM() }
            f.operations.shopBuy(f.player, f.player.inv, f.shop, slot, 10)
            assertEquals(10, f.player.inv.count(item))
            assertEquals(0, f.shop.inv[slot]?.count)
            repeat(10) {
                f.clock.cycle += 100
                f.restock.process()
            }
            assertEquals(10, f.shop.inv[slot]?.count)
            assertTrue(f.restock.modifiedShops.isEmpty())
        }
    }

    @Test fun `general store accepts foreign stock and it later leaves the shelf`() {
        val f = Fixture("inv.generalshop2")
        assertEquals(40, f.shop.inv.size)
        assertTrue(f.shop.inv.type.allStock)
        assertEquals(13, f.shop.inv.type.stock.size)
        f.player.inv[1] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.bronze_dagger".asRSCM())), 1)
        f.operations.invSell(f.player, f.player.inv, f.shop, 1, 1)
        assertNull(f.player.inv[1])
        assertEquals(1, f.shop.inv.count("obj.bronze_dagger"))
        f.clock.cycle = 100
        f.restock.process()
        assertEquals(0, f.shop.inv.count("obj.bronze_dagger"))
    }

    @Test fun `insufficient coins leave both inventory and stock unchanged`() {
        val f = Fixture("inv.swordshop")
        f.player.inv[0] = null
        val slot = f.shop.inv.indexOfFirst { it?.id == "obj.bronze_dagger".asRSCM() }
        f.operations.shopBuy(f.player, f.player.inv, f.shop, slot, 5)
        assertEquals(10, f.shop.inv[slot]?.count)
        assertEquals(0, f.player.inv.count("obj.bronze_dagger"))
    }

    @Test fun `shared stock is the same inventory for both shoppers`() {
        val f = Fixture("inv.swordshop")
        val other = Player().apply { client = RecordingClient(); inv = Inventory.create("inv.inv") }
        f.shops.openConfigured(other, f.npc, "inv.swordshop")
        assertSame(f.shop.inv, other.openedShop?.inv)
    }

    @Test fun `reward purchases keep stock available and charge the exact pearl price`() {
        val f = RewardFixture()
        val initialStock = f.shop.inv[f.slot]?.count
        assertTrue(f.shop.infiniteStock)
        repeat(20) {
            f.operations.shopBuy(f.player, f.player.inv, f.shop, f.slot, 1)
            assertEquals(initialStock, f.shop.inv[f.slot]?.count)
        }
        assertEquals(20, f.player.inv.count("obj.hat_of_the_eye"))
        assertEquals(2_000, f.player.inv.count("obj.abyssal_pearl"))
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test fun `insufficient pearls leave reward and currency unchanged`() {
        val f = RewardFixture(pearls = 399)
        val originalInventory = f.player.inv.objs.toList()
        val originalStock = f.shop.inv.objs.toList()
        f.operations.shopBuy(f.player, f.player.inv, f.shop, f.slot, 1)
        assertEquals(originalInventory, f.player.inv.objs.toList())
        assertEquals(originalStock, f.shop.inv.objs.toList())
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test fun `full inventory rolls back pearl payment and preserves reward stock`() {
        val f = RewardFixture()
        val pot = checkNotNull(ServerCacheManager.getItem("obj.pot_empty".asRSCM()))
        for (slot in 1 until f.player.inv.size) {
            f.player.inv[slot] = InvObj(pot, 1)
        }
        val originalInventory = f.player.inv.objs.toList()
        val originalStock = f.shop.inv.objs.toList()
        f.operations.shopBuy(f.player, f.player.inv, f.shop, f.slot, 1)
        assertEquals(originalInventory, f.player.inv.objs.toList())
        assertEquals(originalStock, f.shop.inv.objs.toList())
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test fun `purchase only shops reject selling even stocked tradeable items`() {
        val f = RewardFixture(allowSelling = false)
        f.player.inv[1] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.air_talisman".asRSCM())), 1)
        val originalInventory = f.player.inv.objs.toList()
        val originalStock = f.shop.inv.objs.toList()
        assertFalse(f.shop.allowSelling)
        f.operations.examineInvValue(f.player, f.player.inv, f.shop, 1)
        f.operations.invSell(f.player, f.player.inv, f.shop, 1, 1)
        assertEquals(originalInventory, f.player.inv.objs.toList())
        assertEquals(originalStock, f.shop.inv.objs.toList())
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test fun `gardeners sell all seven supplies at fixed coin prices and preserve their stock`() {
        val f = Fixture("inv.farmer_supplies", infiniteStock = true, allowSelling = false)
        val initialStock = f.shop.inv.objs.toList()
        for (slot in f.shop.inv.indices) {
            f.operations.shopBuy(f.player, f.player.inv, f.shop, slot, 1)
        }
        assertEquals(999_907, f.player.inv.count("obj.coins"))
        val cureSlot = f.shop.inv.indexOfFirst { it?.id == "obj.plant_cure".asRSCM() }
        f.operations.shopBuy(f.player, f.player.inv, f.shop, cureSlot, 50)
        assertEquals(21, f.player.inv.count("obj.plant_cure"))
        assertEquals(999_107, f.player.inv.count("obj.coins"))
        assertEquals(initialStock, f.shop.inv.objs.toList())
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test fun `item exclusions prevent sellback without disabling ordinary sales`() {
        val f = Fixture("inv.generalshop1")
        val crown = checkNotNull(ServerCacheManager.getItem("obj.crystal_crown_ithell".asRSCM()))
        assertTrue(crown.tradeable || crown.stockmarket)
        val shop = f.shop.copy(unsellableItems = setOf(crown.id))
        f.player.inv[1] = InvObj(crown, 1)
        val stockBefore = shop.inv.objs.toList()
        f.operations.examineInvValue(f.player, f.player.inv, shop, 1)
        f.operations.invSell(f.player, f.player.inv, shop, 1, 1)
        assertEquals(crown.id, f.player.inv[1]?.id)
        assertEquals(1_000_000, f.player.inv.count("obj.coins"))
        assertEquals(stockBefore, shop.inv.objs.toList())
        assertTrue(f.client.messages.any { it.toString().contains("You can't sell this item to this shop.") })
        f.player.inv[1] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.bronze_dagger".asRSCM())), 1)
        f.operations.invSell(f.player, f.player.inv, shop, 1, 1)
        assertNull(f.player.inv[1])
        assertTrue(f.player.inv.count("obj.coins") > 1_000_000)
    }

    @Test fun `a skillcape purchase includes its hood for the same payment`() {
        val f = Fixture("inv.roguesden_shop_skillcape")
        val cape = "obj.skillcape_thieving".asRSCM()
        val hood = InvObj(checkNotNull(ServerCacheManager.getItem("obj.skillcape_thieving_hood".asRSCM())), 1)
        val shop = f.shop.copy(purchaseExtras = mapOf(cape to listOf(hood)))
        val slot = shop.inv.indexOfFirst { it?.id == cape }
        f.operations.shopBuy(f.player, f.player.inv, shop, slot, 1)
        assertEquals(901_000, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.inv.count("obj.skillcape_thieving"))
        assertEquals(1, f.player.inv.count("obj.skillcape_thieving_hood"))
        assertEquals(0, shop.inv[slot]?.count)
    }

    @Test fun `bulk cape purchases fit whole cape and hood pairs`() {
        val f = Fixture("inv.roguesden_shop_skillcape")
        val cape = "obj.skillcape_thieving".asRSCM()
        val hood = InvObj(checkNotNull(ServerCacheManager.getItem("obj.skillcape_thieving_hood".asRSCM())), 1)
        val shop = f.shop.copy(changePercentage = 0.0, purchaseExtras = mapOf(cape to listOf(hood)))
        val slot = shop.inv.indexOfFirst { it?.id == cape }
        shop.inv[slot] = shop.inv[slot]!!.copy(count = 3)
        val pot = InvObj(checkNotNull(ServerCacheManager.getItem("obj.pot_empty".asRSCM())), 1)
        for (i in 1..22) f.player.inv[i] = pot
        assertEquals(5, f.player.inv.freeSpace())
        f.operations.shopBuy(f.player, f.player.inv, shop, slot, 3)
        assertEquals(802_000, f.player.inv.count("obj.coins"))
        assertEquals(2, f.player.inv.count("obj.skillcape_thieving"))
        assertEquals(2, f.player.inv.count("obj.skillcape_thieving_hood"))
        assertEquals(1, shop.inv[slot]?.count)
    }

    @Test fun `insufficient money or room for a hood rolls back the whole purchase`() {
        for (enoughCoins in listOf(false, true)) {
            val f = Fixture("inv.roguesden_shop_skillcape")
            val cape = "obj.skillcape_thieving".asRSCM()
            val hood = InvObj(checkNotNull(ServerCacheManager.getItem("obj.skillcape_thieving_hood".asRSCM())), 1)
            val shop = f.shop.copy(purchaseExtras = mapOf(cape to listOf(hood)))
            val slot = shop.inv.indexOfFirst { it?.id == cape }
            if (enoughCoins) {
                val pot = InvObj(checkNotNull(ServerCacheManager.getItem("obj.pot_empty".asRSCM())), 1)
                for (i in 1..26) f.player.inv[i] = pot
            } else {
                f.player.inv[0] = f.player.inv[0]!!.copy(count = 98_999)
            }
            val before = f.player.inv.objs.toList()
            val stock = shop.inv.objs.toList()
            f.operations.shopBuy(f.player, f.player.inv, shop, slot, 1)
            assertEquals(before, f.player.inv.objs.toList())
            assertEquals(stock, shop.inv.objs.toList())
            assertTrue(f.restock.modifiedShops.isEmpty())
        }
    }

    @Test fun `spending the last coins can free the second slot for a cape pair`() {
        val f = Fixture("inv.roguesden_shop_skillcape")
        val cape = "obj.skillcape_thieving".asRSCM()
        val hood = InvObj(checkNotNull(ServerCacheManager.getItem("obj.skillcape_thieving_hood".asRSCM())), 1)
        val shop = f.shop.copy(purchaseExtras = mapOf(cape to listOf(hood)))
        val slot = shop.inv.indexOfFirst { it?.id == cape }
        f.player.inv[0] = f.player.inv[0]!!.copy(count = 99_000)
        val pot = InvObj(checkNotNull(ServerCacheManager.getItem("obj.pot_empty".asRSCM())), 1)
        for (i in 1..26) f.player.inv[i] = pot
        f.operations.shopBuy(f.player, f.player.inv, shop, slot, 1)
        assertEquals(0, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.inv.count("obj.skillcape_thieving"))
        assertEquals(1, f.player.inv.count("obj.skillcape_thieving_hood"))
        assertEquals(0, f.player.inv.freeSpace())
    }

    @Test fun `shop return exceptions override tradeability but retain stock and refusal rules`() {
        val hood = checkNotNull(ServerCacheManager.getItem("obj.graceful_hood".asRSCM()))
        val tradeable = hood.tradeable
        val stockmarket = hood.stockmarket
        try {
            hood.tradeable = false
            hood.stockmarket = true
            for (rule in listOf("ordinary", "return", "excluded", "purchase-only", "wrong-stock")) {
                val f = Fixture(if (rule == "wrong-stock") "inv.swordshop" else "inv.generalshop1")
                val shop = f.shop.copy(
                    sellableItems = if (rule == "ordinary") emptySet() else setOf(hood.id),
                    unsellableItems = if (rule == "excluded") setOf(hood.id) else emptySet(),
                    allowSelling = rule != "purchase-only",
                    buyPercentage = 80.0,
                    changePercentage = 0.0,
                )
                val operations = StandardCurrencyShopOperations(
                    InvObjShopCurrency(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())), "coin"),
                    f.restock,
                    object : MarketPrices { override fun get(type: ItemServerType): Int? = null },
                    costOf = { 35 },
                )
                f.player.inv[1] = InvObj(hood, 1)
                operations.examineInvValue(f.player, f.player.inv, shop, 1)
                operations.invSell(f.player, f.player.inv, shop, 1, 1)
                if (rule == "return") {
                    assertEquals(0, f.player.inv.count("obj.graceful_hood"))
                    assertEquals(1_000_028, f.player.inv.count("obj.coins"))
                } else {
                    assertEquals(1, f.player.inv.count("obj.graceful_hood"), rule)
                    assertEquals(1_000_000, f.player.inv.count("obj.coins"), rule)
                }
            }
        } finally {
            hood.tradeable = tradeable
            hood.stockmarket = stockmarket
        }
    }

    private class RewardFixture(pearls: Int = 10_000, allowSelling: Boolean = true) {
        private val pearl = checkNotNull(ServerCacheManager.getItem("obj.abyssal_pearl".asRSCM()))
        val player = Player().apply {
            client = RecordingClient()
            inv = Inventory.create("inv.inv")
            inv[0] = InvObj(pearl, pearls)
        }
        val restock = ShopRestockProcess(MapClock(0))
        val operations = StandardCurrencyShopOperations(
            InvObjShopCurrency(pearl, "abyssal pearl"),
            restock,
            object : MarketPrices { override fun get(type: ItemServerType): Int? = null },
            costOf = { 400 },
        )
        init {
            Shops(EventBus()).open(
                player = player,
                title = "Temple Supplies",
                shopInv = "inv.gotr_reward_shop_all",
                buyPercentage = 0.0,
                sellPercentage = 100.0,
                changePercentage = 0.0,
                currency = "currency.abyssal_pearls",
                infiniteStock = true,
                allowSelling = allowSelling,
            )
        }
        val shop get() = checkNotNull(player.openedShop)
        val slot = shop.inv.indexOfFirst { it?.id == "obj.hat_of_the_eye".asRSCM() }
    }

    private class Fixture(inventory: String, infiniteStock: Boolean = false, allowSelling: Boolean = true) {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            inv = Inventory.create("inv.inv")
            inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())), 1_000_000)
        }
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc("npc.thessalia_normal".asRSCM())))
        val shops = Shops(EventBus())
        val clock = MapClock(0)
        val restock = ShopRestockProcess(clock)
        val operations = StandardCurrencyShopOperations(
            InvObjShopCurrency(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())), "coin"),
            restock,
            object : MarketPrices { override fun get(type: ItemServerType): Int? = null },
        )
        init { shops.openConfigured(player, npc, inventory, infiniteStock = infiniteStock, allowSelling = allowSelling) }
        val shop get() = checkNotNull(player.openedShop)
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
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            val ctx = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            InvTransactionsScript(PlayerItemStorage(emptySet())).apply { with(this) { ctx.startup() } }
        }
    }
}
