package org.rsmod.content.skills.thieving.scripts

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.thieving.configs.ThievingNpcs

@ResourceLock("ServerCacheManager")
class PrifddinasLootTest {
    @Test
    fun `seed tokkul and direct coin targets are not blocked by unrelated coin pouches`() {
        for (target in listOf(ThievingNpcs.masterFarmer, ThievingNpcs.tzhaarHur, ThievingNpcs.villager, ThievingNpcs.digsiteWorkman)) {
            assertTrue(!target.usesCoinPouches)
        }
        assertTrue(ThievingNpcs.knightOfArdougne.usesCoinPouches)
        assertTrue(ThievingNpcs.hamMember.usesCoinPouches)
    }

    @Test
    fun `rogue bonus doubles both the shard and enhanced seed from successful rolls`() {
        val ranges = mutableListOf<Int>()
        val drops = rollPrifddinasLoot(true) { ranges += it; 0 }
        assertEquals(listOf(35, 1024), ranges)
        assertEquals(listOf("obj.prif_crystal_shard", "obj.prif_teleport_seed"), drops.map { it.obj })
        assertEquals(listOf(2, 2), drops.map { it.count })
    }

    @Test
    fun `without rogue bonus each successful bonus roll yields one item`() {
        assertEquals(listOf(1, 1), rollPrifddinasLoot(false) { 0 }.map { it.count })
    }

    @Test
    fun `rogue equipment does not create loot from failed bonus rolls`() {
        assertTrue(rollPrifddinasLoot(true) { it - 1 }.isEmpty())
        val seedOnly = rollPrifddinasLoot(true) { if (it == 1024) 0 else 1 }
        assertEquals(listOf(ThievingLoot.ThievingDrop("obj.prif_teleport_seed", 2)), seedOnly)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
