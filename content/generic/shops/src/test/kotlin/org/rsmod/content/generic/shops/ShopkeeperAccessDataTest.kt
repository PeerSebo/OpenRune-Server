package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.StallShopAccess
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class ShopkeeperAccessDataTest {
    @Test
    fun `all original stall associations refusal durations and bar counters match frozen source data`() {
        val expected = fixture()
        val actual = ShopkeeperAccessData.rules
        assertEquals(46, actual.size)
        assertEquals(28, ShopkeeperStalls.inventories.size)
        assertEquals(18, actual.count { it.stalls.isEmpty() })
        for ((index, row) in expected.withIndex()) {
            val rule = actual[index]
            assertEquals(row[0].toInt(), index)
            assertEquals(row[1], rule.key)
            assertEquals(stalls(row), rule.stalls)
            assertEquals(row[3].toInt(), rule.refusalCycles)
            if (rule.stalls.isNotEmpty()) {
                assertEquals(rule.stalls, ShopkeeperStalls.inventories[rule.key])
                assertEquals(row[3].toInt(), ShopkeeperStalls.refusalCycles(rule.key))
            } else {
                assertNull(ShopkeeperStalls.inventories[rule.key])
                assertEquals(1000, ShopkeeperStalls.refusalCycles(rule.key))
            }
        }
    }

    @Test
    fun `counter reach excludes the adjacent tea seller and preserves other counters`() {
        val expected = fixture().map { it[1] }.toSet() - "inv.teashop"
        for (binding in ShopkeeperCatalog.shops) {
            val portRoberts = binding.inventory.startsWith("inv.port_roberts_") && binding.inventory.endsWith("_trader")
            assertEquals(binding.key in expected || portRoberts, ShopkeeperCounters.requiresApproach(binding), binding.key)
        }
        val futurePortTrader = ShopkeeperBinding("inv.port_roberts_future_trader", emptyList())
        assertTrue(ShopkeeperCounters.requiresApproach(futurePortTrader))
        assertFalse(ShopkeeperCounters.requiresApproach(ShopkeeperBinding("inv.future_trader", emptyList())))
        assertEquals(1000, ShopkeeperStalls.refusalCycles("inv.future_trader"))
    }

    @Test
    fun `every stored theft uses the same authored location key and expires on its own boundary after reload`() {
        val position = CoordGrid(3200, 3200)
        for (row in fixture().filter { it[2].isNotEmpty() }) {
            val key = row[1]
            val duration = row[3].toInt()
            val original = Player()
            VarPlayerIntMapSetter.set(original, "varp.playtime", 100)
            for (stall in stalls(row)) StallShopAccess.record(original, stall, position)
            val saved = original.attr.toPersistentMap()
            val thefts = saved["shop_stall_thefts"] as Map<*, *>
            assertEquals(stalls(row).map { "$it:${position.packed}" }.toSet(), thefts.keys)
            val restored = Player().apply { attr.putAllFromPersistence(saved) }
            val associated = ShopkeeperStalls.inventories.getValue(key)

            VarPlayerIntMapSetter.set(restored, "varp.playtime", 100 + duration - 1)
            assertFalse(StallShopAccess.canTrade(restored, position, associated, ShopkeeperStalls.refusalCycles(key)), key)
            assertTrue(StallShopAccess.canTrade(Player(), position, associated, ShopkeeperStalls.refusalCycles(key)), key)
            VarPlayerIntMapSetter.set(restored, "varp.playtime", 100 + duration)
            assertTrue(StallShopAccess.canTrade(restored, position, associated, ShopkeeperStalls.refusalCycles(key)), key)
        }
    }

    @Test
    fun `access rows join existing bindings and retain typed locations beside persistence keys`() {
        val table = "dbtable.shopkeeper_access".asRSCM()
        val bindings = ShopkeeperCatalog.shops.map { it.key }.toSet()
        assertEquals(46, DbHelper.table("dbtable.shopkeeper_access").size)
        for (fields in fixture()) {
            val key = fields[1]
            assertTrue(key in bindings)
            val row = DbHelper.row("dbrow.shopkeeper_access_" + key.replace('.', '_'))
            assertEquals(table, row.tableId)
            assertEquals(fields[0].toInt(), row.column("dbcol.shopkeeper_access:order", IntCodec))
            for ((name, type) in mapOf("order" to VarType.INT, "key" to VarType.STRING, "refusal_cycles" to VarType.INT)) {
                assertEquals(listOf(type), row.getColumn("dbcol.shopkeeper_access:$name").types.toList())
            }
            if (fields[2].isNotEmpty()) {
                assertEquals(listOf(VarType.LOC), row.getColumn("dbcol.shopkeeper_access:stalls").types.toList())
                assertEquals(listOf(VarType.STRING), row.getColumn("dbcol.shopkeeper_access:stall_keys").types.toList())
                assertEquals(stalls(fields).size, row.getColumn("dbcol.shopkeeper_access:stalls").size)
                assertEquals(stalls(fields).size, row.getColumn("dbcol.shopkeeper_access:stall_keys").size)
            }
        }
    }

    private fun stalls(fields: List<String>) = fields[2].split(',').filter { it.isNotEmpty() }.toSet()

    private fun fixture() = checkNotNull(javaClass.getResourceAsStream("/shops/merchant-access.tsv"))
        .bufferedReader().use { it.readLines().map { row -> row.split('\t') } }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
