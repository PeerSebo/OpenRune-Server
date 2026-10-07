package org.rsmod.api.farming

public enum class FarmingBinPhase {
    FILLING,

    ROTTING,

    READY,

    OPEN;

    public companion object {
        public fun find(name: String?): FarmingBinPhase? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * The whole load determines the product: every ingredient must qualify for supercompost; mixed
 * tomatoes produce ordinary compost.
 */
public enum class FarmingBinFill(internal val locBand: String?) {
    NONE(locBand = null),
    COMPOSTABLE(locBand = "compostable"),
    SUPERCOMPOSTABLE(locBand = "supercompostable"),
    TOMATOES(locBand = "tomatoes");

    public val product: FarmingBinProduct?
        get() =
            when (this) {
                NONE -> null
                COMPOSTABLE -> FarmingBinProduct.COMPOST
                SUPERCOMPOSTABLE -> FarmingBinProduct.SUPERCOMPOST
                TOMATOES -> FarmingBinProduct.ROTTEN_TOMATOES
            }

    /** Only a pure load retains its class; mixing ingredient classes produces ordinary compost. */
    public fun merge(added: FarmingBinFill): FarmingBinFill =
        when {
            added == NONE -> this
            this == NONE -> added
            this == added -> this
            else -> COMPOSTABLE
        }

    public companion object {
        public fun find(name: String?): FarmingBinFill? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Ultracompost has no rotting/ready states; it is an upgrade of opened supercompost. Rotten
 * tomatoes have no compost tier.
 */
public enum class FarmingBinProduct(
    internal val locBand: String,
    internal val hasRotStates: Boolean = true,
) {
    COMPOST(locBand = "compost"),
    SUPERCOMPOST(locBand = "supercompost"),
    ULTRACOMPOST(locBand = "ultracompost", hasRotStates = false),
    ROTTEN_TOMATOES(locBand = "rottentomatoes");

    public val compostTier: FarmingCompost?
        get() =
            when (this) {
                COMPOST -> FarmingCompost.COMPOST
                SUPERCOMPOST -> FarmingCompost.SUPERCOMPOST
                ULTRACOMPOST -> FarmingCompost.ULTRACOMPOST
                ROTTEN_TOMATOES -> null
            }

    public val upgradesTo: FarmingBinProduct?
        get() =
            when (this) {
                COMPOST -> SUPERCOMPOST
                SUPERCOMPOST -> ULTRACOMPOST
                ULTRACOMPOST -> null
                ROTTEN_TOMATOES -> null
            }

    public companion object {
        public fun find(name: String?): FarmingBinProduct? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Bin state is per character; shared transmit varbits only select the visible state for the
 * current scene.
 */
public class FarmingBinState {
    public var phase: FarmingBinPhase = FarmingBinPhase.FILLING

    public var fill: FarmingBinFill = FarmingBinFill.NONE

    public var product: FarmingBinProduct? = null

    public var count: Int = 0

    /**
     * Persist the closing epoch minute, not a cycle-relative deadline, so offline rot time
     * survives restarts.
     */
    public var closedMinute: Long = 0

    public fun isPristine(): Boolean =
        phase == FarmingBinPhase.FILLING &&
            fill == FarmingBinFill.NONE &&
            product == null &&
            count == 0 &&
            closedMinute == 0L

    public fun clearToEmpty() {
        phase = FarmingBinPhase.FILLING
        fill = FarmingBinFill.NONE
        product = null
        count = 0
        closedMinute = 0
    }

    /**
     * Ready at boundaryAtOrBefore(closedMinute) + 2C: the second rot boundary after closing.
     * Zero means never closed.
     */
    public fun readyMinute(cycleMinutes: Int = FarmingConstants.BIN_ROT_CYCLE_MINUTES): Long {
        if (closedMinute <= 0) {
            return 0
        }
        return FarmingClock.boundaryAtOrBefore(closedMinute, cycleMinutes) + 2L * cycleMinutes
    }

    override fun toString(): String =
        "FarmingBinState(phase=$phase, fill=$fill, product=$product, count=$count, " +
            "closedMinute=$closedMinute)"
}
