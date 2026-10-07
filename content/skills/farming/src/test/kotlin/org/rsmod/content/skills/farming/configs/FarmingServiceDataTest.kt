package org.rsmod.content.skills.farming.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.content.skills.farming.FarmingPatchGroups

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingServiceDataTest {
    @Test
    fun `all service rows preserve the original fixed definitions and order`() {
        FarmingServiceData.validate()
        assertRows("slots", FarmingServiceData.countSlots.map {
            listOf(it.component, it.item, it.fields.joinToString(","), it.publishedCap)
        })
        assertRows("companions", FarmingServiceData.companions.map {
            listOf(it.crop, it.protectsAll, it.allotments.joinToString(","))
        })
        assertRows("groups", FarmingServiceData.farms.map {
            listOf(it.flower, it.allotments.joinToString(","))
        })
        assertRows("gardeners", FarmingServiceData.gardeners.map {
            listOf(it.npc, it.choice, it.patches.joinToString(","))
        })
        assertRows("noteRules", FarmingServiceData.notingRules.map { listOf(it.item, it.allowed) })
        assertRows("compostOverrides", FarmingServiceData.compostIngredients.map {
            listOf(it.item, it.fill.name, it.beforeCrops)
        })
        assertRows("doseRows", FarmingServiceData.doses.map { listOf(it.family, it.dose, it.item) })
        assertRows("products", FarmingServiceData.compostProducts.values.map {
            listOf(it.product.name, it.item, it.binXpTenths, it.applicationXpTenths ?: "")
        })
    }

    @Test
    fun `named storage slots retain identity for special secateurs dispatch`() {
        val named = listOf(
            FarmingLeprechaunSlots.RAKE, FarmingLeprechaunSlots.DIBBER,
            FarmingLeprechaunSlots.SPADE, FarmingLeprechaunSlots.SECATEURS,
            FarmingLeprechaunSlots.TROWEL, FarmingLeprechaunSlots.BUCKET,
            FarmingLeprechaunSlots.COMPOST, FarmingLeprechaunSlots.SUPERCOMPOST,
            FarmingLeprechaunSlots.ULTRACOMPOST, FarmingLeprechaunSlots.PLANT_CURE,
        )
        named.forEachIndexed { index, slot -> assertSame(slot, FarmingLeprechaunSlots.COUNT_SLOTS[index]) }
        for (slot in named) {
            assertTrue(slot.capacity <= slot.publishedCap)
            assertTrue(slot.capacity <= slot.rawFieldCapacity)
            assertEquals(125, ServerCacheManager.fromComponent("component.farming_tools:${slot.component}").packed ushr 16)
            assertEquals(126, ServerCacheManager.fromComponent("component.farming_tools_side:${slot.component}").packed ushr 16)
        }
    }

    @Test
    fun `derived produce services preserve original precedence and exclusion`() {
        assertEquals(fixture("noteable").map { it[1] }.toSet(), FarmingLeprechaunProduce.NOTEABLE)
        val actual = FarmingServiceData.compostClassification.map { (id, definition) ->
            assertEquals(definition.second.asRSCM(RSCMType.OBJ), id)
            definition.second to definition.first.name
        }.toMap()
        assertEquals(fixture("classification").associate { it[1] to it[2] }, actual)
        assertFalse("obj.cabbage" in FarmingLeprechaunProduce.NOTEABLE)
        assertTrue("obj.willow_branch" in FarmingLeprechaunProduce.NOTEABLE)
        assertEquals("TOMATOES", actual["obj.tomato"])
        assertEquals("SUPERCOMPOSTABLE", actual["obj.unidentified_toadflax"])
        assertEquals("COMPOSTABLE", actual["obj.cactus_potato"])
        assertNull(actual["obj.cert_watermelon"])
    }

    @Test
    fun `group lookups share one farm and isolated patches stay ungrouped`() {
        for ((_, flower, allotments) in fixture("groups")) {
            val farm = checkNotNull(FarmingPatchGroups.forPatch(flower))
            for (patch in allotments.split(',')) assertSame(farm, FarmingPatchGroups.forPatch(patch))
        }
        assertNull(FarmingPatchGroups.forPatch("farming_veg_patch_9"))
        assertNull(FarmingPatchGroups.forPatch("farming_herb_patch_1"))
        assertTrue(FarmingProtection.flowerProtects("white_lily", "snape_grass"))
        assertFalse(FarmingProtection.flowerProtects("rosemary", "tomato"))
        assertEquals(setOf("sweetcorn"), FarmingProtection.SCARECROW_PROTECTS)
    }

    @Test
    fun `compost rewards retain fractional XP and exclude rotten tomatoes from application`() {
        assertEquals(listOf(4.5, 8.5, 10.0, 4.5), FarmingServiceData.compostProducts.values.map { it.binXp })
        assertEquals(listOf(0.0, 18.0, 26.0, 36.0), FarmingCompost.entries.map(FarmingServiceData::compostXp))
        assertNull(FarmingServiceData.compostProducts.getValue(FarmingBinProduct.ROTTEN_TOMATOES).applicationXp)
        assertEquals(
            mapOf("obj.bucket_compost" to FarmingCompost.COMPOST,
                "obj.bucket_supercompost" to FarmingCompost.SUPERCOMPOST,
                "obj.bucket_ultracompost" to FarmingCompost.ULTRACOMPOST),
            FarmingServiceData.compostByObj.mapKeys { RSCM.getReverseMapping(RSCMType.OBJ, it.key) }
                .mapValues { it.value.first },
        )
    }

    @Test
    fun `service validation rejects overlapping fields missing patches and malformed doses`() {
        val slots = FarmingServiceData.countSlots
        assertThrows(IllegalArgumentException::class.java) {
            FarmingServiceData.validateCountSlots(slots + slots.first())
        }
        assertThrows(IllegalArgumentException::class.java) {
            FarmingServiceData.validateCountSlots(listOf(slots.first().copy(publishedCap = 0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FarmingServiceData.validateGardeners(listOf(FarmingServiceData.gardeners.first().copy(patches = listOf("missing_patch"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FarmingServiceData.validateDoses(FarmingServiceData.doses.drop(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FarmingServiceData.validateDoses(FarmingServiceData.doses.map { if (it.family == "watering_can" && it.dose == 0) it.copy(dose = 1) else it })
        }
    }

    private fun assertRows(name: String, values: List<List<Any>>) {
        assertEquals(fixture(name), values.mapIndexed { index, row -> listOf(index.toString()) + row.map(Any::toString) }, name)
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/farming-service-$name.tsv"))
            .bufferedReader().useLines { lines -> lines.map { it.split('\t') }.toList() }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
