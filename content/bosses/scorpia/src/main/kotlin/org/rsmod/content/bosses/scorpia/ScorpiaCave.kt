package org.rsmod.content.bosses.scorpia

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ScorpiaCave : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.wilderness_scorpion_entrance") {
            val passage = Passages.firstOrNull { path -> path.entrance == it.loc.coords } ?: return@onOpLoc1
            arriveDelay()
            telejump(passage.inside, TeleportType.Exempt)
        }
        onOpLoc1("loc.wilderness_scorpion_exit") {
            val passage = Passages.firstOrNull { path -> path.exit == it.loc.coords } ?: return@onOpLoc1
            arriveDelay()
            telejump(passage.outside, TeleportType.Exempt)
        }
    }

    internal data class Passage(val entrance: CoordGrid, val exit: CoordGrid, val inside: CoordGrid, val outside: CoordGrid)

    internal companion object {
        val Passages = listOf(
            Passage(CoordGrid(3231, 3936), CoordGrid(3233, 10331), CoordGrid(3233, 10332), CoordGrid(3233, 3938)),
            Passage(CoordGrid(3231, 3951), CoordGrid(3232, 10352), CoordGrid(3232, 10351), CoordGrid(3232, 3950)),
            Passage(CoordGrid(3241, 3949), CoordGrid(3243, 10352), CoordGrid(3243, 10351), CoordGrid(3242, 3948)),
        )
        val QueenSpawn = CoordGrid(3233, 10341)
    }
}
