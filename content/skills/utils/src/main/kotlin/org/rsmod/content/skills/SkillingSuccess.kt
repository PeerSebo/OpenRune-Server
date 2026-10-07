package org.rsmod.content.skills

import kotlin.random.Random
import org.rsmod.api.utils.skills.SkillingSuccessRate

private val STATIC_RANDOM = Random.Default

fun skillSuccess(low: Int, high: Int, level: Int): Boolean {
    val rate = computeSkillingSuccess(low, high, level)
    return rate > STATIC_RANDOM.nextDouble()
}

fun computeSkillingSuccess(low: Int, high: Int, level: Int): Double {
    val lvl = level.coerceIn(1, 99)
    return SkillingSuccessRate.successRate(low, high, lvl, maxLevel = 99).coerceIn(0.0, 1.0)
}
