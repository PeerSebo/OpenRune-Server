package org.rsmod.game.entity.player

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** Published on the game thread after an explicit staff invisibility toggle. */
public data class StaffVisibilityChanged(val player: Player) : UnboundEvent
