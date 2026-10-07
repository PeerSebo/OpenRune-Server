package org.rsmod.content.minigames.mahoganyhomes.rewards

import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesManager
import org.rsmod.game.entity.Player

public object CarpenterPoints {
    public const val CURRENCY: String = "currency.carpenter_points"

    public fun getPoints(player: Player): Int = MahoganyHomesManager.getPoints(player)

    public fun addPoints(player: Player, amount: Int) {
        MahoganyHomesManager.addPoints(player, amount)
    }

    public fun spendPoints(player: Player, amount: Int): Boolean = MahoganyHomesManager.spendPoints(player, amount)
}
