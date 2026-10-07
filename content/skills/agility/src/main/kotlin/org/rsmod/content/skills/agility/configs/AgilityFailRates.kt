package org.rsmod.content.skills.agility.configs

// stat_random rounds the summed interpolation once, half up; separately flooring its terms changes success rates.
// See https://oldschool.runescape.wiki/w/Skilling_success_rate.
object AgilityFailRates {
    const val ROLL_RANGE = 256

    fun successValue(level: Int, low: Int, high: Int): Int {
        val clamped = minOf(level, 99)
        return (2 * low * (99 - clamped) + 2 * high * (clamped - 1) + 98) / 196 + 1
    }
}
