package org.rsmod.content.minigames.tempoross

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.tempoross.configs.temporossBestPoints
import org.rsmod.content.minigames.tempoross.configs.temporossBestTicks
import org.rsmod.game.entity.Player

object TemporossStats {
    const val KILLS = "varp.total_tempoross_kills"

    fun recordWin(player: Player, points: Int, durationTicks: Int) {
        if (points < 2000) return
        val count = (player.vars[KILLS].toLong() + 1).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        VarPlayerIntMapSetter.set(player, KILLS, count)
        player.temporossBestPoints = maxOf(player.temporossBestPoints, points)
        if (durationTicks > 0) {
            player.temporossBestTicks = minOf(player.temporossBestTicks.takeIf { it > 0 } ?: Int.MAX_VALUE, durationTicks)
        }
        player.mes("You have subdued Tempoross <col=ff0000>$count</col> ${if (count == 1) "time" else "times"}.")
    }

    fun time(ticks: Int?): String {
        if (ticks == null || ticks <= 0) return "-"
        val seconds = ticks * 3L / 5
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}
