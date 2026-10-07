package org.rsmod.content.areas.misc.goblinvillage

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// This ladder skips the middle floor, so generic one-level climb translation does not apply.
class GoblinVillageLadder : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(LadderBottom) { climb(it.loc) }
        onOpLoc1(LadderTop) { climb(it.loc) }
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo) {
        val levels = SHAFTS[loc.coords] ?: return
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(player.coords.translateLevel(levels))
    }

    private companion object {
        private const val LadderBottom = "loc.goblin_ladder_bottom"

        private const val LadderTop = "loc.goblin_ladder_top"

        private const val ClimbAnim = "seq.human_reachforladder"

        private val SHAFTS: Map<CoordGrid, Int> =
            mapOf(CoordGrid(2954, 3497, 0) to 2, CoordGrid(2954, 3497, 2) to -2)
    }
}
