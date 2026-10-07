package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class PlayerTradeRequest(public val player: Player, public val target: Player) : UnboundEvent
