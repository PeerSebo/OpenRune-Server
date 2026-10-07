package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public fun interface PlayerRespawnHook {
    public fun respawn(player: Player): CoordGrid?
}
