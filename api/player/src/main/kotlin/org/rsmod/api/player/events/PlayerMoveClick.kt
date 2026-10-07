package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid

public class PlayerMoveClick(public val player: Player, public val destination: CoordGrid) : UnboundEvent {
    public var consumed: Boolean = false
}

public class PlayerNpcClick(public val player: Player, public val npc: Npc, public val op: InteractionOp) : UnboundEvent {
    public var consumed: Boolean = false
}
