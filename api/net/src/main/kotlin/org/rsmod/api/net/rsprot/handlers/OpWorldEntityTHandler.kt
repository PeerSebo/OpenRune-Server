package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.worldentities.OpWorldEntityT
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.events.interact.WorldEntityTEvents
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class OpWorldEntityTHandler
@Inject
constructor(
    private val eventBus: EventBus,
    private val worldEntities: WorldEntityRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
) : MessageHandler<OpWorldEntityT> {
    private val logger = InlineLogger()

    @OptIn(InternalApi::class)
    override fun handle(player: Player, message: OpWorldEntityT) {
        if (player.isDelayed) {
            player.clearMapFlag()
            return
        }

        val worldEntity = worldEntities[message.index]
        if (worldEntity == null) {
            player.clearMapFlag()
            player.clearPendingAction(eventBus)
            return
        }

        val event =
            WorldEntityTEvents.Op(
                worldEntity = worldEntity,
                selectedInterfaceId = message.selectedInterfaceId,
                selectedComponentId = message.selectedComponentId,
                selectedSub = message.selectedSub,
                selectedObj = message.selectedObj,
            )

        player.clearPendingAction(eventBus)
        logger.debug { "OpWorldEntityT: entity=$worldEntity" }
        protectedAccess.launchLenient(player) { eventBus.publish(this, event) }
    }
}
