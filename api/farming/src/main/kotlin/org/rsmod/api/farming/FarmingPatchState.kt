package org.rsmod.api.farming

/**
 * Persist patch state per character; rendering through shared varbits does not define
 * ownership.
 */
public class FarmingPatchState {
    public var cropId: String? = null

    public var stage: Int = 0

    public var rakeStage: Int = 0

    /** Watered for the *current* cycle only; cleared when that cycle is consumed. */
    public var watered: Boolean = false

    public var diseased: Boolean = false

    public var dead: Boolean = false

    public var stump: Boolean = false

    public var lives: Int = 0

    public var compost: FarmingCompost = FarmingCompost.NONE

    /**
     * Companion/scarecrow immunity is recomputed before growth; it must not overwrite paid
     * protection.
     */
    public var protected: Boolean = false

    /**
     * Paid protection survives companion changes and ends when the patch is harvested or
     * cleared.
     */
    public var paidProtected: Boolean = false

    /** Epoch minute of the last consumed growth boundary, preserving partial elapsed cycles. */
    public var lastGrowthMinute: Long = 0

    /** Same discipline as [lastGrowthMinute], for the independent produce-regrowth clock. */
    public var lastProduceMinute: Long = 0

    public var checkHealthClaimed: Boolean = false

    /**
     * Pay planting XP once, on full harvest, check-health, or clearing a crop that died before
     * maturity.
     */
    public var plantXpPaid: Boolean = false

    /**
     * Deferred planting XP can outlive the crop state after terminal harvest; clear it when
     * claimed or replanted.
     */
    public var plantXpOwedCrop: String? = null

    public var produceCount: Int = 0

    public fun isPristine(): Boolean =
        cropId == null &&
            stage == 0 &&
            rakeStage == 0 &&
            !watered &&
            !diseased &&
            !dead &&
            !stump &&
            lives == 0 &&
            compost == FarmingCompost.NONE &&
            !protected &&
            !paidProtected &&
            lastGrowthMinute == 0L &&
            lastProduceMinute == 0L &&
            !checkHealthClaimed &&
            !plantXpPaid &&
            plantXpOwedCrop == null &&
            produceCount == 0

    /** Keep unpaid planting XP after terminal harvest until the payout claims it. */
    public fun clearToEmpty() {
        cropId = null
        stage = 0
        rakeStage = FarmingConstants.RAKE_STAGES
        watered = false
        diseased = false
        dead = false
        stump = false
        lives = 0
        compost = FarmingCompost.NONE
        protected = false

        paidProtected = false
        lastGrowthMinute = 0
        lastProduceMinute = 0
        checkHealthClaimed = false
        plantXpPaid = false
        produceCount = 0
    }

    override fun toString(): String =
        "FarmingPatchState(crop=$cropId, stage=$stage, rake=$rakeStage, watered=$watered, " +
            "diseased=$diseased, dead=$dead, stump=$stump, lives=$lives, compost=$compost, " +
            "protected=$protected, paidProtected=$paidProtected, " +
            "lastGrowthMinute=$lastGrowthMinute, " +
            "lastProduceMinute=$lastProduceMinute, checkHealthClaimed=$checkHealthClaimed, " +
            "plantXpPaid=$plantXpPaid, plantXpOwedCrop=$plantXpOwedCrop, produce=$produceCount)"
}
