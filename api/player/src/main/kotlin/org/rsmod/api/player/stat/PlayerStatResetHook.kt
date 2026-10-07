package org.rsmod.api.player.stat

import org.rsmod.game.entity.Player

/** Clears maintained effects before an activity explicitly resets all stats. */
public fun interface PlayerStatResetHook {
    public fun reset(player: Player)
}
