package org.rsmod.api.farming.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinPhase
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingBinRegistry
import org.rsmod.api.farming.FarmingCacheTest
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchRegistry

internal class FarmingWorldPersistenceTest : FarmingCacheTest() {
    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `version one saves resolve every old patch and bin key after table migration`() {
        val before = checkNotNull(javaClass.getResourceAsStream("/farming-world-save-v1.json"))
            .bufferedReader().use { it.readText() }
        val loaded = assertNotNull(FarmingCodec.decode(mapper, before))
        val saved = FarmingCodec.encode(mapper, loaded.patches, loaded.bins)
        val reloaded = assertNotNull(FarmingCodec.decode(mapper, saved))
        val patches = FarmingPatchRegistry()
        val bins = FarmingBinRegistry()
        assertEquals(59, reloaded.patches.size)
        assertEquals(8, reloaded.bins.size)
        for ((index, row) in mapper.readTree(before).get("p").withIndex()) {
            val key = FarmingPatchKey(row.get("k").asText())
            assertNotNull(patches.byKey(key))
            val state = assertNotNull(reloaded.patches.getOrNull(key))
            assertEquals("potato", state.cropId)
            assertEquals(1, state.stage)
            assertEquals(3, state.rakeStage)
            assertEquals(3, state.lives)
            assertEquals(FarmingCompost.SUPERCOMPOST, state.compost)
            assertEquals(5_000_000_000L + index, state.lastGrowthMinute)
            assertTrue(state.paidProtected)
            assertTrue(state.plantXpPaid)
        }
        for ((index, row) in mapper.readTree(before).get("b").withIndex()) {
            val key = FarmingBinKey(row.get("k").asText())
            val def = assertNotNull(bins.byKey(key))
            val state = assertNotNull(reloaded.bins.getOrNull(key))
            assertEquals(FarmingBinPhase.OPEN, state.phase)
            assertEquals(FarmingBinProduct.SUPERCOMPOST, state.product)
            assertEquals(def.capacity, state.count)
            assertEquals(5_000_000_000L + index, state.closedMinute)
        }
        assertEquals(1, mapper.readTree(saved).get("v").asInt())
    }

    @Test
    fun `distant patches and bins sharing transmit slots retain separate saved states`() {
        val before = checkNotNull(javaClass.getResourceAsStream("/farming-world-save-v1.json"))
            .bufferedReader().use { it.readText() }
        val loaded = assertNotNull(FarmingCodec.decode(mapper, before))
        val sharedPatches = FarmingPatchRegistry().all().groupBy { it.transmitSlot }.values.first { it.size > 1 }
        val left = assertNotNull(loaded.patches.getOrNull(sharedPatches[0].key))
        val right = assertNotNull(loaded.patches.getOrNull(sharedPatches[1].key))
        assertNotSame(left, right)
        left.stage = 2
        assertEquals(1, right.stage)

        val sharedBins = FarmingBinRegistry().all().groupBy { it.transmitSlot }.values.first { it.size > 1 }
        val first = assertNotNull(loaded.bins.getOrNull(sharedBins[0].key))
        val second = assertNotNull(loaded.bins.getOrNull(sharedBins[1].key))
        assertNotSame(first, second)
        first.count = 1
        val reloaded = assertNotNull(FarmingCodec.decode(mapper, FarmingCodec.encode(mapper, loaded.patches, loaded.bins)))
        assertEquals(1, reloaded.bins.getOrNull(sharedBins[0].key)?.count)
        assertEquals(sharedBins[1].capacity, reloaded.bins.getOrNull(sharedBins[1].key)?.count)
        assertEquals(2, reloaded.patches.getOrNull(sharedPatches[0].key)?.stage)
        assertEquals(1, reloaded.patches.getOrNull(sharedPatches[1].key)?.stage)
    }
}
