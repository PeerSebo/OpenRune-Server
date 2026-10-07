package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom

class FarmingHarvestTest : FarmingCacheTest() {
    @Test
    fun `a flower is a single pick that empties the patch`() {
        val manager = manager()
        val state = grown(MARIGOLD)

        val result = manager.applyHarvest(state, marigold(), saveChanceOutOf256 = 0)

        assertEquals(FarmingHarvestResult.PICKED_LAST, result)
        assertNull(state.cropId, "the pick must empty the patch")
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage, "and leave it raked")
    }

    @Test
    fun `the flower pick releases the deferred planting XP exactly once`() {
        val manager = manager()
        val state = grown(MARIGOLD)

        manager.applyHarvest(state, marigold(), saveChanceOutOf256 = 0)

        assertTrue(manager.applyClaimPlantXp(state))
        assertFalse(manager.applyClaimPlantXp(state), "and never a second time")
    }

    @Test
    fun `claiming the planting XP before the pick still pays exactly once`() {
        val manager = manager()
        val state = grown(MARIGOLD)

        assertTrue(manager.applyClaimPlantXp(state))
        manager.applyHarvest(state, marigold(), saveChanceOutOf256 = 0)
        assertFalse(manager.applyClaimPlantXp(state), "the pick must not bank an already-paid debt")
    }

    @Test
    fun `a lives-based crop pays the planting XP on the pick that spends the last life`() {
        val manager = manager()
        val state = grown(POTATO)
        state.lives = 2

        assertEquals(
            FarmingHarvestResult.PICKED,
            manager.applyHarvest(state, potato(), saveChanceOutOf256 = 0),
        )
        assertTrue(manager.applyClaimPlantXp(state), "still claimable while the patch lives")
        assertEquals(
            FarmingHarvestResult.PICKED_LAST,
            manager.applyHarvest(state, potato(), saveChanceOutOf256 = 0),
        )
        assertFalse(manager.applyClaimPlantXp(state))
        assertNull(state.cropId)
    }

    @Test
    fun `a lives-based crop banks the debt when content never claimed it early`() {
        val manager = manager()
        val state = grown(POTATO)
        state.lives = 1

        assertEquals(
            FarmingHarvestResult.PICKED_LAST,
            manager.applyHarvest(state, potato(), saveChanceOutOf256 = 0),
        )
        assertTrue(manager.applyClaimPlantXp(state))
        assertFalse(manager.applyClaimPlantXp(state))
    }

    @Test
    fun `a produce crop keeps the plant and banks nothing`() {
        val manager = manager()
        val state = grown(APPLE)
        state.checkHealthClaimed = true
        state.produceCount = 2

        assertEquals(
            FarmingHarvestResult.PICKED,
            manager.applyHarvest(state, apple(), saveChanceOutOf256 = 0),
        )
        assertEquals(1, state.produceCount)
        assertEquals(APPLE, state.cropId, "a fruit tree stays put")

        assertEquals(
            FarmingHarvestResult.PICKED,
            manager.applyHarvest(state, apple(), saveChanceOutOf256 = 0),
        )
        assertEquals(0, state.produceCount)
        assertEquals(
            FarmingHarvestResult.NOT_HARVESTABLE,
            manager.applyHarvest(state, apple(), saveChanceOutOf256 = 0),
        )
        assertEquals(APPLE, state.cropId)
    }

    @Test
    fun `an unchecked plant cannot be harvested`() {
        val manager = manager()
        val state = grown(APPLE)
        state.produceCount = 3

        assertEquals(
            FarmingHarvestResult.NOT_HARVESTABLE,
            manager.applyHarvest(state, apple(), saveChanceOutOf256 = 0),
        )
    }

    @Test
    fun `checking a mature fruit tree makes its full first harvest available immediately`() {
        val manager = manager()
        val crop = apple()
        val state = grown(APPLE)

        assertTrue(manager.applyCheckHealth(state, crop, nowMinute = 1200))
        assertEquals(crop.produceCapacity, state.produceCount)
        repeat(crop.produceCapacity) {
            assertEquals(
                FarmingHarvestResult.PICKED,
                manager.applyHarvest(state, crop, saveChanceOutOf256 = 0),
            )
        }
        assertEquals(
            FarmingHarvestResult.NOT_HARVESTABLE,
            manager.applyHarvest(state, crop, saveChanceOutOf256 = 0),
        )
        assertEquals(APPLE, state.cropId)
    }

    @Test
    fun `checking a mature bush supplies its own produce capacity`() {
        val manager = manager()
        val bush = apple().copy(id = "redberry", patchType = FarmingPatchType.BUSH, produceCapacity = 4)
        val state = grown(bush.id)

        assertTrue(manager.applyCheckHealth(state, bush, nowMinute = 1200))
        assertEquals(4, state.produceCount)
    }

    @Test
    fun `rechecking a harvested tree cannot refill it and fruit regrows on its normal clock`() {
        val manager = manager()
        val crop = apple()
        val crops = FarmingCropRegistry(FarmingPatchRegistry()).apply { register(crop) }
        val growth = FarmingGrowth(FixedRandom(), crops)
        val state = grown(APPLE)
        val checkedAt = 1200L
        manager.applyCheckHealth(state, crop, nowMinute = checkedAt)
        repeat(crop.produceCapacity) { manager.applyHarvest(state, crop, saveChanceOutOf256 = 0) }

        assertFalse(manager.applyCheckHealth(state, crop, nowMinute = checkedAt))
        growth.resolve(state, nowMinute = checkedAt)
        assertEquals(0, state.produceCount)
        growth.resolve(state, nowMinute = checkedAt + crop.produceRegrowMinutes - 1)
        assertEquals(0, state.produceCount)
        growth.resolve(state, nowMinute = checkedAt + crop.produceRegrowMinutes)
        assertEquals(1, state.produceCount)
    }

    @Test
    fun `checking health never supplies fruit to immature dead diseased or chopped plants`() {
        val manager = manager()
        val crop = apple()
        val states =
            listOf(
                grown(APPLE).apply { stage-- },
                grown(APPLE).apply { dead = true },
                grown(APPLE).apply { diseased = true },
                grown(APPLE).apply { stump = true },
            )

        for (state in states) {
            assertFalse(manager.applyCheckHealth(state, crop, nowMinute = 1200))
            assertFalse(state.checkHealthClaimed)
            assertEquals(0, state.produceCount)
        }
    }

    @Test
    fun `a debt is not carried across a replant`() {
        val manager = manager()
        val state = grown(MARIGOLD)
        manager.applyHarvest(state, marigold(), saveChanceOutOf256 = 0)
        assertEquals(MARIGOLD, state.plantXpOwedCrop)

        state.cropId = POTATO
        state.plantXpOwedCrop = null
        state.plantXpPaid = false
        assertTrue(manager.applyClaimPlantXp(state))
        assertFalse(manager.applyClaimPlantXp(state))
    }

    @Test
    fun `uprooting a healthy unfinished crop releases no planting XP`() {
        val manager = manager()
        val crop = potato()
        val state = grown(POTATO).apply {
            stage = 0
            paidProtected = true
            compost = FarmingCompost.ULTRACOMPOST
        }

        manager.applyClear(state, crop)

        assertNull(state.cropId)
        assertFalse(manager.applyClaimPlantXp(state))
        assertFalse(state.paidProtected)
        assertEquals(FarmingCompost.NONE, state.compost)
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage)
    }

    @Test
    fun `clearing a crop that died before maturity still pays its planting XP once`() {
        val manager = manager()
        val state = grown(POTATO).apply {
            stage = 1
            dead = true
        }

        manager.applyClear(state, potato())

        assertEquals(POTATO, state.plantXpOwedCrop)
        assertTrue(manager.applyClaimPlantXp(state))
        assertFalse(manager.applyClaimPlantXp(state))
    }

    @Test
    fun `clearing a checked mature crop does not pay its planting XP twice`() {
        val manager = manager()
        val state = grown(APPLE)
        manager.applyCheckHealth(state, apple(), nowMinute = 1200)
        assertTrue(manager.applyClaimPlantXp(state))

        manager.applyClear(state, apple())

        assertFalse(manager.applyClaimPlantXp(state))
    }

    private fun grown(cropId: String): FarmingPatchState {
        val state = FarmingPatchState()
        state.cropId = cropId
        state.rakeStage = FarmingConstants.RAKE_STAGES
        state.stage = GROWN_STAGE
        state.lastGrowthMinute = 1000
        return state
    }

    private fun manager(): FarmingManager {
        val patches = FarmingPatchRegistry()
        val crops = FarmingCropRegistry(patches)
        crops.register(marigold())
        crops.register(potato())
        crops.register(apple())
        val random = FixedRandom()
        return FarmingManager(
            patches = patches,
            crops = crops,
            growth = FarmingGrowth(random, crops),
            renderer = FarmingRenderer(crops, FarmingTransmitVars()),
            random = random,
            preResolveHooks = { emptySet() },
        )
    }

    private fun marigold() =
        FarmingCrop(
            id = MARIGOLD,
            patchType = FarmingPatchType.FLOWER,
            growthStages = GROWN_STAGE + 1,
            osrsCycleMinutes = 5,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor("loc.marigold_seed"),
                    watered = FarmingAnchor("loc.marigold_seed_watered"),
                    diseased = FarmingAnchor("loc.marigold_1_diseased", stage = 1),
                    dead = FarmingAnchor("loc.marigold_1_dead", stage = 1),
                ),
            baseLives = 0,
            produceCapacity = 0,
        )

    private fun potato() =
        FarmingCrop(
            id = POTATO,
            patchType = FarmingPatchType.ALLOTMENT,
            growthStages = GROWN_STAGE + 1,
            osrsCycleMinutes = 10,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor("loc.potato_seed"),
                    diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                    dead = FarmingAnchor("loc.potato_1_dead", stage = 1),
                ),
            baseLives = 3,
        )

    private fun apple() =
        FarmingCrop(
            id = APPLE,
            patchType = FarmingPatchType.FRUIT_TREE,
            growthStages = GROWN_STAGE + 1,
            osrsCycleMinutes = 160,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor("loc.apple_tree_seedling"),
                    unchecked = FarmingAnchor("loc.apple_tree_claim_xp"),
                    grownChecked = FarmingAnchor("loc.apple_tree_fullygrown"),
                ),
            produceCapacity = 6,
            osrsProduceRegrowMinutes = 40,
        )

    private class FixedRandom : GameRandom {
        override fun of(maxExclusive: Int): Int = maxExclusive - 1

        override fun of(minInclusive: Int, maxInclusive: Int): Int = maxInclusive

        override fun randomDouble(): Double = 1.0
    }

    private companion object {
        private const val MARIGOLD = "marigold"
        private const val POTATO = "potato"
        private const val APPLE = "apple_tree"
        private const val GROWN_STAGE = 4
    }
}
