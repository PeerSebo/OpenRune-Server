package org.rsmod.content.skills.herblore

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class HerbloreTableTest {
    @Test
    fun `recipe lookup accepts either item order and rejects two unrelated ingredients`() {
        val forward = HerbloreDefinitions.findPotionCandidates("obj.guamvial", "obj.eye_of_newt")
        assertFalse(forward.isEmpty())
        assertEquals(forward, HerbloreDefinitions.findPotionCandidates("obj.eye_of_newt", "obj.guamvial"))
        assertTrue(HerbloreDefinitions.findPotionCandidates("obj.eye_of_newt", "obj.limpwurt_root").isEmpty())
        assertTrue(HerbloreDefinitions.findPotionCandidates("obj.coins", "obj.guamvial").isEmpty())
    }

    @Test
    fun `six recipe families are loaded from cache tables`() {
        with(HerbloreDefinitions) {
            for (rows in listOf(unfinishedPotions, finishedPotions, cleaningHerbs, barbarianMixes, swampTars, crushingRecipes)) {
                assertFalse(rows.isEmpty())
            }
            assertTrue(unfinishedPotions.any { it.herbItem.internalName == "obj.guam_leaf" })
            assertTrue(unfinishedPotions.any { it.herbItem.internalName == "obj.torstol" })
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() {
            ServerCacheManager.init(241).close()
        }
    }
}
