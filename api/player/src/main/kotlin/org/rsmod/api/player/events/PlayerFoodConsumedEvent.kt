package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public data class PlayerFoodConsumedEvent(
    public val player: Player,
    public val item: String,
    public val heal: Int,
) : UnboundEvent
