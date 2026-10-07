package org.rsmod.api.inv

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocTDefaultEvents
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocT
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class LocUOpScript @Inject private constructor(private val interactions: LocUInteractions) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocT("component.inventory:items") { opLocU(it) }
    }

    private suspend fun ProtectedAccess.opLocU(op: LocTDefaultEvents.Op) {
        val objType =
            op.objType?.let { id ->
                ServerCacheManager.getItems().values.firstOrNull { it.id == id.id }
            } ?: return resendSlot(inv, 0)

        // `op.loc` is the base loc, `op.vis`/`op.type` are what `LocTInteractions.opTrigger`
        // already resolved the multiloc to. Hand over the **base**: `LocUInteractions.opTrigger`
        // does its own multiloc walk and needs to start above the leaf, or its fallback to the
        // parent id never runs and every `onOpLocU` bound to a multiloc parent stops dispatching.
        interactions.interactOp(this, op.loc, objType, inv, op.comsub)
    }
}
