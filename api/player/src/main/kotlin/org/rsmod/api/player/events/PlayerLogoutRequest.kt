package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class PlayerLogoutRequest(public val player: Player) : UnboundEvent {
    public var consumed: Boolean = false
}
