package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.InvStock
import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.currency.InvObjShopCurrency
import org.rsmod.api.shops.operation.StandardCurrencyShopOperations
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShopkeeperStockProfilesTest {
    private val profiles = fixture("profiles")
    private val stock = fixture("stock").groupBy { it[0] }
    private val restore = mutableListOf<() -> Unit>()

    @AfterEach
    fun restoreStock() {
        restore.asReversed().forEach { it() }
        restore.clear()
    }

    @Test
    fun `every decoded profile and ordered stock entry matches the original catalogue`() {
        val actual = ShopkeeperStockProfiles.shops
        assertEquals(33, actual.size)
        assertEquals(386, actual.sumOf { checkNotNull(it.stock).stock.size })
        assertEquals(34, actual.sumOf { it.merchants.size })
        assertEquals(9, actual.sumOf { checkNotNull(it.stock).unsellableItems.size })
        for ((index, fields) in profiles.withIndex()) {
            val binding = actual[index]
            val definition = checkNotNull(binding.stock)
            assertEquals(fields[0].toInt(), index)
            assertEquals(fields[1], binding.key)
            assertEquals(fields[2].asRSCM(), binding.inventory.asRSCM())
            assertEquals(fields[3].split(',').map { it.asRSCM() }, binding.merchants.map { it.asRSCM() })
            assertEquals(fields[4], definition.name)
            assertEquals(fields[5].asRSCM(), definition.displayTemplate.asRSCM())
            assertEquals(fields[6].toInt(), definition.sellMultiplier)
            assertEquals(fields[7].toInt(), definition.buyMultiplier)
            assertEquals(fields[8].toInt(), definition.delta)
            assertEquals(fields[9].toBooleanStrict(), definition.allStock)
            assertEquals(symbolList(fields[10]).map { it.asRSCM() }.toSet(), definition.unsellableItems)
            assertNull(binding.title)
            assertFalse(binding.infiniteStock)
            assertTrue(binding.allowSelling)
            val expectedStock = stock.getValue(binding.key).mapIndexed { slot, entry ->
                assertEquals(slot, entry[1].toInt())
                InvStock(entry[2].asRSCM(), entry[3].toInt(), entry[4].toInt())
            }
            assertEquals(expectedStock, definition.stock, binding.key)
        }
    }

    @Test
    fun `packed profiles retain typed references aligned stock and explicit row identity`() {
        val table = "dbtable.shopkeeper_stock_profiles".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(table))
        assertEquals(33, DbHelper.table("dbtable.shopkeeper_stock_profiles").size)
        val columns = listOf(
            "profile_order" to VarType.INT, "key" to VarType.STRING, "inventory" to VarType.INV,
            "merchants" to VarType.NPC, "name" to VarType.STRING, "display_template" to VarType.INV,
            "sell_multiplier" to VarType.INT, "buy_multiplier" to VarType.INT, "delta" to VarType.INT,
            "all_stock" to VarType.BOOLEAN, "items" to VarType.OBJ, "counts" to VarType.INT,
            "restock_cycles" to VarType.INT,
        )
        for (fields in profiles) {
            val key = fields[1]
            val row = DbHelper.row("dbrow.shopkeeper_stock_${key.removePrefix("profile.")}")
            assertEquals(table, row.tableId)
            assertEquals(fields[0].toInt(), row.column("dbcol.shopkeeper_stock_profiles:profile_order", IntCodec))
            for ((name, type) in columns) {
                assertEquals(listOf(type), row.getColumn("dbcol.shopkeeper_stock_profiles:$name").types.toList())
            }
            for (name in listOf("items", "counts", "restock_cycles")) {
                assertEquals(stock.getValue(key).size, row.getColumn("dbcol.shopkeeper_stock_profiles:$name").size)
            }
            if (fields[10].isNotEmpty()) {
                val unsellable = row.getColumn("dbcol.shopkeeper_stock_profiles:unsellable")
                assertEquals(listOf(VarType.OBJ), unsellable.types.toList())
                assertEquals(9, unsellable.size)
            }
        }
    }

    @Test
    fun `profiles sharing a native display retain distinct stock but share with their own shoppers`() {
        val first = Fixture("profile.nurmof")
        val second = Fixture("profile.lovakengj_sulphur_keeper")
        assertEquals(first.shop.inv.type.id, second.shop.inv.type.id)
        assertNotSame(first.shop.inv, second.shop.inv)
        val other = Player().apply { inv = Inventory.create("inv.inv") }
        checkNotNull(first.binding.stock).open(other, first.shops)
        assertSame(first.shop.inv, other.openedShop?.inv)
        val template = checkNotNull(ServerCacheManager.getInventory(first.binding.inventory.asRSCM()))
        val nativeStock = template.stock.toList()
        val nativeName = template.name
        val inventory = first.shop.inv
        assertEquals(InvScope.Shared, inventory.type.scope)
        assertEquals(InvStackType.Always, inventory.type.stack)
        assertTrue(inventory.type.restock)
        assertFalse(inventory.type.protect)
        assertFalse(inventory.type.allStock)
        assertEquals(template.size, inventory.size)
        first.operations.shopBuy(first.player, first.player.inv, first.shop, 0, 1)
        assertEquals(5, inventory[0]?.count)
        assertEquals(5, other.openedShop?.inv?.get(0)?.count)
        assertEquals(5, second.shop.inv[0]?.count)
        assertEquals(999_900, first.player.inv.count("obj.coins"))
        first.clock.cycle = 49
        first.restock.process()
        assertEquals(5, inventory[0]?.count)
        first.clock.cycle = 50
        first.restock.process()
        assertEquals(6, inventory[0]?.count)
        assertEquals(5, second.shop.inv[0]?.count)
        assertEquals(nativeStock, template.stock)
        assertEquals(nativeName, template.name)
    }

    @Test
    fun `profile purchases and sales retain stock dependent pricing`() {
        val f = Fixture("profile.nurmof")
        assertEquals(100.0, f.shop.sellPercentage)
        assertEquals(60.0, f.shop.buyPercentage)
        assertEquals(2.0, f.shop.changePercentage)
        f.operations.shopBuy(f.player, f.player.inv, f.shop, 0, 1)
        assertEquals(999_900, f.player.inv.count("obj.coins"))
        val slot = f.player.inv.indexOfFirst { it?.id == "obj.bronze_pickaxe".asRSCM() }
        f.operations.invSell(f.player, f.player.inv, f.shop, slot, 1)
        assertEquals(999_962, f.player.inv.count("obj.coins"))
        assertEquals(0, f.player.inv.count("obj.bronze_pickaxe"))
        assertEquals(6, f.shop.inv[0]?.count)
    }

    @Test
    fun `general profile accepts foreign stock and restocks without changing another profile`() {
        val f = Fixture("profile.warrens_shopkeeper")
        val other = Fixture("profile.prif_generalstore")
        val originalOther = other.shop.inv.objs.toList()
        assertTrue(f.shop.inv.type.allStock)
        f.player.inv[1] = InvObj("obj.bronze_dagger")
        f.operations.invSell(f.player, f.player.inv, f.shop, 1, 1)
        assertEquals(1_000_040, f.player.inv.count("obj.coins"))
        assertEquals(1, f.shop.inv.count("obj.bronze_dagger"))
        f.clock.cycle = 99
        f.restock.process()
        assertEquals(1, f.shop.inv.count("obj.bronze_dagger"))
        f.clock.cycle = 100
        f.restock.process()
        assertEquals(0, f.shop.inv.count("obj.bronze_dagger"))
        assertEquals(originalOther, other.shop.inv.objs.toList())
    }

    @Test
    fun `profile restrictions reject all nine unsellable crystal items`() {
        val f = Fixture("profile.prif_lliann")
        val originalStock = f.shop.inv.objs.toList()
        val crystalItems = symbolList(profiles.single { it[1] == f.binding.key }[10])
        for (item in crystalItems) {
            f.player.inv[1] = InvObj(item)
            val originalInventory = f.player.inv.objs.toList()
            f.operations.invSell(f.player, f.player.inv, f.shop, 1, 1)
            assertEquals(originalInventory, f.player.inv.objs.toList(), item)
            assertEquals(originalStock, f.shop.inv.objs.toList(), item)
        }
        assertTrue(f.restock.modifiedShops.isEmpty())
    }

    @Test
    fun `failed profile purchase preserves currency and stock`() {
        for (full in listOf(false, true)) {
            val f = Fixture("profile.nurmof")
            if (full) {
                for (slot in 1 until f.player.inv.size) f.player.inv[slot] = InvObj("obj.pot_empty")
            } else {
                f.player.inv[0] = null
            }
            val originalStock = f.shop.inv.objs.toList()
            val originalInventory = f.player.inv.objs.toList()
            f.operations.shopBuy(f.player, f.player.inv, f.shop, 0, 1)
            assertEquals(originalInventory, f.player.inv.objs.toList())
            assertEquals(originalStock, f.shop.inv.objs.toList())
            assertTrue(f.restock.modifiedShops.isEmpty())
        }
    }

    private inner class Fixture(key: String) {
        val binding = ShopkeeperStockProfiles.shops.single { it.key == key }
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            inv[0] = InvObj("obj.coins", 1_000_000)
        }
        val shops = Shops(EventBus())
        val clock = MapClock(0)
        val restock = ShopRestockProcess(clock)
        val operations = StandardCurrencyShopOperations(
            InvObjShopCurrency(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())), "coin"),
            restock,
            object : MarketPrices { override fun get(type: ItemServerType): Int? = null },
            costOf = { 100 },
        )

        init {
            val definition = checkNotNull(binding.stock)
            val inventory = definition.inventory
            val original = inventory.objs.toList()
            restore += { original.forEachIndexed { slot, obj -> inventory[slot] = obj } }
            definition.open(player, shops, infiniteStock = binding.infiniteStock, allowSelling = binding.allowSelling)
        }

        val shop get() = checkNotNull(player.openedShop)
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/shops/$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    private fun symbolList(field: String): List<String> = field.split(',').filter { it.isNotEmpty() }

    companion object {
        private val restoreGlobals = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restoreGlobals += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restoreGlobals += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll
        fun cleanup() {
            restoreGlobals.asReversed().forEach { it() }
            restoreGlobals.clear()
        }
    }
}
