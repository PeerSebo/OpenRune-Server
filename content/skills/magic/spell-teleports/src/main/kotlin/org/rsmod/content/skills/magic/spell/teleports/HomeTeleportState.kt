package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal object HomeTeleportState {
    private var Player.encodedCastCoords by intVarp("varp.home_teleport_cast_coords")

    fun castCoords(player: Player): Int? = player.encodedCastCoords.takeUnless { it == 0 }?.minus(1)

    fun start(player: Player, packedCoords: Int) {
        player.encodedCastCoords = packedCoords + 1
    }

    fun clear(player: Player) {
        player.encodedCastCoords = 0
    }
}
