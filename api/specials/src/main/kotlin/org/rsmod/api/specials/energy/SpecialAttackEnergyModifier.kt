package org.rsmod.api.specials.energy

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public object SpecialAttackEnergyModifier {
    private var Player.storedCostDivisor by intVarp("varp.player_special_attack_cost_divisor")

    public fun setCostDivisor(player: Player, divisor: Int) {
        require(divisor >= 1) { "Special attack cost divisor must be at least 1." }
        player.storedCostDivisor = divisor
    }

    public fun adjustedCost(player: Player, baseCost: Int): Int {
        if (baseCost < SPECIALIZED_REQUIREMENT_THRESHOLD) return baseCost
        val divisor = player.storedCostDivisor.coerceAtLeast(1)
        return ((baseCost.toLong() + divisor - 1) / divisor).toInt()
    }

    public fun isActive(player: Player): Boolean = player.storedCostDivisor > 1

    public fun clear(player: Player) {
        player.storedCostDivisor = 0
    }

    private const val SPECIALIZED_REQUIREMENT_THRESHOLD = 10
}
