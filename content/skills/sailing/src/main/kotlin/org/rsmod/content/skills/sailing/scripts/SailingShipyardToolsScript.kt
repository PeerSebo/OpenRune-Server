package org.rsmod.content.skills.sailing.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.sailing.SHIPBUILDING_HAMMER
import org.rsmod.content.skills.sailing.SHIPBUILDING_SAW
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingShipyardToolsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.crate_hammers") { takeTool(it.loc, SHIPBUILDING_HAMMER, "hammer") }
        onOpLoc1("loc.crate_saws") { takeTool(it.loc, SHIPBUILDING_SAW, "saw") }
    }

    private suspend fun ProtectedAccess.takeTool(crate: BoundLocInfo, tool: String, name: String) {
        arriveDelay()
        faceLoc(crate)
        if (inv.isFull() || !invAdd(inv, tool, count = 1).success) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("You take a $name from the crate.")
    }
}
