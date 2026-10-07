package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.GameRandom
import org.rsmod.content.minigames.tempoross.TemporossRewards.Entry
import org.rsmod.content.minigames.tempoross.TemporossRewards.Reward
import org.rsmod.content.minigames.tempoross.configs.TemporossRewardData

@ResourceLock("ServerCacheManager")
class TemporossRewardDataTest {
    private val bands = fixture("fish-bands").map { it[0].toInt() to it.drop(1) }
    private val slots = fixture("fish-slots").map {
        TemporossRewardData.FishSlot(it[0].toInt(), it[1].toInt()..it[2].toInt(), it[3].toInt())
    }
    private val casket = fixture("casket-rewards").map {
        Entry(it[0], it[1].toInt()..it[2].toInt(), it[3].toInt(), it[4].toBooleanStrict())
    }

    @Test
    fun `packed reward rows preserve their table ownership column types and order`() {
        verifyTable("tempoross_fish_bands", 7, listOf("level" to VarType.INT, "fish" to VarType.OBJ))
        verifyTable(
            "tempoross_fish_slots", 5,
            listOf("roll_order" to VarType.INT, "minimum" to VarType.INT, "maximum" to VarType.INT, "weight" to VarType.INT),
        )
        verifyTable(
            "tempoross_casket_rewards", 46,
            listOf(
                "roll_order" to VarType.INT, "reward" to VarType.OBJ, "minimum" to VarType.INT,
                "maximum" to VarType.INT, "weight" to VarType.INT, "noted" to VarType.BOOLEAN,
            ),
        )
        for (level in bands.map { it.first }) {
            val row = DbHelper.row("dbrow.tempoross_fish_band_$level")
            assertEquals("dbtable.tempoross_fish_bands".asRSCM(), row.tableId)
            assertEquals(level, row.column("dbcol.tempoross_fish_bands:level", IntCodec))
            assertEquals(5, row.getColumn("dbcol.tempoross_fish_bands:fish").size)
        }
        for ((table, size, prefix) in listOf(
            Triple("tempoross_fish_slots", 5, "tempoross_fish_slot"),
            Triple("tempoross_casket_rewards", 46, "tempoross_casket"),
        )) {
            for (order in 0 until size) {
                val row = DbHelper.row("dbrow.${prefix}_$order")
                assertEquals("dbtable.$table".asRSCM(), row.tableId)
                assertEquals(order, row.column("dbcol.$table:roll_order", IntCodec))
            }
        }
    }

    @Test
    fun `decoded rows equal the complete pre-refactor catalogue`() {
        assertEquals(bands, TemporossRewardData.fishBands.map { it.level to it.fish })
        assertEquals(slots, TemporossRewardData.fishSlots)
        assertEquals(casket, TemporossRewards.casketTable)
        assertEquals(80, slots.sumOf { it.weight })
        assertEquals(230, casket.sumOf { it.weight })
    }

    @Test
    fun `every level boundary retains all fish choices including out of range fallback`() {
        for (level in (-1..126).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            val expected = bands.lastOrNull { level >= it.first } ?: bands.first()
            assertEquals(expected.second, TemporossRewards.fishBand(level), "level $level")
        }
    }

    @Test
    fun `every fish outcome retains its weight quantity and random draw order`() {
        val outcomes = slots.flatMap { slot -> List(slot.weight) { slot } }
        val levels = bands.flatMap { listOf(it.first - 1, it.first, it.first + 1) } + listOf(Int.MIN_VALUE, Int.MAX_VALUE)
        for (level in levels.distinct()) {
            val expectedBand = (bands.lastOrNull { level >= it.first } ?: bands.first()).second
            for ((roll, slot) in outcomes.withIndex()) {
                for (quantity in slot.quantity) {
                    val random = CheckedRolls(quantity, 7999, 0, roll)
                    assertEquals(
                        Reward(expectedBand[slot.order], quantity, noted = true),
                        TemporossRewards.rollPool(level, random) { error("Fish must not check unique ownership") },
                        "level $level, fish roll $roll, quantity $quantity",
                    )
                    random.verify(listOf(0 until 8000, 0 until 160, 0 until 80, slot.quantity))
                }
            }
        }
    }

    @Test
    fun `every casket outcome retains its weight quantity note flag and random draw order`() {
        val outcomes = casket.flatMap { entry -> List(entry.weight) { entry } }
        for ((roll, entry) in outcomes.withIndex()) {
            for (quantity in listOf(entry.quantity.first, entry.quantity.last).distinct()) {
                val random = CheckedRolls(quantity, roll)
                assertEquals(Reward(entry.obj, quantity, entry.noted), TemporossRewards.rollCasket(random), "roll $roll")
                random.verify(listOf(0 until 230, entry.quantity))
            }
        }
    }

    private fun verifyTable(name: String, count: Int, columns: List<Pair<String, VarType>>) {
        val tableId = "dbtable.$name".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(tableId))
        val rows = DbHelper.table("dbtable.$name")
        assertEquals(count, rows.size)
        assertEquals(count, rows.distinctBy { it.id }.size)
        for (row in rows) {
            assertEquals(tableId, row.tableId)
            for ((column, type) in columns) {
                assertEquals(listOf(type), row.getColumn("dbcol.$name:$column").types.toList())
            }
        }
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/tempoross/$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    private class CheckedRolls(private val quantity: Int, vararg values: Int) : GameRandom {
        private val pending = ArrayDeque(values.toList())
        private val draws = mutableListOf<IntRange>()

        override fun of(maxExclusive: Int): Int {
            draws += 0 until maxExclusive
            return pending.removeFirst().also { assertTrue(it in 0 until maxExclusive) }
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            draws += minInclusive..maxInclusive
            assertTrue(quantity in minInclusive..maxInclusive)
            return quantity
        }

        override fun randomDouble(): Double = error("Unexpected floating-point random draw")

        fun verify(expected: List<IntRange>) {
            assertEquals(expected, draws)
            assertTrue(pending.isEmpty())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
