package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo

/** A verified root-world loc click from a deck. Content must explicitly allow the cross-world op. */
public class PlayerWorldEntityLocOpEvent(
    public val player: Player,
    public val worldEntity: WorldEntity,
    public val loc: BoundLocInfo,
    public val op: InteractionOp,
) : UnboundEvent {
    public var accepted: Boolean = false
}
