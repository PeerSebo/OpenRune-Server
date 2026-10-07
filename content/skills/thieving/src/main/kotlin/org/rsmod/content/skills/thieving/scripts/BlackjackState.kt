package org.rsmod.content.skills.thieving.scripts

import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal object BlackjackState {
    private var Player.remaining by intVarp("varp.blackjack_pickpockets_remaining")
    private var Player.targetUid by intVarp("varp.blackjack_target_uid")
    private var Player.expiresAt by intVarp("varp.blackjack_expires_at")

    fun grant(player: Player, npc: Npc) {
        player.remaining = ThievingRates.BLACKJACK_FREE_PICKPOCKETS
        player.targetUid = npc.uid.packed
        player.expiresAt = player.currentMapClock + ThievingRates.BLACKJACK_KO_CYCLES
    }

    fun consumeGuaranteedPickpocket(player: Player, npc: Npc): Boolean {
        val left = player.remaining
        val expiry = player.expiresAt

        if (expiry <= player.currentMapClock) {
            clear(player)
            return false
        }

        if (left <= 0 || player.targetUid != npc.uid.packed) {
            return false
        }

        if (left == 1) {
            clear(player)
        } else {
            player.remaining = left - 1
        }
        return true
    }

    fun clear(player: Player) {
        player.remaining = 0
        player.targetUid = 0
        player.expiresAt = 0
    }
}
