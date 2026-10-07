package org.rsmod.api.utils.skills

import kotlin.math.floor

public object SkillingSuccessRate {
    public fun lowRate(low: Int, level: Int, maxLevel: Int): Double {
        return (low * (maxLevel - level)) / (maxLevel - 1.0)
    }

    public fun highRate(high: Int, level: Int, maxLevel: Int): Double {
        return (high * (level - 1)) / (maxLevel - 1.0)
    }

    public fun successRate(low: Int, high: Int, level: Int, maxLevel: Int): Double =
        successRate(low, high, level, maxLevel, flatBonus = 0.0)

    /**
     * Add flatBonus before flooring interpolation. Hosidius adds 12.8/25.6 out of 256, not a
     * level shift; negative bonuses are valid.
     */
    public fun successRate(
        low: Int,
        high: Int,
        level: Int,
        maxLevel: Int,
        flatBonus: Double,
    ): Double {
        val lowRate = lowRate(low, level, maxLevel)
        val highRate = highRate(high, level, maxLevel)
        return (1.0 + floor(lowRate + highRate + flatBonus + 0.5)) / 256.0
    }
}
