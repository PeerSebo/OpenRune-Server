package org.rsmod.content.minigames.gotr

internal object GotrDefenceRules {
    const val FIRST_ATTACK_TICK = 201
    const val WAVE_TICKS = 76
    const val REPAIR_FRAGMENTS = 12
    const val REPAIR_ENERGY = 25
    const val GUARDIAN_LIMIT = 10

    val cells = listOf("obj.gotr_cell_tier1", "obj.gotr_cell_tier2", "obj.gotr_cell_tier3", "obj.gotr_cell_tier4")
    val barrierHitpoints = listOf(50, 100, 200, 400)
    val guardianHitpoints = listOf(10, 20, 30, 50)
    val healing = listOf(10, 25, 50, 100)
    val rechargeEnergy = listOf(2, 5, 9, 15)
    val placingEnergy = listOf(17, 20, 24, 30)
    val strengthenEnergy = listOf(0, 7, 13, 22)
    val guardianEnergy = listOf(6, 15, 27, 45)
    val barrierXp = listOf(30.0, 100.0, 180.0, 250.0)

    data class CellUse(val tier: Int, val hitpoints: Int, val energy: Int, val verb: String, val wasFull: Boolean = false)

    fun useCell(barrierTier: Int, hitpoints: Int, cellTier: Int): CellUse {
        require(barrierTier in 0..4 && cellTier in 1..4)
        return when {
            barrierTier == 0 -> CellUse(1, barrierHitpoints[0] / 2, placingEnergy[cellTier - 1], "creates")
            cellTier > barrierTier -> {
                val tier = barrierTier + 1
                CellUse(tier, barrierHitpoints[tier - 1] / 2, strengthenEnergy[cellTier - 1], "strengthens")
            }
            else -> CellUse(barrierTier, (hitpoints + healing[cellTier - 1]).coerceAtMost(barrierHitpoints[barrierTier - 1]), rechargeEnergy[cellTier - 1], "recharges", hitpoints >= barrierHitpoints[barrierTier - 1])
        }
    }

    fun nextWaveTick(elapsed: Int): Int = FIRST_ATTACK_TICK + ((elapsed - FIRST_ATTACK_TICK).coerceAtLeast(0) / WAVE_TICKS + 1) * WAVE_TICKS

    fun rectangleDistance(ax: Int, az: Int, aSize: Int, bx: Int, bz: Int, bSize: Int): Int {
        val dx = maxOf(bx - (ax + aSize - 1), ax - (bx + bSize - 1), 0)
        val dz = maxOf(bz - (az + aSize - 1), az - (bz + bSize - 1), 0)
        return maxOf(dx, dz)
    }

    // These caps apply only when recharging an already-full barrier.
    class FullBarrierBudget {
        private var energy = 0
        private var xp = 0.0

        fun claim(energyEach: Int, requestedXp: Double): Pair<Int, Double> {
            val awardedEnergy = minOf(energyEach, (450 - energy).coerceAtLeast(0) / 2)
            val awardedXp = minOf(requestedXp, (2500.0 - xp).coerceAtLeast(0.0))
            energy += awardedEnergy * 2
            xp += awardedXp
            return awardedEnergy to awardedXp
        }
    }
}
