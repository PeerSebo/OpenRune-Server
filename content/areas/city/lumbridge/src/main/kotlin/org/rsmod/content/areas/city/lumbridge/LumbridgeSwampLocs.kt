package org.rsmod.content.areas.city.lumbridge

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LumbridgeSwampLocs : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.ham_multi_trapdoor") {
            arriveDelay()
            mes("You try to open the trap door.", ChatType.Spam)
            mes("This trapdoor seems totally locked.", ChatType.Spam)
            soundSynth("synth.LOCKED")
        }
    }
}
