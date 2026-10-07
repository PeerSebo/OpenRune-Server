package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvStock
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.GameRandom

@ResourceLock("ServerCacheManager")
class MerchantCatalogueTest {
    @Test
    fun `every native merchant binding retains its exact authored key options and order`() {
        val expected = fixture("merchant-bindings")
        val actual = ShopkeeperCatalog.nativeShops
        assertEquals(238, expected.size)
        assertEquals(expected.size, actual.size)
        assertEquals(307, actual.sumOf { it.merchants.size })
        for ((index, fields) in expected.withIndex()) {
            val binding = actual[index]
            assertEquals(fields[0].toInt(), index)
            assertEquals(fields[1], binding.inventory)
            assertEquals(fields[1], binding.key)
            assertEquals(fields[2].split(',').map { it.asRSCM() }, binding.merchants.map { it.asRSCM() })
            assertEquals(fields[3].takeIf { it.isNotEmpty() }, binding.title)
            assertEquals(fields[4].toBooleanStrict(), binding.infiniteStock)
            assertEquals(fields[5].toBooleanStrict(), binding.allowSelling)
            assertNull(binding.stock)
        }
    }

    @Test
    fun `native catalogue preserves gardeners and title override before appending existing profile instances`() {
        val native = ShopkeeperCatalog.nativeShops
        val all = ShopkeeperCatalog.shops
        assertEquals(271, all.size)
        for (index in native.indices) assertSame(native[index], all[index])
        for ((index, profile) in ShopkeeperStockProfiles.shops.withIndex()) {
            assertSame(profile, all[native.size + index])
        }
        val gardener = native.last()
        assertEquals("inv.farmer_supplies", gardener.key)
        assertEquals(50, gardener.merchants.size)
        assertTrue(gardener.infiniteStock)
        assertFalse(gardener.allowSelling)
        val jennifer = native[native.lastIndex - 1]
        assertEquals("Jennifer's General Supplies", jennifer.title)
        assertEquals("inv.pest_general_store", jennifer.inventory)
        assertEquals(listOf("npc.shayzien_generalstore".asRSCM()), jennifer.merchants.map { it.asRSCM() })
    }

    @Test
    fun `reward merchant currencies rates counts and ordered prices match the frozen catalogue`() {
        val expected = fixture("reward-merchants")
        val itemRows = fixture("reward-merchant-items").groupBy { it[0] }
        assertEquals(4, RewardShopData.shops.size)
        assertEquals(28, RewardShopData.shops.sumOf { it.items.size })
        for ((index, fields) in expected.withIndex()) {
            val shop = RewardShopData.shops[index]
            assertEquals(fields[0].toInt(), index)
            assertEquals(fields[1], shop.key)
            assertEquals(fields[2], shop.title)
            assertEquals(fields[3], shop.currency)
            assertEquals(fields[4].toBooleanStrict(), shop.allowSelling)
            assertEquals(fields[5].asRSCM(), shop.displayTemplate.asRSCM())
            assertEquals(fields[6].toInt(), shop.sellMultiplier)
            assertEquals(fields[7].toInt(), shop.buyMultiplier)
            assertEquals(fields[8].toInt(), shop.delta)
            assertEquals(fields[9].toInt(), shop.restockCycles)
            val items = itemRows.getValue(shop.key)
            assertEquals(items.size, shop.items.size)
            for ((slot, row) in items.withIndex()) {
                val item = shop.items[slot]
                assertEquals(slot, row[1].toInt())
                assertEquals(row[2].asRSCM(), item.item.asRSCM())
                assertEquals(row[3].toInt(), item.price)
                assertEquals(row[4].toInt(), item.count)
            }
            val stock = shop.createStock()
            assertEquals(shop.title, stock.name)
            assertEquals(shop.sellMultiplier, stock.sellMultiplier)
            assertEquals(shop.buyMultiplier, stock.buyMultiplier)
            assertEquals(shop.delta, stock.delta)
            assertEquals(items.map { InvStock(it[2].asRSCM(), it[4].toInt(), fields[9].toInt()) }, stock.stock)
        }
    }

    @Test
    fun `openable packs and gem outcomes retain their counts draw bounds and collection log targets`() {
        val packs = fixture("reward-packs")
        assertEquals(6, RewardPackData.packs.size)
        for ((index, row) in packs.withIndex()) {
            val pack = RewardPackData.packs[index]
            assertEquals(index, row[0].toInt())
            assertEquals(row[1].asRSCM(), pack.pack.asRSCM())
            assertEquals(row[2], pack.kind.name.lowercase())
            assertEquals(row[3].takeIf { it.isNotEmpty() }?.asRSCM(), pack.item?.asRSCM())
            assertEquals(row[4].toInt(), pack.count)
        }
        val gems = fixture("reward-gems")
        assertEquals(6, RewardPackData.gems.size)
        for ((index, row) in gems.withIndex()) {
            val gem = RewardPackData.gems[index]
            assertEquals(index, row[0].toInt())
            assertEquals(row[1].asRSCM(), gem.item.asRSCM())
            assertEquals(row[2].toInt(), gem.weight)
            assertEquals(row[3].toInt(), gem.drawBound)
            assertEquals(row[4].toBooleanStrict(), gem.rare)
            assertEquals(row[5].takeIf { it.isNotEmpty() }?.asRSCM(), gem.collectionItem?.asRSCM())
        }
    }

    @Test
    fun `rare draw remains first and a successful onyx roll consumes no normal draw`() {
        val random = TraceRandom(listOf(1, 0, 0, 1, 199))

        val results = List(3) { RewardPacksScript.rollGem(random) }

        assertEquals(listOf("obj.cert_uncut_sapphire", "obj.cert_uncut_onyx", "obj.cert_uncut_dragonstone"), results)
        assertEquals(listOf(100_000_000, 200, 100_000_000, 100_000_000, 200), random.bounds)
        assertEquals(5, random.used)
    }

    @Test
    fun `gem boundaries match the original ordered two hundred outcome distribution`() {
        for ((roll, gem) in listOf(
            0 to "sapphire", 99 to "sapphire", 100 to "emerald", 168 to "emerald",
            169 to "ruby", 192 to "ruby", 193 to "diamond", 198 to "diamond", 199 to "dragonstone",
        )) {
            val random = TraceRandom(listOf(99_999_999, roll))
            assertEquals("obj.cert_uncut_$gem", RewardPacksScript.rollGem(random))
            assertEquals(listOf(100_000_000, 200), random.bounds)
        }
    }

    @Test
    fun `packed catalogues keep typed references and their expected explicit row identities`() {
        checkTable("shopkeeper_bindings", 238, mapOf(
            "order" to VarType.INT, "inventory" to VarType.INV, "merchants" to VarType.NPC,
            "title" to VarType.STRING, "infinite_stock" to VarType.BOOLEAN,
            "allow_selling" to VarType.BOOLEAN, "key" to VarType.STRING,
        ))
        for (row in fixture("merchant-bindings")) {
            val symbol = "dbrow.shopkeeper_binding_" + row[2].split(',').first().removePrefix("npc.")
            assertEquals(row[0].toInt(), DbHelper.row(symbol).column("dbcol.shopkeeper_bindings:order", IntCodec))
        }
        checkTable("reward_merchant_shops", 4, mapOf(
            "order" to VarType.INT, "key" to VarType.STRING, "title" to VarType.STRING,
            "currency" to VarType.STRING, "allow_selling" to VarType.BOOLEAN, "display_template" to VarType.INV,
            "sell_multiplier" to VarType.INT, "buy_multiplier" to VarType.INT, "delta" to VarType.INT,
            "restock_cycles" to VarType.INT, "items" to VarType.OBJ, "prices" to VarType.INT, "counts" to VarType.INT,
        ))
        checkTable("reward_packs", 6, mapOf("order" to VarType.INT, "pack" to VarType.OBJ, "kind" to VarType.STRING, "count" to VarType.INT))
        checkTable("reward_pack_gems", 6, mapOf("order" to VarType.INT, "item" to VarType.OBJ, "weight" to VarType.INT, "draw_bound" to VarType.INT, "rare" to VarType.BOOLEAN))
        for (pack in RewardPackData.packs.filter { it.kind == RewardPackKind.Fixed }) {
            val row = DbHelper.row("dbrow.reward_pack_" + pack.pack.removePrefix("obj."))
            assertEquals(listOf(VarType.OBJ), row.getColumn("dbcol.reward_packs:item").types.toList())
        }
        val onyx = DbHelper.row("dbrow.reward_pack_gem_onyx")
        assertEquals(listOf(VarType.OBJ), onyx.getColumn("dbcol.reward_pack_gems:collection_item").types.toList())
    }

    private fun checkTable(table: String, count: Int, columns: Map<String, VarType>) {
        val id = "dbtable.$table".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(id))
        val rows = DbHelper.table("dbtable.$table")
        assertEquals(count, rows.size)
        for (row in rows) {
            assertEquals(id, row.tableId)
            for ((name, type) in columns) assertEquals(listOf(type), row.getColumn("dbcol.$table:$name").types.toList())
        }
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/shops/$name.tsv"))
            .bufferedReader().use { it.readLines().map { line -> line.split('\t') } }

    private class TraceRandom(private val results: List<Int>) : GameRandom {
        val bounds = mutableListOf<Int>()
        var used = 0
            private set
        override fun of(maxExclusive: Int): Int {
            bounds += maxExclusive
            return results[used++].also { require(it in 0 until maxExclusive) }
        }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected inclusive draw")
        override fun randomDouble(): Double = error("Unexpected floating point draw")
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
