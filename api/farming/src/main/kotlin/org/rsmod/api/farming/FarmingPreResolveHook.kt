package org.rsmod.api.farming

import org.rsmod.game.entity.Player

/**
 * Run before growth catch-up so companion protection changes apply to the same resolution. Read
 * other patches through stored state to avoid recursive resolution.
 */
public fun interface FarmingPreResolveHook {
    public fun beforeResolve(player: Player, def: FarmingPatchDef)
}
