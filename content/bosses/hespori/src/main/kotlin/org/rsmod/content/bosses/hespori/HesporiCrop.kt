package org.rsmod.content.bosses.hespori

import org.rsmod.api.farming.FarmingClock
import org.rsmod.api.farming.FarmingConstants
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object HesporiCrop {
    const val TRANSMIT = "varbit.farming_transmit_j"
    const val REQUIRED_LEVEL = 65
    const val HARVEST_XP = 12662.0
    val cycleMinutes = (640 / FarmingConstants.GROWTH_SPEED_MULTIPLIER).coerceAtLeast(1)

    fun stage(player: Player, now: Long = FarmingClock.currentMinute()): Int {
        if (player.hesporiDefeated) return 8
        val planted = player.hesporiPlantedMinute ?: return player.hesporiRakeProgress
        return 4 + growth(planted.toLong(), now)
    }

    fun growth(planted: Long, now: Long): Int {
        val boundary = FarmingClock.boundaryAtOrBefore(planted, cycleMinutes)
        return ((now - boundary).coerceAtLeast(0) / cycleMinutes).coerceAtMost(3).toInt()
    }

    fun plant(player: Player, now: Long = FarmingClock.currentMinute()): Boolean {
        if (stage(player, now) != 3) return false
        player.hesporiPlantedMinute = now.toInt()
        player.hesporiDefeated = false
        render(player, now)
        return true
    }

    fun defeat(player: Player): Boolean {
        if (stage(player) != 7) return false
        player.hesporiDefeated = true
        render(player)
        return true
    }

    /** Claim before item/XP delivery; no suspension is allowed inside the reward transaction. */
    fun claimHarvest(player: Player): Boolean {
        if (!player.hesporiDefeated) return false
        player.hesporiDefeated = false
        player.hesporiPlantedMinute = null
        player.hesporiRakeProgress = 3
        render(player)
        return true
    }

    fun render(player: Player, now: Long = FarmingClock.currentMinute()) {
        VarPlayerIntMapSetter.set(player, TRANSMIT, stage(player, now))
    }
}
