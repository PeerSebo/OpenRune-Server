package org.rsmod.api.player.hit

import kotlin.math.min
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public object PlayerAbsorption {
    private var Player.absorptionPoints by intVarp("varp.player_absorption_points")

    public fun points(player: Player): Int = player.absorptionPoints

    public fun add(player: Player, amount: Int, maximum: Int): Int {
        require(amount >= 0) { "Absorption amount must not be negative." }
        require(maximum >= 0) { "Absorption maximum must not be negative." }

        val updated = (points(player).toLong() + amount).coerceAtMost(maximum.toLong()).toInt()

        if (updated <= 0) {
            clear(player)
        } else {
            player.absorptionPoints = updated
        }

        return updated
    }

    public fun absorb(player: Player, incomingDamage: Int): Int {
        if (incomingDamage <= 0) {
            return 0
        }

        val applicableDamage = min(player.hitpoints, incomingDamage)

        if (applicableDamage <= 0) {
            return 0
        }

        val current = points(player)
        if (current <= 0) {
            return applicableDamage
        }

        val absorbed = min(current, applicableDamage)

        val remainingPoints = current - absorbed

        if (remainingPoints <= 0) {
            clear(player)
        } else {
            player.absorptionPoints = remainingPoints
        }

        return applicableDamage - absorbed
    }

    public fun clear(player: Player) {
        player.absorptionPoints = 0
    }
}
