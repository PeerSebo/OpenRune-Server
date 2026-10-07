package org.rsmod.content.skills.mining.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.mining.MiningRocksRow

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiningRocksTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @TestFactory
    fun `generic rock variants retain their matching model`() = listOf(
        "clayrock", "copperrock", "tinrock", "bluriterock", "ironrock", "silverrock",
        "coalrock", "gemrock", "goldrock", "mithrilrock", "lovakiterock", "adamantiterock",
        "runiterock", "softclayrock", "infernalshale",
    ).map { name ->
        dynamicTest(name) {
            val row = row(name)
            assertEquals(2, row.rockObject.size)
            assertEquals(11390, row.emptyRockFor(row.rockObject[0].id)?.id)
            assertEquals(11391, row.emptyRockFor(row.rockObject[1].id)?.id)
        }
    }

    @Test
    fun `recorded tin and copper IDs select the captured depleted types`() {
        assertEquals(11391, row("tinrock").emptyRockFor(11361)?.id)
        assertEquals(11390, row("tinrock").emptyRockFor(11360)?.id)
        assertEquals(11391, row("copperrock").emptyRockFor(11161)?.id)
        assertEquals(11390, row("copperrock").emptyRockFor(10943)?.id)
    }

    @Test
    fun `sandstone and granite use their respective rock outlines`() {
        assertEquals(11390, row("sandstonerock").emptyRockFor(11386)?.id)
        assertEquals(11391, row("graniterock").emptyRockFor(11387)?.id)
    }

    @Test
    fun `all four calcified outcrops retain their matching empty model`() {
        val row = row("calcifiedrock")
        for ((full, empty) in listOf(51485 to 51486, 51487 to 51488, 51489 to 51490, 51491 to 51492)) {
            assertEquals(empty, row.emptyRockFor(full)?.id)
        }
    }

    @Test
    fun `shared dedicated empty rocks still serve every full variant`() {
        for ((name, empty) in listOf("limestonerock" to 11385, "amethystrock" to 11393)) {
            val row = row(name)
            assertEquals(1, row.emptyRockObject.size)
            for (full in row.rockObject) assertEquals(empty, row.emptyRockFor(full.id)?.id)
        }
    }

    @Test
    fun `infinite essence has no depleted replacement`() {
        val row = row("essence")
        assertTrue(row.isInfinite)
        assertTrue(row.emptyRockObject.isEmpty())
        assertNull(row.emptyRockFor(row.rockObject.single().id))
    }

    @Test
    fun `unrelated full rock cannot use another ores replacement`() {
        assertNull(row("tinrock").emptyRockFor(10943))
        assertNull(row("limestonerock").emptyRockFor(11360))
    }

    @Test
    fun `every packed target resolves without losing or shifting variant slots`() {
        for (row in MiningRocksRow.all()) {
            val raw = ServerCacheManager.getDbrow(row.rowId)!!
            val emptyColumn = "dbcol.mining_rocks:empty_rock_object".asRSCM() and 0xFFFF
            val packedIds = raw.definedColumns()[emptyColumn]?.values?.toList().orEmpty()
            assertEquals(packedIds, row.emptyRockObject.map { it.id }, "Mining row ${row.rowId}")
            assertTrue(row.emptyRockObject.size <= 1 || row.emptyRockObject.size == row.rockObject.size)
            for (full in row.rockObject) row.emptyRockFor(full.id)
        }
    }

    private fun row(name: String): MiningRocksRow = MiningRocksRow(DbHelper.row("dbrow.mining_$name"))
}
