package org.rsmod.content.minigames.giantsfoundry.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class FoundryCatalogueTest {
    @Test
    fun `recorded heat ramp repeats its final increment to reach both temperature bounds`() {
        assertEquals(listOf(7, 8, 9, 11, 13, 15, 17, 19, 21, 24, 27, 30, 33, 37, 41, 45, 49, 53, 57, 62, 67, 72, 77, 83, 89), FoundryData.temperatureSteps)
        for (start in 0..1000) {
            var heat = start
            var cool = start
            for (elapsed in 0..FoundryData.temperatureSteps.size) {
                val step = FoundryData.temperatureStep(elapsed)
                heat = (heat + step - 1).coerceAtMost(1000)
                cool = (cool - step).coerceAtLeast(0)
            }
            assertEquals(1000, heat)
            assertEquals(0, cool)
        }
        assertEquals(89, FoundryData.temperatureStep(100))
        assertThrows(IllegalArgumentException::class.java) { FoundryData.temperatureStep(-1) }
    }

    @Test
    fun `native mould lists retain free and unlockable parts with their exact scores`() {
        assertEquals(listOf("Narrow", "Light", "Flat", "Broad", "Heavy", "Spiked"), FoundryData.words.values.toList())
        assertEquals(33, FoundryData.moulds.size)
        assertEquals(18, FoundryData.moulds.count { it.unlockVarbit == null })
        for (part in FoundryMouldPart.entries) {
            val rows = FoundryData.moulds.filter { it.part == part }
            assertEquals((1..11).toList(), rows.map { it.index })
            assertTrue(rows.take(5).all { it.unlockVarbit != null })
            assertTrue(rows.drop(5).all { it.unlockVarbit == null })
        }
        val spiker = requireNotNull(FoundryData.mould(FoundryMouldPart.FORTE, 1))
        assertEquals("Spiker!", spiker.name)
        assertEquals(79, spiker.level)
        assertEquals(22, spiker.score(6))
        assertEquals("varbit.giants_foundry_unlocked_mould_ricasso_11", spiker.unlockVarbit)
    }

    @Test
    fun `native mould combinations reproduce both observed starting qualities`() {
        val broadHeavy = listOf(
            requireNotNull(FoundryData.mould(FoundryMouldPart.FORTE, 8)),
            requireNotNull(FoundryData.mould(FoundryMouldPart.BLADE, 8)),
            requireNotNull(FoundryData.mould(FoundryMouldPart.TIP, 6)),
        )
        assertEquals(94, 50 + FoundryData.mouldScore(4, 5, broadHeavy))
        val heavyFlat = listOf(
            requireNotNull(FoundryData.mould(FoundryMouldPart.FORTE, 8)),
            requireNotNull(FoundryData.mould(FoundryMouldPart.BLADE, 6)),
            requireNotNull(FoundryData.mould(FoundryMouldPart.TIP, 7)),
        )
        assertEquals(54, 10 + FoundryData.mouldScore(5, 3, heavyFlat))
    }

    @Test
    fun `native reward catalogue contains every item and exactly one unlock for each paid mould`() {
        assertEquals((1..24).toList(), FoundryData.rewards.map { it.index })
        val items = FoundryData.rewards.filter { it.item != null }
        assertEquals(listOf(2000, 300, 15, 200, 4000, 4000, 3500, 3500, 5000), items.map { it.cost })
        assertEquals("obj.giants_foundry_ore_pack".asRSCM(), items[3].item!!.asRSCM())
        assertEquals("obj.giants_foundry_colossal_blade".asRSCM(), items[8].item!!.asRSCM())
        val moulds = FoundryData.rewards.mapNotNull { it.mould }
        assertEquals(15, moulds.size)
        assertEquals(FoundryData.moulds.filter { it.unlockVarbit != null }.toSet(), moulds.toSet())
    }

    @Test
    fun `smelting requirements and supported metals derive from the smithing catalogue`() {
        assertEquals(listOf("bronze", "iron", "steel", "mithril", "adamant", "rune"), FoundryData.metals.map { it.key })
        assertEquals(listOf(15, 15, 30, 50, 70, 85), FoundryData.metals.map { it.level })
        for (metal in FoundryData.metals) {
            assertEquals(FoundryMaterial(metal, 1), FoundryData.materials[metal.bar.asRSCM()])
            assertNotNull(ServerCacheManager.getVarbit(metal.countVarbit.asRSCM()))
        }
    }

    @Test
    fun `equipment is worth one fewer smithing bar and ammunition is excluded`() {
        assertEquals(1, FoundryData.materials["obj.bronze_scimitar".asRSCM()]?.bars)
        assertEquals(2, FoundryData.materials["obj.iron_platelegs".asRSCM()]?.bars)
        assertEquals(4, FoundryData.materials["obj.rune_platebody".asRSCM()]?.bars)
        assertNull(FoundryData.materials["obj.bronze_dagger".asRSCM()])
        assertNull(FoundryData.materials["obj.black_platebody".asRSCM()])
        assertNull(FoundryData.materials["obj.gold_bar".asRSCM()])
        assertNull(FoundryData.materials["obj.silver_bar".asRSCM()])
        assertTrue(FoundryData.materials.values.all { it.bars in 1..4 })
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
