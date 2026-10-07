package org.rsmod.content.bosses.gemstonecrab

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal object GemstoneCrabNotifications {
    private const val NOTIFICATION_DISTANCE = 15

    fun announceBurrow(
        crab: Npc,
        players: Iterable<Player>,
        topNames: List<String>,
        eligibleMiners: Set<Long>,
    ) {
        for (player in players) {
            if (!crab.isWithinDistance(player, NOTIFICATION_DISTANCE)) continue
            player.mes(
                "The gemstone crab burrows away, leaving a piece of its shell behind.",
                ChatType.Broadcast,
            )
            if (topNames.isNotEmpty()) {
                player.mes("The top three crab crushers were ${joinNames(topNames)}!", ChatType.Broadcast)
            }
            if (player.uuid in eligibleMiners) {
                player.mes("<col=005f00>You gained enough understanding of the crab to mine from its remains.")
            }
        }
    }

    private fun joinNames(names: List<String>): String =
        when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            else -> "${names.dropLast(1).joinToString(", ")}, & ${names.last()}"
        }
}
