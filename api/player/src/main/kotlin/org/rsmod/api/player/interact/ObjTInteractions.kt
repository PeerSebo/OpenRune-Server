package org.rsmod.api.player.interact

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.ApEvent
import org.rsmod.api.player.events.interact.ObjTContentEvents
import org.rsmod.api.player.events.interact.ObjTDefaultEvents
import org.rsmod.api.player.events.interact.ObjTEvents
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.events.EventBus
import org.rsmod.game.obj.Obj

public class ObjTInteractions @Inject constructor(private val eventBus: EventBus) {
    public fun opTrigger(
        obj: Obj,
        type: ItemServerType,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
    ): OpEvent? {
        val typeEvent = ObjTEvents.Op(obj, comsub, objType, type, component)
        if (eventBus.contains(typeEvent::class.java, typeEvent.id)) {
            return typeEvent
        }

        val contentEvent = ObjTContentEvents.Op(obj, comsub, objType, component, type.contentGroup)
        if (eventBus.contains(contentEvent::class.java, contentEvent.id)) {
            return contentEvent
        }

        val defaultEvent = ObjTDefaultEvents.Op(obj, comsub, objType, type, component)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasOpTrigger(
        obj: Obj,
        type: ItemServerType,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
    ): Boolean = opTrigger(obj, type, objType, component, comsub) != null

    public fun apTrigger(
        obj: Obj,
        type: ItemServerType,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
    ): ApEvent? {
        val typeEvent = ObjTEvents.Ap(obj, comsub, objType, type, component)
        if (eventBus.contains(typeEvent::class.java, typeEvent.id)) {
            return typeEvent
        }

        val contentEvent = ObjTContentEvents.Ap(obj, comsub, objType, component, type.contentGroup)
        if (eventBus.contains(contentEvent::class.java, contentEvent.id)) {
            return contentEvent
        }

        val defaultEvent = ObjTDefaultEvents.Ap(obj, comsub, objType, type, component)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasApTrigger(
        obj: Obj,
        type: ItemServerType,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
    ): Boolean = apTrigger(obj, type, objType, component, comsub) != null
}
