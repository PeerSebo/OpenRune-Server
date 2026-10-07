package org.rsmod.api.poh

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public data class PohHouseEnteredEvent(public val player: Player, public val buildMode: Boolean) :
    UnboundEvent

public data class PohHouseExitedEvent(public val player: Player) : UnboundEvent
