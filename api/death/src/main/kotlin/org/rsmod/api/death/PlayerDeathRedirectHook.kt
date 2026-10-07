package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

public fun interface PlayerDeathRedirectHook {
    public fun destination(player: Player): CoordGrid?
}

public fun interface PlayerDeathItemStorageHook {
    /** Atomically stores every item and returns true, or returns false without changing anything. */
    public fun store(player: Player, items: List<InvObj>): Boolean
}

internal data class PlayerDeathRecovery(val context: PlayerDeathContext, val coords: CoordGrid) {
    companion object {
        fun resolve(context: PlayerDeathContext, hooks: Set<PlayerDeathRedirectHook>): PlayerDeathRecovery {
            // Instance exits are never allowed to change Wilderness, PvP, or ordinary world deaths.
            if (!context.inInstance || context.inWilderness || context.isPvpDeath) return PlayerDeathRecovery(context, context.coords)
            val exit = hooks.firstNotNullOfOrNull { it.destination(context.player) }
                ?: return PlayerDeathRecovery(context, context.coords)
            return PlayerDeathRecovery(context.copy(inInstance = false), exit)
        }
    }
}
