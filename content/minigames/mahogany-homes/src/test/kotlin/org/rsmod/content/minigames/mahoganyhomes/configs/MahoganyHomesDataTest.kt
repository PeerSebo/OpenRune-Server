package org.rsmod.content.minigames.mahoganyhomes.configs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbHelper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspots
import org.rsmod.content.minigames.mahoganyhomes.rewards.CarpenterPoints
import org.rsmod.content.minigames.mahoganyhomes.rewards.MahoganyHomesShop

@ResourceLock("ServerCacheManager")
class MahoganyHomesDataTest {
    @Test
    fun `tiers preserve the original names ordinal identities and fractional XP`() {
        val expected = fixture("tiers")
        assertEquals(4, MahoganyHomesData.tiers.size)
        assertEquals(expected.map { it[0] }, MahoganyHomesTier.entries.map { it.name })
        for ((index, row) in expected.withIndex()) {
            val tier = MahoganyHomesTier.ofOrdinal(index)
            assertEquals(row[0], tier.name)
            assertEquals(index, tier.ordinal)
            assertEquals(row[1].toInt(), tier.levelReq)
            assertEquals(row[2], tier.plankObj)
            assertEquals(row[3].toDouble(), tier.buildXp)
            assertEquals(row[4].toDouble(), tier.repairXp)
            assertEquals(row[5].toDouble(), tier.completionXp)
            assertEquals(row[6].toInt(), tier.points)
            assertEquals(120.0, tier.barRepairXp)
        }
    }

    @Test
    fun `all original house clients and 88 ordered hotspot references survive packing`() {
        val expected = fixture("houses")
        assertEquals(12, MahoganyHomesData.houses.size)
        assertEquals(expected.map { it[0] }, MahoganyHomesHouse.entries.map { it.name })
        for ((index, row) in expected.withIndex()) {
            val house = MahoganyHomesHouse.ofOrdinal(index)
            val hotspots = (1..row[2].toInt()).map { "loc.${row[3]}_hotspot$it" }
            assertEquals(row[0], house.name)
            assertEquals(row[1], house.clientNpc)
            assertEquals(hotspots, house.hotspots)
            assertEquals(hotspots, MahoganyHomesLayouts.layoutFor(house).map { it.locName })
            assertEquals((1..hotspots.size).toList(), MahoganyHomesLayouts.layoutFor(house).map { it.hotspotIndex })
            for ((slot, hotspot) in hotspots.withIndex()) {
                val definition = checkNotNull(MahoganyHomesHotspots.get(hotspot))
                assertEquals(slot + 1, definition.slot)
                assertEquals(hotspot.asRSCM(), definition.locId)
                assertNotNull(definition.kind)
            }
        }
        assertEquals(88, MahoganyHomesHouse.entries.sumOf { it.hotspots.size })
        MahoganyHomesHotspots.validateAtBoot()
    }

    @Test
    fun `reward override preserves every original price and ordinary item fallback`() {
        val ops = ShopOperationMap()
        MahoganyHomesShop.registerCostOverride(ops)
        val field = ShopOperationMap::class.java.getDeclaredField("costOfOverrides").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val overrides = field.get(ops) as Map<String, (ItemServerType) -> Int>
        val price = overrides.getValue(CarpenterPoints.CURRENCY)
        val expected = fixture("rewards")
        assertEquals(expected.map { it[0] to it[1].toInt() }, MahoganyHomesData.rewards.map { it.item to it.points })
        for (row in expected) {
            val type = checkNotNull(ServerCacheManager.getItem(row[0].asRSCM()))
            assertEquals(row[1].toInt(), price(type.copy(cost = 999999)))
        }
        val other = checkNotNull(ServerCacheManager.getItem("obj.pot_empty".asRSCM()))
        for (cost in listOf(-1, 0, 1, 50)) assertEquals(maxOf(1, cost), price(other.copy(cost = cost)))
    }

    @Test
    fun `schemas carry typed material client hotspot and reward references`() {
        val schemas = mapOf(
            "mahogany_homes_tiers" to mapOf("plank" to VarType.OBJ),
            "mahogany_homes_houses" to mapOf("client" to VarType.NPC, "hotspots" to VarType.LOC),
            "mahogany_homes_rewards" to mapOf("item" to VarType.OBJ),
        )
        for ((table, columns) in schemas) {
            val rows = DbHelper.table("dbtable.$table")
            assertTrue(rows.isNotEmpty())
            for (row in rows) {
                assertEquals("dbtable.$table".asRSCM(), row.tableId)
                for ((column, type) in columns) assertEquals(listOf(type), row.getColumn("dbcol.$table:$column").types.toList())
            }
        }
    }

    @Test
    fun `tier validation rejects changed saved identities missing rows and invalid values`() {
        val rows = MahoganyHomesData.tiers.values.toList()
        val invalid = listOf(
            emptyList(), rows.drop(1), rows.reversed(),
            listOf(rows[0].copy(key = "EXPERT")) + rows.drop(1),
            listOf(rows[0].copy(order = 1)) + rows.drop(1),
            listOf(rows[0].copy(level = 0)) + rows.drop(1),
            listOf(rows[0].copy(buildXpTenths = -1)) + rows.drop(1),
            listOf(rows[0].copy(plank = "npc.bob")) + rows.drop(1),
        )
        for (case in invalid) assertThrows(IllegalArgumentException::class.java) { MahoganyHomesData.validateTiers(case) }
    }

    @Test
    fun `house validation rejects duplicate clients hotspots and invalid display slots`() {
        val rows = MahoganyHomesData.houses.values.toList()
        val invalid = listOf(
            emptyList(), rows.drop(1), rows.reversed(),
            listOf(rows[0].copy(client = rows[1].client)) + rows.drop(1),
            listOf(rows[0].copy(hotspots = emptyList())) + rows.drop(1),
            listOf(rows[0].copy(hotspots = rows[0].hotspots + rows[1].hotspots.first())) + rows.drop(1),
            listOf(rows[0].copy(hotspots = listOf(rows[1].hotspots.first()))) + rows.drop(1),
        )
        for (case in invalid) assertThrows(IllegalArgumentException::class.java) { MahoganyHomesData.validateHouses(case) }
    }

    @Test
    fun `reward validation rejects missing duplicate nonpositive and unordered prices`() {
        val rows = MahoganyHomesData.rewards
        val invalid = listOf(
            emptyList(), rows.drop(1), rows.reversed(),
            listOf(rows[0].copy(item = rows[1].item)) + rows.drop(1),
            listOf(rows[0].copy(points = 0)) + rows.drop(1),
        )
        for (case in invalid) assertThrows(IllegalArgumentException::class.java) { MahoganyHomesData.validateRewards(case) }
    }

    @Test
    fun `native furniture aliases keep repair classification and substring precedence`() {
        val builds = mapOf(
            "chair" to 1, "large_table" to 4, "large_small_table" to 4,
            "longtable" to 4, "small_table" to 2, "sidetable" to 2,
            "drawers" to 2, "cabinet" to 2, "bookcase" to 2, "shelves" to 2,
            "cupboard" to 2, "dresser" to 2, "wardrobe" to 2, "table" to 3,
            "bed" to 3, "red" to 3, "CHAIR_TABLE" to 1,
        )
        for ((kind, cost) in builds) {
            assertEquals(cost, MahoganyHomesFurniture.buildPlankCost(kind), kind)
            assertNull(MahoganyHomesFurniture.repairPlankCost(kind), kind)
            assertFalse(MahoganyHomesFurniture.repairsWithBar(kind), kind)
        }
        for (kind in listOf("clock", "hatstand", "mirror", "grandfatherclock", "fai_varrock_posh_grandfather_clock", "bathmirror", "fai_varrock_mirror")) {
            assertNull(MahoganyHomesFurniture.buildPlankCost(kind), kind)
            assertEquals(1, MahoganyHomesFurniture.repairPlankCost(kind), kind)
            assertFalse(MahoganyHomesFurniture.repairsWithBar(kind), kind)
        }
        for (kind in listOf("range", "sink", "bath")) {
            assertNull(MahoganyHomesFurniture.buildPlankCost(kind), kind)
            assertNull(MahoganyHomesFurniture.repairPlankCost(kind), kind)
            assertTrue(MahoganyHomesFurniture.repairsWithBar(kind), kind)
        }
        assertNull(MahoganyHomesFurniture.buildPlankCost("unknown"))
        assertNull(MahoganyHomesFurniture.repairPlankCost("unknown"))
        assertFalse(MahoganyHomesFurniture.repairsWithBar("unknown"))
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/mahogany-homes-$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            assertEquals("BEGINNER", MahoganyHomesData.tiers.values.first().key)
            assertEquals("BOB", MahoganyHomesData.houses.values.first().key)
        }
    }
}
