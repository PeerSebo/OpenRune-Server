package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom

/** Resolve elapsed cycles exactly once; these model tests do not depend on cache rendering. */
class FarmingGrowthTest : FarmingCacheTest() {
    @Test
    fun `anchors a fresh plant to the boundary at or before now`() {
        val fixture = fixture()
        val state = FarmingPatchState()
        state.cropId = POTATO
        state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(1234, CYCLE)

        assertFalse(fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1239))
        assertEquals(0, state.stage)

        assertTrue(fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240))
        assertEquals(1, state.stage)
        assertEquals(1240, state.lastGrowthMinute)
    }

    @Test
    fun `consumes the boundary, not the present`() {
        val fixture = fixture()
        val state = plantedAt(1230)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1249)
        assertEquals(1, state.stage)
        assertEquals(1240, state.lastGrowthMinute)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1250)
        assertEquals(2, state.stage)
        assertEquals(1250, state.lastGrowthMinute)
    }

    @Test
    fun `resolving twice in the same minute rolls nothing the second time`() {
        val fixture = fixture(diseaseRoll = 0) // every roll would contract disease
        val state = plantedAt(1230)

        assertTrue(fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240))
        val rollsAfterFirst = fixture.random.rolls
        assertFalse(fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240))
        assertEquals(rollsAfterFirst, fixture.random.rolls)
    }

    @Test
    fun `rolls one disease check per elapsed cycle, never per observation`() {
        val fixture = fixture(diseaseRoll = LARGE) // never contracts disease
        val state = plantedAt(1230)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1265)
        assertEquals(3, state.stage)
        assertEquals(1260, state.lastGrowthMinute)
        // Stage 0 -> 1, 1 -> 2 and 2 -> 3 are disease-eligible; entering the grown stage is not.
        assertEquals(3, fixture.random.rolls)
    }

    @Test
    fun `stops at the grown stage and rolls nothing further`() {
        val fixture = fixture(diseaseRoll = LARGE)
        val state = plantedAt(1230)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + CYCLE * 50L)
        assertEquals(GROWN_STAGE, state.stage)
        assertFalse(state.diseased)
        assertEquals(GROWN_STAGE - 1, fixture.random.rolls)
    }

    @Test
    fun `a diseased crop stops growing and dies on the next elapsed cycle`() {
        val fixture = fixture(diseaseRoll = 0)
        val state = plantedAt(1230)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240)
        assertEquals(1, state.stage)
        assertTrue(state.diseased)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1250)
        assertEquals(1, state.stage, "a diseased crop must not advance")
        assertFalse(state.diseased)
        assertTrue(state.dead)
    }

    @Test
    fun `watering lapses after the cycle it covered`() {
        val fixture = fixture(diseaseRoll = LARGE)
        val state = plantedAt(1230)
        state.watered = true

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240)
        assertFalse(state.watered)
    }

    @Test
    fun `protection makes a crop immune without touching the clock`() {
        val fixture = fixture(diseaseRoll = 0)
        val state = plantedAt(1230)
        state.protected = true

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1260)
        assertFalse(state.diseased)
        assertEquals(3, state.stage)
        assertEquals(0, fixture.random.rolls, "a protected crop must not consume a roll")
    }

    @Test
    fun `a clock that moves backwards re-anchors instead of granting cycles`() {
        val fixture = fixture(diseaseRoll = LARGE)
        val state = plantedAt(2000)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1000)
        assertEquals(0, state.stage)
        assertEquals(1000, state.lastGrowthMinute)
    }

    @Test
    fun `fruit regrows one per interval up to capacity and then idles`() {
        val fixture = fixture(diseaseRoll = LARGE)
        val state = FarmingPatchState()
        state.cropId = FRUIT
        state.stage = FRUIT_GROWN_STAGE
        state.checkHealthClaimed = true
        state.lastGrowthMinute = 1200
        state.lastProduceMinute = 1200

        fixture.growth.resolve(state, FRUIT_TREE, nowMinute = 1200 + PRODUCE_CYCLE * 3L)
        assertEquals(3, state.produceCount)

        fixture.growth.resolve(state, FRUIT_TREE, nowMinute = 1200 + PRODUCE_CYCLE * 100L)
        assertEquals(FRUIT_CAPACITY, state.produceCount)

        state.produceCount--
        val now = 1200 + PRODUCE_CYCLE * 100L
        fixture.growth.resolve(state, FRUIT_TREE, nowMinute = now)
        assertEquals(FRUIT_CAPACITY - 1, state.produceCount)
        fixture.growth.resolve(state, FRUIT_TREE, nowMinute = now + PRODUCE_CYCLE)
        assertEquals(FRUIT_CAPACITY, state.produceCount)
    }

    @Test
    fun `an unchecked tree waits for check-health to supply its initial crop`() {
        val fixture = fixture()
        val state = FarmingPatchState()
        state.cropId = FRUIT
        state.stage = FRUIT_GROWN_STAGE
        state.lastGrowthMinute = 1200
        state.lastProduceMinute = 1200

        // Unchecked fruit trees cannot render or award produce before their health check.
        fixture.growth.resolve(state, FRUIT_TREE, nowMinute = 1200 + PRODUCE_CYCLE * 5L)
        assertEquals(0, state.produceCount)
    }

    @Test
    fun `a raked patch left alone goes overgrown one stage per cycle`() {
        val fixture = fixture()
        val state = rakedAt(1230)
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage)

        val weedCycle = ALLOTMENT.weedCycleMinutes
        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + weedCycle.toLong())
        assertEquals(2, state.rakeStage)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + weedCycle * 2L)
        assertEquals(1, state.rakeStage)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + weedCycle * 3L)
        assertEquals(0, state.rakeStage, "the patch is fully overgrown and needs raking again")
    }

    @Test
    fun `weeds consume the boundary, so resolving twice in a minute does not double-decrement`() {
        val fixture = fixture()
        val state = rakedAt(1230)
        val weedCycle = ALLOTMENT.weedCycleMinutes
        val now = 1230 + weedCycle + (weedCycle - 1L)

        assertTrue(fixture.growth.resolve(state, ALLOTMENT, nowMinute = now))
        assertEquals(2, state.rakeStage)
        assertEquals(1230L + weedCycle, state.lastGrowthMinute)

        assertFalse(fixture.growth.resolve(state, ALLOTMENT, nowMinute = now))
        assertEquals(2, state.rakeStage)
    }

    @Test
    fun `an overgrown patch stays overgrown and never goes negative`() {
        val fixture = fixture()
        val state = rakedAt(1230)
        state.rakeStage = 0

        fixture.growth.resolve(
            state,
            ALLOTMENT,
            nowMinute = 1230 + ALLOTMENT.weedCycleMinutes * 50L,
        )
        assertEquals(0, state.rakeStage)
    }

    @Test
    fun `a long absence overgrows the patch without wrapping past zero`() {
        val fixture = fixture()
        val state = rakedAt(1230)

        fixture.growth.resolve(
            state,
            ALLOTMENT,
            nowMinute = 1230 + ALLOTMENT.weedCycleMinutes * 999L,
        )
        assertEquals(0, state.rakeStage)
    }

    @Test
    fun `a planted patch does not grow weeds`() {
        val fixture = fixture()
        val state = plantedAt(1230)
        state.rakeStage = FarmingConstants.RAKE_STAGES

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + CYCLE * 3L)
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage)
        assertEquals(3, state.stage)
    }

    @Test
    fun `weeds never grow under a planted crop whose id is no longer registered`() {
        val fixture = fixture()
        val state = FarmingPatchState()
        state.cropId = "a_crop_that_was_renamed"
        state.rakeStage = FarmingConstants.RAKE_STAGES
        state.stage = 2
        state.lastGrowthMinute = 1230

        assertFalse(fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1230 + 10_000L))
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage)
        assertEquals(2, state.stage)
        assertEquals(1230, state.lastGrowthMinute, "the crop's own clock must be untouched")
    }

    @Test
    fun `a patch of unknown family never weeds over`() {
        val fixture = fixture()
        val state = rakedAt(1230)

        assertFalse(fixture.growth.resolve(state, patchType = null, nowMinute = 1230 + 10_000L))
        assertEquals(FarmingConstants.RAKE_STAGES, state.rakeStage)
    }

    @Test
    fun `a diseased crop that cannot die lapses instead of freezing the patch`() {
        val fixture = fixture()
        val state = FarmingPatchState()
        state.cropId = IMMORTAL
        state.diseased = true
        state.lastGrowthMinute = 1230

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1240)
        assertFalse(state.diseased)
        assertFalse(state.dead)

        fixture.growth.resolve(state, ALLOTMENT, nowMinute = 1250)
        assertEquals(1, state.stage, "the patch resumes growing once the disease lapses")
    }

    private fun rakedAt(minute: Long): FarmingPatchState {
        val state = FarmingPatchState()
        state.rakeStage = FarmingConstants.RAKE_STAGES
        state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(minute, ALLOTMENT.weedCycleMinutes)
        return state
    }

    private fun plantedAt(minute: Long): FarmingPatchState {
        val state = FarmingPatchState()
        state.cropId = POTATO
        state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(minute, CYCLE)
        return state
    }

    private fun fixture(diseaseRoll: Int = LARGE): Fixture {
        val random = CountingRandom(diseaseRoll)
        val crops = FarmingCropRegistry(FarmingPatchRegistry())
        crops.register(
            FarmingCrop(
                id = POTATO,
                patchType = FarmingPatchType.ALLOTMENT,
                growthStages = GROWN_STAGE + 1,
                osrsCycleMinutes = osrs(CYCLE),
                visuals =
                    FarmingCropVisuals(
                        growing = FarmingAnchor("loc.potato_seed"),
                        watered = FarmingAnchor("loc.potato_seed_watered"),
                        diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                        dead = FarmingAnchor("loc.potato_1_dead", stage = 1),
                    ),
                diseaseChance = 27,
                baseLives = 3,
            )
        )
        crops.register(
            FarmingCrop(
                id = FRUIT,
                patchType = FarmingPatchType.FRUIT_TREE,
                growthStages = FRUIT_GROWN_STAGE + 1,
                osrsCycleMinutes = osrs(160),
                visuals = FarmingCropVisuals(growing = FarmingAnchor("loc.apple_tree_1")),
                produceCapacity = FRUIT_CAPACITY,
                osrsProduceRegrowMinutes = osrs(PRODUCE_CYCLE),
            )
        )
        crops.register(
            FarmingCrop(
                id = IMMORTAL,
                patchType = FarmingPatchType.ALLOTMENT,
                growthStages = GROWN_STAGE + 1,
                osrsCycleMinutes = osrs(CYCLE),
                visuals =
                    FarmingCropVisuals(
                        growing = FarmingAnchor("loc.potato_seed"),
                        // Even invalid saved disease without a dead anchor must not freeze growth.
                        diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                    ),
                diseaseChance = 0,
            )
        )
        return Fixture(random, FarmingGrowth(random, crops))
    }

    private class Fixture(val random: CountingRandom, val growth: FarmingGrowth)

    private class CountingRandom(private val next: Int) : GameRandom {
        var rolls: Int = 0
            private set

        override fun of(maxExclusive: Int): Int {
            rolls++
            return next.coerceIn(0, maxExclusive - 1)
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            rolls++
            return next.coerceIn(minInclusive, maxInclusive)
        }

        override fun randomDouble(): Double = 0.0
    }

    private companion object {
        private val ALLOTMENT = FarmingPatchType.ALLOTMENT
        private val FRUIT_TREE = FarmingPatchType.FRUIT_TREE

        private fun osrs(effectiveMinutes: Int): Int =
            effectiveMinutes * FarmingConstants.GROWTH_SPEED_MULTIPLIER

        private const val POTATO = "potato"
        private const val FRUIT = "apple_tree"
        private const val IMMORTAL = "cannot_die"

        private const val CYCLE = 10
        private const val GROWN_STAGE = 4
        private const val FRUIT_GROWN_STAGE = 6
        private const val FRUIT_CAPACITY = 6
        private const val PRODUCE_CYCLE = 40

        private const val LARGE = 127
    }
}
