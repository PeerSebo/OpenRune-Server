package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom

/**
 * Write companion protection before resolving elapsed growth; reversing that order leaves one
 * unprotected disease roll.
 */
class FarmingProtectionOrderingTest : FarmingCacheTest() {
    @Test
    fun `a flag set before the resolve protects every pending cycle`() {
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)

        state.protected = true
        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE * 3L)

        assertFalse(state.diseased, "no pending cycle may roll against the old flag")
        assertEquals(3, state.stage)
        assertEquals(0, fixture.random.rolls, "a protected patch must not consume a roll at all")
    }

    @Test
    fun `a flag set after the resolve is one resolution late`() {
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)

        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE.toLong())
        state.protected = true

        assertTrue(state.diseased, "this is exactly the lag the pre-resolve hook removes")
    }

    @Test
    fun `clearing protection before the resolve exposes the patch immediately`() {
        // A dead companion must stop protection before this pass resolves disease.
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)
        state.protected = true

        state.protected = false
        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE.toLong())

        assertTrue(state.diseased)
    }

    @Test
    fun `protection does not stop growth, only disease`() {
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)
        state.protected = true

        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE * 4L)

        assertEquals(GROWN_STAGE, state.stage)
        assertFalse(state.diseased)
        assertFalse(state.dead)
    }

    @Test
    fun `paid protection alone is enough to grant immunity`() {
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)
        state.paidProtected = true

        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE * 3L)

        assertFalse(state.protected, "the companion flag stays off; only the payment is in force")
        assertFalse(state.diseased)
        assertEquals(3, state.stage)
        assertEquals(0, fixture.random.rolls, "a paid patch must not consume a roll at all")
    }

    @Test
    fun `clearing the companion flag does not cancel a payment`() {
        val fixture = fixture(alwaysDiseases = true)
        val state = plantedAt(1230)
        state.paidProtected = true
        state.protected = true

        state.protected = false
        fixture.growth.resolve(state, FarmingPatchType.ALLOTMENT, nowMinute = 1230 + CYCLE.toLong())

        assertFalse(state.diseased)
    }

    @Test
    fun `harvesting or clearing the patch ends the payment`() {
        val state = plantedAt(1230)
        state.paidProtected = true

        state.clearToEmpty()

        assertFalse(state.paidProtected, "OSRS protection lasts until harvested or cleared")
        assertFalse(state.protected, "and the companion flag ends on the same event")
    }

    private fun plantedAt(minute: Long): FarmingPatchState {
        val state = FarmingPatchState()
        state.cropId = POTATO
        state.rakeStage = FarmingConstants.RAKE_STAGES
        state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(minute, CYCLE)
        return state
    }

    private fun fixture(alwaysDiseases: Boolean): Fixture {
        val random = CountingRandom(if (alwaysDiseases) 0 else Int.MAX_VALUE)
        val crops = FarmingCropRegistry(FarmingPatchRegistry())
        crops.register(
            FarmingCrop(
                id = POTATO,
                patchType = FarmingPatchType.ALLOTMENT,
                growthStages = GROWN_STAGE + 1,
                osrsCycleMinutes = CYCLE * FarmingConstants.GROWTH_SPEED_MULTIPLIER,
                visuals =
                    FarmingCropVisuals(
                        growing = FarmingAnchor("loc.potato_seed"),
                        diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                        dead = FarmingAnchor("loc.potato_1_dead", stage = 1),
                    ),
                diseaseChance = FarmingConstants.DISEASE_DENOMINATOR,
                baseLives = 3,
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
        private const val POTATO = "potato"
        private const val CYCLE = 10
        private const val GROWN_STAGE = 4
    }
}
