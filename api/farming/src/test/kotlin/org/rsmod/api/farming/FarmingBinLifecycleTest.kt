package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FarmingBinLifecycleTest : FarmingCacheTest() {

    @Test
    fun `a pure supercompostable load stays supercompostable and rots into supercompost`() {
        val manager = manager()
        val state = FarmingBinState()
        repeat(SMALL) { manager.applyAdd(state, SMALL, FarmingBinFill.SUPERCOMPOSTABLE) }

        assertEquals(FarmingBinFill.SUPERCOMPOSTABLE, state.fill)
        assertTrue(manager.applyClose(state, SMALL, nowMinute = 100))
        assertEquals(FarmingBinProduct.SUPERCOMPOST, state.product)
    }

    @Test
    fun `one ordinary vegetable drops the whole load to plain compost`() {
        val manager = manager()
        val state = FarmingBinState()
        repeat(SMALL - 1) { manager.applyAdd(state, SMALL, FarmingBinFill.SUPERCOMPOSTABLE) }
        manager.applyAdd(state, SMALL, FarmingBinFill.COMPOSTABLE)

        assertEquals(FarmingBinFill.COMPOSTABLE, state.fill)
        assertTrue(manager.applyClose(state, SMALL, nowMinute = 100))
        assertEquals(FarmingBinProduct.COMPOST, state.product)
    }

    @Test
    fun `tomatoes and supercompostables cannot combine into either pure load`() {
        val manager = manager()
        val state = FarmingBinState()
        manager.applyAdd(state, SMALL, FarmingBinFill.TOMATOES)
        manager.applyAdd(state, SMALL, FarmingBinFill.SUPERCOMPOSTABLE)

        assertEquals(FarmingBinFill.COMPOSTABLE, state.fill)
    }

    @Test
    fun `filling the last slot is reported separately, because that is when Close appears`() {
        val manager = manager()
        val state = FarmingBinState()
        repeat(SMALL - 1) {
            assertEquals(
                FarmingBinAddResult.ADDED,
                manager.applyAdd(state, SMALL, FarmingBinFill.TOMATOES),
            )
        }
        assertEquals(
            FarmingBinAddResult.ADDED_FULL,
            manager.applyAdd(state, SMALL, FarmingBinFill.TOMATOES),
        )
        assertEquals(
            FarmingBinAddResult.FULL,
            manager.applyAdd(state, SMALL, FarmingBinFill.TOMATOES),
        )
    }

    @Test
    fun `a bin holding product refuses new items until it is emptied`() {
        val manager = manager()
        val state = full(manager, FarmingBinFill.COMPOSTABLE)
        manager.applyClose(state, SMALL, nowMinute = BOUNDARY)

        assertEquals(
            FarmingBinAddResult.NOT_ACCEPTING,
            manager.applyAdd(state, SMALL, FarmingBinFill.COMPOSTABLE),
        )
    }

    @Test
    fun `a partly filled bin cannot be closed`() {
        // Cache-derived, not a wiki reading: op1 `Close` exists only on `compostable_15`.
        val manager = manager()
        val state = FarmingBinState()
        repeat(SMALL - 1) { manager.applyAdd(state, SMALL, FarmingBinFill.COMPOSTABLE) }

        assertFalse(manager.applyClose(state, SMALL, nowMinute = 100))
        assertEquals(FarmingBinPhase.FILLING, state.phase)
    }

    @Test
    fun `the big bin takes thirty items before it will close`() {
        val manager = manager()
        val state = FarmingBinState()
        repeat(SMALL) { manager.applyAdd(state, BIG, FarmingBinFill.COMPOSTABLE) }
        assertFalse(manager.applyClose(state, BIG, nowMinute = 100), "15 is not full for a big bin")

        repeat(BIG - SMALL) { manager.applyAdd(state, BIG, FarmingBinFill.COMPOSTABLE) }
        assertTrue(manager.applyClose(state, BIG, nowMinute = 100))
        assertEquals(BIG, state.count)
    }

    @Test
    fun `a bin closed on a boundary waits two full cycles`() {
        val manager = manager()
        val state = closed(manager, closedAt = BOUNDARY)

        assertFalse(manager.resolve(state, nowMinute = BOUNDARY + 2L * CYCLE - 1))
        assertEquals(FarmingBinPhase.ROTTING, state.phase)
        assertTrue(manager.resolve(state, nowMinute = BOUNDARY + 2L * CYCLE))
        assertEquals(FarmingBinPhase.READY, state.phase)
    }

    @Test
    fun `a bin closed just before a boundary waits barely more than one cycle`() {
        val manager = manager()
        val state = closed(manager, closedAt = BOUNDARY - 1)

        assertFalse(manager.resolve(state, nowMinute = BOUNDARY + CYCLE - 1))
        assertTrue(manager.resolve(state, nowMinute = BOUNDARY + CYCLE))
        assertEquals(FarmingBinPhase.READY, state.phase)
    }

    @Test
    fun `resolving is idempotent and never rewinds a ready bin`() {
        val manager = manager()
        val state = closed(manager, closedAt = BOUNDARY)

        assertTrue(manager.resolve(state, nowMinute = BOUNDARY + 3L * CYCLE))
        assertFalse(
            manager.resolve(state, nowMinute = BOUNDARY + 3L * CYCLE),
            "second resolve changes nothing",
        )
        assertFalse(manager.resolve(state, nowMinute = BOUNDARY + 4L * CYCLE))
        assertEquals(FarmingBinPhase.READY, state.phase)
    }

    @Test
    fun `a clock that moved backwards re-anchors instead of handing out a free rot`() {
        val manager = manager()
        val state = closed(manager, closedAt = BOUNDARY)
        val rewound = BOUNDARY - 10L * CYCLE

        assertTrue(manager.resolve(state, nowMinute = rewound))
        assertEquals(FarmingBinPhase.ROTTING, state.phase)
        assertTrue(state.closedMinute <= rewound)
    }

    @Test
    fun `a closed bin with no anchor starts its wait now rather than being instantly ready`() {
        val manager = manager()
        val state = FarmingBinState()
        state.phase = FarmingBinPhase.ROTTING
        state.product = FarmingBinProduct.COMPOST
        state.count = SMALL
        state.closedMinute = 0

        assertTrue(manager.resolve(state, nowMinute = BOUNDARY))
        assertEquals(FarmingBinPhase.ROTTING, state.phase)
        assertFalse(manager.resolve(state, nowMinute = BOUNDARY))
    }

    @Test
    fun `the scaled cycle divides exactly`() {
        assertEquals(
            FarmingConstants.OSRS_BIN_ROT_CYCLE_MINUTES,
            FarmingConstants.BIN_ROT_CYCLE_MINUTES * FarmingConstants.GROWTH_SPEED_MULTIPLIER,
        )
    }

    @Test
    fun `a closed bin becomes ready without ever being observed in between`() {
        val manager = manager()
        val state = closed(manager, closedAt = BOUNDARY)

        assertEquals(FarmingBinPhase.ROTTING, state.phase)
        assertTrue(manager.resolve(state, nowMinute = BOUNDARY + 60))
        assertEquals(FarmingBinPhase.READY, state.phase)
    }

    @Test
    fun `the last bucket reports the product it handed out, after the wipe`() {
        val manager = manager()
        val state = opened(manager, FarmingBinProduct.COMPOST, count = 2)

        val first = manager.applyTake(state)
        assertEquals(FarmingBinTakeResult.TAKEN, first.result)
        assertEquals(FarmingBinProduct.COMPOST, first.product)

        val last = manager.applyTake(state)
        assertEquals(FarmingBinTakeResult.TAKEN_LAST, last.result)
        assertEquals(FarmingBinProduct.COMPOST, last.product)
        assertNull(state.product)
        assertTrue(state.isPristine())
    }

    @Test
    fun `an emptied bin accepts items again`() {
        val manager = manager()
        val state = opened(manager, FarmingBinProduct.COMPOST, count = 1)
        manager.applyTake(state)

        assertEquals(
            FarmingBinAddResult.ADDED,
            manager.applyAdd(state, SMALL, FarmingBinFill.COMPOSTABLE),
        )
    }

    @Test
    fun `taking from a bin that is not open yields nothing`() {
        val manager = manager()
        val state = closed(manager, closedAt = 0)

        val take = manager.applyTake(state)
        assertEquals(FarmingBinTakeResult.NOTHING_TO_TAKE, take.result)
        assertFalse(take.taken)
        assertNull(take.product)
    }

    @Test
    fun `upgrades move exactly one tier and only in an opened bin`() {
        val manager = manager()
        val state = opened(manager, FarmingBinProduct.COMPOST, count = SMALL)

        assertFalse(manager.applyUpgrade(state, FarmingBinProduct.ULTRACOMPOST))
        assertTrue(manager.applyUpgrade(state, FarmingBinProduct.SUPERCOMPOST))
        assertTrue(manager.applyUpgrade(state, FarmingBinProduct.ULTRACOMPOST))
        assertEquals(FarmingBinProduct.ULTRACOMPOST, state.product)
        assertFalse(manager.applyUpgrade(state, FarmingBinProduct.ULTRACOMPOST))
    }

    @Test
    fun `a partly emptied bin is still upgradeable`() {
        val manager = manager()
        val state = opened(manager, FarmingBinProduct.SUPERCOMPOST, count = 1)

        assertTrue(manager.applyUpgrade(state, FarmingBinProduct.ULTRACOMPOST))
    }

    @Test
    fun `a closed bin cannot be upgraded`() {
        val manager = manager()
        val state = closed(manager, closedAt = 0)

        assertFalse(manager.applyUpgrade(state, FarmingBinProduct.SUPERCOMPOST))
    }

    @Test
    fun `rotten tomatoes upgrade into nothing`() {
        val manager = manager()
        val state = opened(manager, FarmingBinProduct.ROTTEN_TOMATOES, count = SMALL)

        assertFalse(manager.applyUpgrade(state, FarmingBinProduct.COMPOST))
        assertFalse(manager.applyUpgrade(state, FarmingBinProduct.SUPERCOMPOST))
    }

    private fun full(manager: FarmingBinManager, fill: FarmingBinFill): FarmingBinState {
        val state = FarmingBinState()
        repeat(SMALL) { manager.applyAdd(state, SMALL, fill) }
        return state
    }

    private fun closed(manager: FarmingBinManager, closedAt: Long): FarmingBinState {
        val state = full(manager, FarmingBinFill.COMPOSTABLE)
        manager.applyClose(state, SMALL, nowMinute = closedAt)
        return state
    }

    private fun opened(
        manager: FarmingBinManager,
        product: FarmingBinProduct,
        count: Int,
    ): FarmingBinState {
        val state = closed(manager, closedAt = BOUNDARY)
        state.phase = FarmingBinPhase.OPEN
        state.product = product
        state.count = count
        return state
    }

    private fun manager(): FarmingBinManager =
        FarmingBinManager(
            bins = FarmingBinRegistry(),
            renderer = FarmingBinRenderer(FarmingTransmitVars()),
        )

    private companion object {
        private const val SMALL: Int = 15
        private const val BIG: Int = 30

        private val CYCLE: Int = FarmingConstants.BIN_ROT_CYCLE_MINUTES

        /** Use a nonzero aligned epoch minute: zero is the missing-anchor sentinel. */
        private val BOUNDARY: Long = FarmingClock.boundaryAtOrBefore(29_000_000L, CYCLE)
    }
}
