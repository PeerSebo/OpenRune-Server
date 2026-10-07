package org.rsmod.content.areas.city.portsarim

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AsgarnianIceDungeonTrapdoor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Trapdoor) { climbDown(it.loc.coords.x, it.loc.coords.z, it.loc.coords.level) }
    }

    private suspend fun ProtectedAccess.climbDown(x: Int, z: Int, level: Int) {
        if (x != TrapdoorX || z != TrapdoorZ || level != TrapdoorLevel) {
            return
        }

        arriveDelay()
        spam("You climb down through the trapdoor.")
        telejump(player.coords.translateZ(DungeonTranslateZ))
    }

    private companion object {
        private const val Trapdoor = "loc.fai_trapdoor"

        private const val TrapdoorX = 3008
        private const val TrapdoorZ = 3150
        private const val TrapdoorLevel = 0

        private const val DungeonTranslateZ = 6400
    }
}
