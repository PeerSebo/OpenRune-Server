package org.rsmod.api.farming

public object FarmingConstants {
    public const val MILLIS_PER_MINUTE: Long = 60_000L

    /**
     * Scale crop/patch durations, never FarmingClock itself; otherwise resolver loops skip or
     * double-consume boundaries. This multiplier deliberately accelerates all Farming timers.
     */
    public const val GROWTH_SPEED_MULTIPLIER: Int = 5

    /**
     * UNVERIFIED: 35-minute rot cycles finishing on the second boundary model the wiki's 35-70
     * minute range; other wiki pages give conflicting durations.
     */
    public const val OSRS_BIN_ROT_CYCLE_MINUTES: Int = 35

    public val BIN_ROT_CYCLE_MINUTES: Int
        get() = maxOf(1, OSRS_BIN_ROT_CYCLE_MINUTES / GROWTH_SPEED_MULTIPLIER)

    public const val BIN_EMPTY_TRANSFORM_INDEX: Int = 0

    /**
     * Transform 0 is fully overgrown and also the table filler; never use it as a generic
     * missing-state fallback.
     */
    public const val WEEDS_TRANSFORM_INDEX: Int = 0

    public const val EMPTY_TRANSFORM_INDEX: Int = 3

    public const val RAKE_STAGES: Int = EMPTY_TRANSFORM_INDEX

    /** Entry 256 is the multiloc fallback; only indices 0-255 are reachable through the varbit. */
    public const val MAX_TRANSFORM_INDEX: Int = 255

    /** Disease rates are published as `x/128` per growth cycle. */
    public const val DISEASE_DENOMINATOR: Int = 128

    public const val MIN_DISEASE_CHANCE: Int = 1

    /** Watering is modelled as halving disease chance; its actual reduction is unpublished. */
    public const val WATERED_DISEASE_DIVISOR: Int = 2

    /**
     * Bound lazy catch-up work for long absences or corrupt dates; normal terminal states stop
     * advancing earlier.
     */
    public const val MAX_CYCLES_PER_RESOLVE: Int = 8192

    public const val REFRESH_INTERVAL_TICKS: Int = 10
}
