package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.hunter.configs.ImplingDropTables

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImplingLootTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `all eleven ordinary species tables have exact published totals and real noteable rewards`() {
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12), ImplingDropTables.main.keys)
        for ((index, entries) in ImplingDropTables.main) {
            val expected = when (index) { 8 -> 21; 9, 10 -> 19; 12 -> 18; else -> 100 }
            assertEquals(expected, entries.sumOf { it.weight }, "Impling $index")
            for (entry in entries) {
                if (entry.item.isNothing) continue
                val type = checkNotNull(ServerCacheManager.getItem(entry.item.obj.asRSCM()))
                assertFalse(type.isPlaceholder)
                assertTrue(entry.item.count.first > 0)
                if (entry.noted) assertTrue(type.canCert, "${entry.item.obj} must have a native note")
            }
        }
        for (bonuses in ImplingDropTables.tertiary.values) for (bonus in bonuses) {
            assertNotNull(ServerCacheManager.getItem(bonus.obj.asRSCM()))
        }
    }

    @Test fun `baby has ten nothing slots and both independent clue bonuses`() {
        for (slot in 80..89) {
            val loot = ImplingLoot.roll(1, Rolls(mutableListOf(slot, 1, 1)))
            assertTrue(loot.isEmpty(), "Slot $slot")
        }
        val bonus = ImplingLoot.roll(1, Rolls(mutableListOf(80, 0, 0)))
        assertEquals(listOf("obj.trail_clue_beginner", "obj.trail_clue_easy_simple001"), bonus.map { it.obj })
        assertEquals("obj.spicespot", ImplingLoot.roll(1, Rolls(mutableListOf(90, 1, 1))).single().obj)
    }

    @Test fun `gourmet key replaces standard loot but keeps its independent clue roll`() {
        val key = ImplingLoot.roll(3, Rolls(mutableListOf(0, 1)))
        assertEquals(listOf(ImplingLoot.Reward("obj.hosdun_grubby_key", 1)), key)
        val standard = ImplingLoot.roll(3, Rolls(mutableListOf(1, 0, 1)))
        assertEquals(listOf(ImplingLoot.Reward("obj.tuna", 1)), standard)
    }

    @Test fun `magpie dragonhide occupies two slots and crystal shard range is the updated 30 to 40`() {
        for (slot in 0..1) {
            assertEquals(ImplingLoot.Reward("obj.dragonhide_black", 6, true), ImplingLoot.roll(8, Rolls(mutableListOf(slot, 1))).single())
        }
        val low = ImplingLoot.roll(12, Rolls(mutableListOf(2, 1, 1)))
        val high = ImplingLoot.roll(12, Rolls(mutableListOf(2, 1, 1), high = true))
        assertEquals(ImplingLoot.Reward("obj.prif_crystal_shard", 30), low.single())
        assertEquals(ImplingLoot.Reward("obj.prif_crystal_shard", 40), high.single())
    }

    private class Rolls(val values: MutableList<Int>, val high: Boolean = false) : GameRandom {
        override fun of(maxExclusive: Int): Int = values.removeAt(0).also { require(it in 0 until maxExclusive) }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = if (high) maxInclusive else minInclusive
        override fun randomDouble(): Double = error("Not used by ordinary impling tables")
    }
}
