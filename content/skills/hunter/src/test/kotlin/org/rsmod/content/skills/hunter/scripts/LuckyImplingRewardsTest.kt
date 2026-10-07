package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.GameRandom

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LuckyImplingRewardsTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `all five complete single-roll tables resolve real items and note variants`() {
        assertEquals(LuckyImplingRewards.tiers.toSet(), LuckyImplingRewards.tables.keys)
        for ((tier, entries) in LuckyImplingRewards.tables) {
            assertTrue(entries.size >= 170, tier)
            assertEquals(1.0, entries.sumOf { it.weight }, .001, tier)
            for (entry in entries) {
                val item = checkNotNull(ServerCacheManager.getItem(entry.obj.asRSCM()))
                assertTrue(entry.minimum in 1..entry.maximum, entry.obj)
                assertTrue(entry.weight > 0, entry.obj)
                if (entry.noted) assertTrue(item.canCert, entry.obj)
                assertFalse(item.name.startsWith("Clue scroll"), entry.obj)
                assertFalse(entry.obj.contains("bloodhound") || entry.obj.contains("mimic"), entry.obj)
            }
        }
    }

    @Test fun `one lucky impling grants one reward from its chosen tier`() {
        for (tier in 0..4) for (value in listOf(0.0, .25, .75, .999999)) {
            val random = object : GameRandom {
                override fun of(maxExclusive: Int) = tier
                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
                override fun randomDouble() = value
            }
            val reward = LuckyImplingRewards.roll(random).single()
            assertTrue(LuckyImplingRewards.tables.getValue(LuckyImplingRewards.tiers[tier]).any { it.obj == reward.obj })
        }
    }
}
