package org.rsmod.content.areas.misc.goblinvillage

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GoblinVillageSacks : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(WrigglingSacks) { prod() }
        onOpLoc2(WrigglingSacks) { refuseToOpen() }
    }

    private suspend fun ProtectedAccess.prod() {
        arriveDelay()
        mesbox("You hear a muffled groan. The sack wriggles slightly.")
    }

    private suspend fun ProtectedAccess.refuseToOpen() {
        arriveDelay()
        mes("I have no reason to do that.")
    }

    private companion object {
        private const val WrigglingSacks = "loc.percy_sacks"
    }
}
