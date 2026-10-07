package org.rsmod.api.account.saver

import org.rsmod.game.entity.Player

/** Refresh persistent state on the game thread before the save payload is captured. */
public fun interface PlayerSavePreparationHook {
    public fun prepare(player: Player)
}
