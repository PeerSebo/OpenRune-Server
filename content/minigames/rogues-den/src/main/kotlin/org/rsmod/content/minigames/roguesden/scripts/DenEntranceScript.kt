package org.rsmod.content.minigames.roguesden.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DenEntranceScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(TrapdoorEntrance) { descend(it.loc) }
        onOpLoc1(PassageToPub) { ascend(it.loc) }
    }

    private suspend fun ProtectedAccess.descend(loc: BoundLocInfo) {
        if (loc.coords != TrapdoorTile) {
            return
        }
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(DenLanding)
    }

    private suspend fun ProtectedAccess.ascend(loc: BoundLocInfo) {
        if (loc.coords != PassageTile) {
            return
        }
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(TrapdoorTile)
    }

    private companion object {
        private const val TrapdoorEntrance = "loc.roguesden_trapdoor_entrance"

        private const val PassageToPub = "loc.roguesden_passage_to_pub"

        private const val ClimbAnim = "seq.human_reachforladder"

        private val TrapdoorTile = CoordGrid(2905, 3537, 0)

        private val PassageTile = CoordGrid(3061, 4986, 1)

        // The sealed chamber has only two standable tiles; land inside it, not on the passage wall.
        private val DenLanding = CoordGrid(3061, 4985, 1)
    }
}
