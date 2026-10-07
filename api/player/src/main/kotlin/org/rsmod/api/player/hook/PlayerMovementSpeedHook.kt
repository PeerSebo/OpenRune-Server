package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed

public fun interface PlayerMovementSpeedHook {
    public fun overrideSpeed(player: Player): MoveSpeed?
}
