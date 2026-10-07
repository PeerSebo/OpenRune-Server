package org.rsmod.api.player.interact

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.ObjUContentEvents
import org.rsmod.api.player.events.interact.ObjUDefaultEvents
import org.rsmod.api.player.events.interact.ObjUEvents
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.obj.Obj

public class ObjUInteractions @Inject private constructor(private val eventBus: EventBus) {
    private val logger = InlineLogger()

    public suspend fun interactOp(
        access: ProtectedAccess,
        obj: Obj,
        groundType: ItemServerType,
        objType: ItemServerType,
        inv: Inventory,
        invSlot: Int,
    ) {
        val invObj = inv[invSlot]
        if (objectVerify(inv, invObj, objType)) {
            access.opObjU(obj, groundType, objType, invSlot)
        }
    }

    private suspend fun ProtectedAccess.opObjU(
        obj: Obj,
        groundType: ItemServerType,
        objType: ItemServerType,
        invSlot: Int,
    ) {
        val script = opTrigger(obj, groundType, objType, invSlot)
        if (script != null) {
            eventBus.publish(this, script)
            return
        }
        mes(constants.dm_default, ChatType.Engine)
        logger.debug {
            "opObjU for `${objType.name}` on `${groundType.name}` is not implemented: " +
                "groundType=$groundType, objType=$objType"
        }
    }

    private fun opTrigger(
        obj: Obj,
        groundType: ItemServerType,
        objType: ItemServerType,
        invSlot: Int,
    ): OpEvent? {
        val groundContent = groundType.contentGroup

        val typeScript = ObjUEvents.Op(obj, invSlot, objType, groundType)
        if (eventBus.contains(typeScript::class.java, typeScript.id)) {
            return typeScript
        }

        val groupScript = ObjUContentEvents.Op(obj, invSlot, objType, groundContent)
        if (eventBus.contains(groupScript::class.java, groupScript.id)) {
            return groupScript
        }

        val defaultTypeScript = ObjUDefaultEvents.OpType(obj, invSlot, objType, groundType)
        if (eventBus.contains(defaultTypeScript::class.java, defaultTypeScript.id)) {
            return defaultTypeScript
        }

        val defGroupScript = ObjUDefaultEvents.OpContent(obj, invSlot, objType, groundContent)
        if (eventBus.contains(defGroupScript::class.java, defGroupScript.id)) {
            return defGroupScript
        }

        return null
    }

    private fun objectVerify(inv: Inventory, obj: InvObj?, type: ItemServerType): Boolean {
        if (obj == null || !obj.isType(type)) {
            resendSlot(inv, 0)
            return false
        }
        return true
    }
}
