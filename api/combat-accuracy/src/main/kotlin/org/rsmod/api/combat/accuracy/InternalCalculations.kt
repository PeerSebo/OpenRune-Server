package org.rsmod.api.combat.accuracy

internal const val PERCENT_SCALE: Int = 100

internal fun scaleByPercent(base: Int, multiplier: Int): Int = (base * multiplier) / PERCENT_SCALE

// Epsilon prevents floating-point error from flooring 1.15 * 100 to 114.
internal fun safePercentScale(multiplier: Double): Int =
    ((multiplier * PERCENT_SCALE) + 0.00001).toInt()
