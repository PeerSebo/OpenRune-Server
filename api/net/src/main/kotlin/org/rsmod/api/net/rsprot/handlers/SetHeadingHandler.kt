package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import org.rsmod.api.player.events.PlayerSetHeadingEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class SetHeadingHandler @Inject constructor(private val eventBus: EventBus) :
    MessageHandler<SetHeading> {
    override fun handle(player: Player, message: SetHeading) {
        if (message.heading !in 0 until PlayerSetHeadingEvent.HEADING_COUNT) {
            return
        }
        // Steering is independent of walking or an action on the deck. The content subscriber
        // validates live captain/helm ownership before accepting this request.
        eventBus.publish(PlayerSetHeadingEvent(player, message.heading))
    }
}
