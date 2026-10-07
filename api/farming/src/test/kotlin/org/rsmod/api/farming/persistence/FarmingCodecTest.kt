package org.rsmod.api.farming.persistence

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.farming.FarmingBinFill
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinPhase
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingBinState
import org.rsmod.api.farming.FarmingBins
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchState
import org.rsmod.api.farming.FarmingPatches

class FarmingCodecTest {
    private val mapper: ObjectMapper =
        ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun `round trips every field of a populated patch`() {
        val original = FarmingPatches()
        original.put(HERB_KEY, populatedState())

        val json = FarmingCodec.encode(mapper, original, bins = null)
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).patches

        val state = assertNotNull(decoded.getOrNull(HERB_KEY))
        assertEquals("ranarr", state.cropId)
        assertEquals(2, state.stage)
        assertEquals(3, state.rakeStage)
        assertTrue(state.watered)
        assertTrue(state.diseased)
        assertTrue(state.dead)
        assertTrue(state.stump)
        assertEquals(5, state.lives)
        assertEquals(FarmingCompost.ULTRACOMPOST, state.compost)
        assertTrue(state.protected)
        assertTrue(state.paidProtected)
        assertEquals(28_412_350L, state.lastGrowthMinute)
        assertEquals(28_412_320L, state.lastProduceMinute)
        assertTrue(state.checkHealthClaimed)
        assertTrue(state.plantXpPaid)
        assertEquals(4, state.produceCount)
    }

    @Test
    fun `epoch minutes survive as longs, not truncated ints`() {
        val patches = FarmingPatches()
        val state = FarmingPatchState()
        state.cropId = "guam"
        // Exercise epoch-minute values beyond Int range.
        state.lastGrowthMinute = 5_000_000_000L
        state.lastProduceMinute = 4_000_000_000L
        patches.put(HERB_KEY, state)

        val decoded = roundTrip(patches).patches
        val loaded = assertNotNull(decoded.getOrNull(HERB_KEY))
        assertEquals(5_000_000_000L, loaded.lastGrowthMinute)
        assertEquals(4_000_000_000L, loaded.lastProduceMinute)
    }

    @Test
    fun `pristine patches are not written`() {
        val patches = FarmingPatches()
        patches.getOrCreate(HERB_KEY)
        patches.getOrCreate(FarmingPatchKey("farming_veg_patch_2"))

        assertEquals(0, roundTrip(patches).patches.size)
    }

    @Test
    fun `an empty document decodes to an empty farm`() {
        val decoded = assertNotNull(FarmingCodec.decode(mapper, "{}"))
        assertEquals(0, decoded.patches.size)
        assertEquals(0, decoded.bins.size)
    }

    @Test
    fun `an unknown compost tier falls back to none rather than failing the login`() {
        val json = """{"v":1,"p":[{"k":"farming_herb_patch_1","c":"guam","cp":"MEGACOMPOST"}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).patches
        val state = assertNotNull(decoded.getOrNull(HERB_KEY))
        assertEquals(FarmingCompost.NONE, state.compost)
    }

    @Test
    fun `a row without a key is dropped`() {
        val json = """{"v":1,"p":[{"k":"","c":"guam"}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).patches
        assertEquals(0, decoded.size)
        assertNull(decoded.getOrNull(FarmingPatchKey("")))
    }

    @Test
    fun `round trips every field of a closed compost bin`() {
        val bins = FarmingBins()
        val original = FarmingBinState()
        original.phase = FarmingBinPhase.ROTTING
        original.fill = FarmingBinFill.SUPERCOMPOSTABLE
        original.product = FarmingBinProduct.SUPERCOMPOST
        original.count = 15
        original.closedMinute = 28_412_350L
        bins.put(BIN_KEY, original)

        val state = assertNotNull(roundTrip(bins = bins).bins.getOrNull(BIN_KEY))
        assertEquals(FarmingBinPhase.ROTTING, state.phase)
        assertEquals(FarmingBinFill.SUPERCOMPOSTABLE, state.fill)
        assertEquals(FarmingBinProduct.SUPERCOMPOST, state.product)
        assertEquals(15, state.count)
        assertEquals(28_412_350L, state.closedMinute)
    }

    @Test
    fun `an upgraded bin keeps the product it was upgraded to, not the one its load implies`() {
        val bins = FarmingBins()
        val original = FarmingBinState()
        original.phase = FarmingBinPhase.OPEN
        original.fill = FarmingBinFill.SUPERCOMPOSTABLE
        original.product = FarmingBinProduct.ULTRACOMPOST
        original.count = 9
        bins.put(BIN_KEY, original)

        val state = assertNotNull(roundTrip(bins = bins).bins.getOrNull(BIN_KEY))
        assertEquals(FarmingBinProduct.ULTRACOMPOST, state.product)
        assertEquals(9, state.count)
    }

    @Test
    fun `pristine bins are not written`() {
        val bins = FarmingBins()
        bins.getOrCreate(BIN_KEY)
        bins.getOrCreate(FarmingBinKey("farming_compost_bin_2"))

        assertEquals(0, roundTrip(bins = bins).bins.size)
    }

    @Test
    fun `a document written before compost bins existed decodes to no bins`() {
        val json = """{"v":1,"p":[{"k":"farming_herb_patch_1","c":"guam"}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json))
        assertEquals(1, decoded.patches.size)
        assertEquals(0, decoded.bins.size)
    }

    @Test
    fun `an unknown bin phase or product falls back rather than failing the login`() {
        val json = """{"v":1,"b":[{"k":"farming_compost_bin_1","ph":"FERMENTING","pd":"MULCH"}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).bins
        val state = assertNotNull(decoded.getOrNull(BIN_KEY))
        assertEquals(FarmingBinPhase.FILLING, state.phase)
        assertEquals(FarmingBinFill.NONE, state.fill)
        assertNull(state.product)
    }

    @Test
    fun `a closed bin holding nothing is reset rather than left unrenderable`() {
        // A rotting bin without a product has no usable cache state.
        val json = """{"v":1,"b":[{"k":"farming_compost_bin_1","ph":"ROTTING","n":15}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).bins
        val state = assertNotNull(decoded.getOrNull(BIN_KEY))
        assertEquals(FarmingBinPhase.FILLING, state.phase)
        assertEquals(0, state.count)
        assertTrue(state.isPristine())
    }

    @Test
    fun `an unknown phase discards its stale count and fill instead of trapping an empty bin`() {
        val json =
            """{"v":1,"b":[{"k":"farming_compost_bin_1","ph":"FERMENTING","f":"COMPOSTABLE","pd":"COMPOST","n":15}]}"""

        val state = assertNotNull(FarmingCodec.decode(mapper, json)?.bins?.getOrNull(BIN_KEY))

        assertTrue(state.isPristine())
    }

    @Test
    fun `a filling bin with no recognized filling class is reset entirely`() {
        for (fill in listOf("NONE", "UNKNOWN")) {
            val json =
                """{"v":1,"b":[{"k":"farming_compost_bin_1","ph":"FILLING","f":"$fill","n":15}]}"""

            val state = assertNotNull(FarmingCodec.decode(mapper, json)?.bins?.getOrNull(BIN_KEY))

            assertTrue(state.isPristine())
        }
    }

    @Test
    fun `a product phase with no buckets becomes usable empty storage`() {
        for (phase in listOf("ROTTING", "READY", "OPEN")) {
            val json =
                """{"v":1,"b":[{"k":"farming_compost_bin_1","ph":"$phase","pd":"COMPOST","n":0,"cm":1200}]}"""

            val state = assertNotNull(FarmingCodec.decode(mapper, json)?.bins?.getOrNull(BIN_KEY))

            assertTrue(state.isPristine())
        }
    }

    @Test
    fun `a bin row without a key is dropped`() {
        val json = """{"v":1,"b":[{"k":"","ph":"OPEN","pd":"COMPOST","n":3}]}"""
        val decoded = assertNotNull(FarmingCodec.decode(mapper, json)).bins
        assertEquals(0, decoded.size)
    }

    @Test
    fun `patches and bins share one document and survive each other`() {
        val patches = FarmingPatches()
        patches.put(HERB_KEY, populatedState())
        val bins = FarmingBins()
        val bin = FarmingBinState()
        bin.phase = FarmingBinPhase.OPEN
        bin.product = FarmingBinProduct.ROTTEN_TOMATOES
        bin.count = 4
        bins.put(BIN_KEY, bin)

        val decoded = roundTrip(patches, bins)
        assertEquals("ranarr", assertNotNull(decoded.patches.getOrNull(HERB_KEY)).cropId)
        assertEquals(4, assertNotNull(decoded.bins.getOrNull(BIN_KEY)).count)
    }

    private fun roundTrip(
        patches: FarmingPatches? = null,
        bins: FarmingBins? = null,
    ): FarmingDocument =
        assertNotNull(FarmingCodec.decode(mapper, FarmingCodec.encode(mapper, patches, bins)))

    private fun populatedState(): FarmingPatchState {
        val state = FarmingPatchState()
        state.cropId = "ranarr"
        state.stage = 2
        state.rakeStage = 3
        state.watered = true
        state.diseased = true
        state.dead = true
        state.stump = true
        state.lives = 5
        state.compost = FarmingCompost.ULTRACOMPOST
        state.protected = true
        state.paidProtected = true
        state.lastGrowthMinute = 28_412_350L
        state.lastProduceMinute = 28_412_320L
        state.checkHealthClaimed = true
        state.plantXpPaid = true
        state.produceCount = 4
        return state
    }

    private companion object {
        private val HERB_KEY = FarmingPatchKey("farming_herb_patch_1")
        private val BIN_KEY = FarmingBinKey("farming_compost_bin_1")
    }
}
