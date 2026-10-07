package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.objs.OpObjT
import org.rsmod.api.net.rsprot.player.InterfaceEvents
import org.rsmod.api.player.interact.ObjTInteractions
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.ctrlMoveSpeed
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionObjT
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.obj.Obj
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class OpObjTHandler
@Inject
constructor(
    private val eventBus: EventBus,
    private val objRegistry: ObjRegistry,
    private val objInteractions: ObjTInteractions,
) : MessageHandler<OpObjT> {
    private val logger = InlineLogger()

    private val OpObjT.asComponent: Component
        get() = Component(selectedInterfaceId, selectedComponentId)

    override fun handle(player: Player, message: OpObjT) {
        if (player.isDelayed) {
            return
        }

        val coords = CoordGrid(message.x, message.z, player.level)
        val obj = findObj(player, coords, message.id)
        if (obj == null) {
            player.clearMapFlag()
            player.clearPendingAction(eventBus)
            return
        }
        val type = ServerCacheManager.getItem(obj.type) ?: return
        val interfaceType = ServerCacheManager.fromInterface(message.asComponent.packed)
        val componentType = ServerCacheManager.fromComponent(message.asComponent.packed)
        val objType = message.selectedObj.takeIf { it != -1 }?.let(ServerCacheManager::getItem)

        val isValidInterface =
            player.ui.containsOverlay(interfaceType) || player.ui.containsModal(interfaceType)
        if (!isValidInterface) {
            player.clearMapFlag()
            player.clearPendingAction(eventBus)
            return
        }

        val comsub = message.selectedSub

        val targetEnabled =
            InterfaceEvents.isEnabled(player.ui, componentType, comsub, IfEvent.TgtObj)
        if (!targetEnabled) {
            player.clearMapFlag()
            player.clearPendingAction(eventBus)
            return
        }

        val speed = if (message.controlKey) player.ctrlMoveSpeed() else null
        val opTrigger = objInteractions.hasOpTrigger(obj, type, objType, componentType, comsub)
        val apTrigger = objInteractions.hasApTrigger(obj, type, objType, componentType, comsub)
        val interaction =
            InteractionObjT(
                target = obj,
                comsub = comsub,
                objType = objType,
                component = componentType,
                hasOpTrigger = opTrigger,
                hasApTrigger = apTrigger,
            )
        val routeRequest = RouteRequestCoord(coords, clientRequest = true)
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceSquare(coords)
        player.interaction = interaction
        player.routeRequest = routeRequest
        player.tempMoveSpeed = speed
        logger.debug {
            "OpObjT: obj=$obj, type=$type, comsub=$comsub, " +
                "component=$componentType, selected=$objType"
        }
    }

    private fun findObj(player: Player, coords: CoordGrid, type: Int): Obj? =
        objRegistry.findAll(coords).firstOrNull { it.type == type && it.isVisibleTo(player) }
}
