package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.worldentities.OpWorldEntity
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.events.interact.WorldEntityEvents
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity

/**
 * Ops 1-5 carry an allocated world-entity index. Validate that instance before applying any
 * interaction side effects.
 */
class OpWorldEntityHandler
@Inject
constructor(
    private val eventBus: EventBus,
    private val worldEntities: WorldEntityRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
) : MessageHandler<OpWorldEntity> {
    private val logger = InlineLogger()

    @OptIn(InternalApi::class)
    override fun handle(player: Player, message: OpWorldEntity) {
        if (player.isDelayed) {
            player.clearMapFlag()
            return
        }

        // An index that resolves to nothing is not a click on anything, so it must have **no**
        // side effects. Clearing the map flag and the pending action here let a spoofed or stale
        // index cancel whatever real action the player was in the middle of - a free interrupt for
        // anyone willing to send opcode 96 with a made-up index, and a race for anyone whose boat
        // was released in the same cycle they clicked it.
        val worldEntity = worldEntities[message.index] ?: return

        val event = worldEntity.toOp(message.op)
        if (event == null) {
            logger.debug { "OpWorldEntity unhandled op: op=${message.op}, entity=$worldEntity" }
            return
        }

        player.clearPendingAction(eventBus)
        logger.debug { "OpWorldEntity: op=${message.op}, entity=$worldEntity" }
        protectedAccess.launchLenient(player) { eventBus.publish(this, event) }
    }

    private fun WorldEntity.toOp(op: Int): WorldEntityEvents.Op? =
        when (op) {
            1 -> WorldEntityEvents.Op1(this)
            2 -> WorldEntityEvents.Op2(this)
            3 -> WorldEntityEvents.Op3(this)
            4 -> WorldEntityEvents.Op4(this)
            5 -> WorldEntityEvents.Op5(this)
            else -> null
        }
}
