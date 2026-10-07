package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal object AgilityPyramidProgress {
    private var Player.topClaim by intVarp("varp.agility_pyramid_repeat")

    internal const val TOP_CLAIMED_SENTINEL = 1 shl 30

    fun isTopClaimed(player: Player): Boolean = isTopClaimed(player.topClaim)

    internal fun isTopClaimed(packed: Int): Boolean = packed == TOP_CLAIMED_SENTINEL

    fun markTopClaimed(player: Player) {
        player.topClaim = TOP_CLAIMED_SENTINEL
    }

    fun resetTop(player: Player) {
        player.topClaim = 0
    }
}
