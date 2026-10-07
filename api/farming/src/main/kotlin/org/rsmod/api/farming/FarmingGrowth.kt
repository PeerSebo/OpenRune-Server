package org.rsmod.api.farming

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom

/**
 * Resolve each elapsed boundary once, in order. Disease blocks growth and watering expires
 * after its covered cycle; produce regrowth uses a separate clock.
 */
@Singleton
public class FarmingGrowth
@Inject
constructor(private val random: GameRandom, private val crops: FarmingCropRegistry) {
    public fun resolve(
        state: FarmingPatchState,
        patchType: FarmingPatchType? = null,
        nowMinute: Long = FarmingClock.currentMinute(),
    ): Boolean {
        val crop = crops.find(state.cropId)
        if (crop == null) {
            return resolveWeeds(state, patchType, nowMinute)
        }
        var changed = resolveGrowth(state, crop, nowMinute)
        changed = resolveProduce(state, crop, nowMinute) || changed
        return changed
    }

    /** Empty patches regrow weeds on global boundaries; raking must not restart that clock. */
    private fun resolveWeeds(
        state: FarmingPatchState,
        patchType: FarmingPatchType?,
        nowMinute: Long,
    ): Boolean {
        // Unregistered crops retain their state and clock until their definition returns.
        if (state.cropId != null || state.stump) {
            return false
        }
        val cycle = patchType?.weedCycleMinutes ?: return false

        if (state.rakeStage <= 0) {
            return parkAnchor(state, nowMinute, cycle)
        }

        if (state.lastGrowthMinute <= 0 || state.lastGrowthMinute > nowMinute) {
            state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }

        var boundary = FarmingClock.nextBoundaryAfter(state.lastGrowthMinute, cycle)
        var changed = false
        var rolled = 0
        while (boundary <= nowMinute && state.rakeStage > 0) {
            if (rolled >= FarmingConstants.MAX_CYCLES_PER_RESOLVE) {
                state.rakeStage = 0
                break
            }
            state.rakeStage--
            state.lastGrowthMinute = boundary
            changed = true
            rolled++
            boundary += cycle
        }
        if (state.rakeStage <= 0) {
            return parkAnchor(state, nowMinute, cycle) || changed
        }
        return changed
    }

    private fun parkAnchor(state: FarmingPatchState, nowMinute: Long, cycle: Int): Boolean {
        val parked = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
        if (state.lastGrowthMinute == parked) {
            return false
        }
        state.lastGrowthMinute = parked
        return true
    }

    private fun resolveGrowth(
        state: FarmingPatchState,
        crop: FarmingCrop,
        nowMinute: Long,
    ): Boolean {
        val cycle = crop.cycleMinutes

        // Missing anchors begin at the current boundary without granting elapsed growth.
        if (state.lastGrowthMinute <= 0) {
            state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }

        // Clock rollback must re-anchor without granting extra growth cycles.
        if (state.lastGrowthMinute > nowMinute) {
            state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }

        var boundary = FarmingClock.nextBoundaryAfter(state.lastGrowthMinute, cycle)
        var changed = false
        var rolled = 0
        while (boundary <= nowMinute) {
            if (rolled >= FarmingConstants.MAX_CYCLES_PER_RESOLVE) {
                state.lastGrowthMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
                return true
            }
            applyCycle(state, crop)
            // Record the consumed boundary so the partially elapsed cycle survives.
            state.lastGrowthMinute = boundary
            changed = true
            rolled++
            boundary += cycle
        }
        return changed
    }

    /** Resolve existing disease before growth; an uncured disease cycle can kill the plant. */
    private fun applyCycle(state: FarmingPatchState, crop: FarmingCrop) {
        if (state.dead || state.stump) {
            return
        }

        if (state.diseased) {
            // Clear uncured disease even without a dead anchor so invalid saves cannot freeze growth.
            state.diseased = false
            if (crop.canDie) {
                state.dead = true
            }
            return
        }

        val grown = crop.grownStage
        if (state.stage >= grown) {
            return
        }

        // Watering covers exactly the cycle that is ending, then lapses.
        val wasWatered = state.watered
        state.watered = false

        state.stage++

        // Only intermediate growth stages have disease states in the cache.
        if (state.stage in 1 until grown) {
            rollDisease(state, crop, wasWatered)
        }
    }

    private fun rollDisease(state: FarmingPatchState, crop: FarmingCrop, wasWatered: Boolean) {
        // Companion changes must never cancel paid protection.
        if (state.protected || state.paidProtected || !crop.canBeDiseased) {
            return
        }
        val chance = diseaseChance(state, crop, wasWatered)
        if (chance <= 0) {
            return
        }
        if (random.of(FarmingConstants.DISEASE_DENOMINATOR) < chance) {
            state.diseased = true
        }
    }

    /** Compost uses ceiling division and a minimum of one; watering reduction remains unverified. */
    private fun diseaseChance(
        state: FarmingPatchState,
        crop: FarmingCrop,
        wasWatered: Boolean,
    ): Int {
        var chance = crop.diseaseChanceAt(state.stage)
        if (chance <= 0) {
            return 0
        }
        chance = ceilDiv(chance, state.compost.diseaseDivisor)
        if (wasWatered) {
            chance = ceilDiv(chance, FarmingConstants.WATERED_DISEASE_DIVISOR)
        }
        return chance.coerceAtLeast(FarmingConstants.MIN_DISEASE_CHANCE)
    }

    /** Produce uses an independent boundary clock; picking fruit must not discard partial regrowth. */
    private fun resolveProduce(
        state: FarmingPatchState,
        crop: FarmingCrop,
        nowMinute: Long,
    ): Boolean {
        if (crop.produceCapacity <= 0) {
            return false
        }
        val checked = !crop.checkHealth || state.checkHealthClaimed
        val bearing =
            !state.dead &&
                !state.diseased &&
                !state.stump &&
                checked &&
                state.stage >= crop.grownStage
        val cycle = crop.produceRegrowMinutes

        if (!bearing || state.produceCount >= crop.produceCapacity) {
            val parked = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            if (state.lastProduceMinute == parked) {
                return false
            }
            state.lastProduceMinute = parked
            return true
        }

        if (state.lastProduceMinute <= 0 || state.lastProduceMinute > nowMinute) {
            state.lastProduceMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }

        var boundary = FarmingClock.nextBoundaryAfter(state.lastProduceMinute, cycle)
        var changed = false
        var rolled = 0
        while (boundary <= nowMinute && state.produceCount < crop.produceCapacity) {
            if (rolled >= FarmingConstants.MAX_CYCLES_PER_RESOLVE) {
                break
            }
            state.produceCount++
            state.lastProduceMinute = boundary
            changed = true
            rolled++
            boundary += cycle
        }

        if (state.produceCount >= crop.produceCapacity) {
            val parked = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            if (state.lastProduceMinute != parked) {
                state.lastProduceMinute = parked
                changed = true
            }
        }
        return changed
    }

    private companion object {
        private fun ceilDiv(value: Int, divisor: Int): Int =
            if (divisor <= 1) value else (value + divisor - 1) / divisor
    }
}
