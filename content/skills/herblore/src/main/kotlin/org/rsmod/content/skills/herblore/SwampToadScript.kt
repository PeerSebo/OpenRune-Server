package org.rsmod.content.skills.herblore

import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SwampToadScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.swamp_toad") {
            if (it.inventory[it.slot]?.id != it.type.id) return@onOpHeld1
            val legs = getInvObj(InvObj("obj.toads_legs"))
            val result = invReplaceSlot(it.inventory, it.slot, 1, legs)
            if (result.success) {
                mes("You pull the legs off the toad. Poor toad. At least they'll grow back.")
            }
        }
    }
}
