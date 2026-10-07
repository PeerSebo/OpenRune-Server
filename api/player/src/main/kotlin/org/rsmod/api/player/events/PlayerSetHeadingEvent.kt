package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity

/**
 * A native steering request from [player]. This event grants no control over a world entity:
 * subscribers must verify that the player currently controls the relevant live boat's helm.
 */
public data class PlayerSetHeadingEvent(public val player: Player, public val heading: Int) :
    UnboundEvent {
    init {
        require(heading in 0 until HEADING_COUNT) { "Invalid heading: $heading" }
    }

    public val angle: Int
        get() = heading * (WorldEntity.ANGLE_UNITS_PER_TURN / HEADING_COUNT)

    public companion object {
        public const val HEADING_COUNT: Int = 16
    }
}
