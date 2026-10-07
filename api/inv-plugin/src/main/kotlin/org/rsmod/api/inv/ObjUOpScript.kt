package org.rsmod.api.inv

import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.ObjTDefaultEvents
import org.rsmod.api.player.interact.ObjUInteractions
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpObjT
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class ObjUOpScript @Inject private constructor(private val interactions: ObjUInteractions) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpObjT("component.inventory:items") { opObjU(it) }
    }

    private suspend fun ProtectedAccess.opObjU(op: ObjTDefaultEvents.Op) {
        val objType = op.objType ?: return resendSlot(inv, 0)
        interactions.interactOp(this, op.obj, op.type, objType, inv, op.comsub)
    }
}
