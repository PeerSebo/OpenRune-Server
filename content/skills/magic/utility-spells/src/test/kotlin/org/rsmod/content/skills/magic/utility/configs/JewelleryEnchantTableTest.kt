package org.rsmod.content.skills.magic.utility.configs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class JewelleryEnchantTableTest {
    private val tiers = fixture("tiers")
    private val products = fixture("products")

    @Test
    fun `every tier and ordered product matches the frozen original catalogue`() {
        assertEquals(7, EnchantTier.entries.size)
        assertEquals(40, EnchantTier.entries.sumOf { it.rows.size })
        for ((order, fields) in tiers.withIndex()) {
            val tier = EnchantTier.entries[order]
            assertEquals(order, fields[0].toInt())
            assertEquals(fields[1], tier.name)
            assertEquals(fields[2], tier.spellObj)
            assertEquals(fields[3], tier.amuletSeq)
            assertEquals(fields[4], tier.amuletSpotanim)
            assertEquals(fields[5], tier.amuletSound)
            assertEquals(fields[6], tier.ringSound)
            val expected = products.filter { it[0] == fields[1] }.mapIndexed { index, product ->
                assertEquals(index, product[1].toInt())
                EnchantJewelleryRow(product[2], product[3], EnchantJewelleryShape.valueOf(product[4]))
            }
            assertEquals(expected, tier.rows, tier.name)
        }
    }

    @Test
    fun `lookup only accepts the input symbol from its original tier`() {
        for (fields in products) {
            val tier = EnchantTier.valueOf(fields[0])
            val row = checkNotNull(tier.findRow(fields[2]))
            assertEquals(fields[3], row.product)
            assertEquals(fields[4], row.shape.name)
            assertSame(row, tier.findRow(fields[2]))
            for (other in EnchantTier.entries.filter { it != tier }) {
                assertNull(other.findRow(fields[2]), "${other.name}: ${fields[2]}")
            }
            assertNull(tier.findRow(fields[3]))
        }
        for (tier in EnchantTier.entries) {
            assertNull(tier.findRow("obj.coins"))
            assertNull(tier.findRow("obj.unimplemented_jewellery"))
        }
    }

    @Test
    fun `packed rows retain table identity typed references and explicit order`() {
        val tierTable = "dbtable.jewellery_enchant_tiers".asRSCM()
        val productTable = "dbtable.jewellery_enchant_products".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(tierTable))
        assertNotNull(ServerCacheManager.getDbtable(productTable))
        assertEquals(7, DbHelper.table("dbtable.jewellery_enchant_tiers").size)
        assertEquals(40, DbHelper.table("dbtable.jewellery_enchant_products").size)
        val tierColumns = listOf(
            "tier_order" to VarType.INT, "tier" to VarType.STRING, "spell" to VarType.OBJ,
            "amulet_seq" to VarType.SEQ, "amulet_spotanim" to VarType.SPOTANIM,
            "amulet_sound" to VarType.SYNTH, "ring_sound" to VarType.SYNTH,
        )
        for (fields in tiers) {
            val order = fields[0].toInt()
            val row = DbHelper.row("dbrow.jewellery_enchant_tier_${order + 1}")
            assertEquals(tierTable, row.tableId)
            assertEquals(order, row.column("dbcol.jewellery_enchant_tiers:tier_order", IntCodec))
            assertEquals(fields[1], row.column("dbcol.jewellery_enchant_tiers:tier", StringCodec))
            for ((name, type) in tierColumns) {
                val column = row.getColumn("dbcol.jewellery_enchant_tiers:$name")
                assertEquals(listOf(type), column.types.toList())
                assertEquals(1, column.size)
            }
        }
        val productColumns = listOf(
            "tier" to VarType.STRING, "product_order" to VarType.INT,
            "input" to VarType.OBJ, "product" to VarType.OBJ, "shape" to VarType.STRING,
        )
        for (fields in products) {
            val row = DbHelper.row("dbrow.jewellery_enchant_${fields[2].removePrefix("obj.")}")
            assertEquals(productTable, row.tableId)
            assertEquals(fields[0], row.column("dbcol.jewellery_enchant_products:tier", StringCodec))
            assertEquals(fields[1].toInt(), row.column("dbcol.jewellery_enchant_products:product_order", IntCodec))
            for ((name, type) in productColumns) {
                val column = row.getColumn("dbcol.jewellery_enchant_products:$name")
                assertEquals(listOf(type), column.types.toList())
                assertEquals(1, column.size)
            }
        }
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/jewellery-enchant-$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
