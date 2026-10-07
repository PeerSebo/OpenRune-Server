package org.rsmod.api.death.grave

import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * Fires after NPC removal, grave-varp clearing and successful transfers to Death storage;
 * refused items remain recoverable.
 */
public interface GraveCollapseHook {
    /**
     * @param moved the objs that were successfully transferred into `inv.death_permanent`.
     * @param stranded objs that did **not** fit into `inv.death_permanent` and were therefore left
     *   behind in `inv.gravestone`. Normally empty. Nothing is ever destroyed, so a non-empty list
     *   means an operator needs to intervene; [GraveManager] logs it.
     */
    public fun onCollapse(
        player: Player,
        reason: GraveCollapseReason,
        moved: List<InvObj>,
        stranded: List<InvObj>,
    )
}

/** Area hooks pause the grave timer; the separate idle-player pause rule remains unimplemented. */
public interface GraveTimerPauseHook {
    public fun isPaused(player: Player): Boolean
}
