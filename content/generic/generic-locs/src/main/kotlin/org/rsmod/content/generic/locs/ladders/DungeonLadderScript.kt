package org.rsmod.content.generic.locs.ladders

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.SequenceServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DungeonLadderScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpContentLoc1("content.dungeonladder_down") { climb(it.loc, it.type, 6400) }
        onOpContentLoc1("content.dungeonladder_up") { climb(it.loc, it.type, -6400) }
    }

    private suspend fun ProtectedAccess.climb(
        loc: BoundLocInfo,
        type: ObjectServerType,
        translateZ: Int,
    ) {
        arriveDelay()
        val dest = destination(loc, player.coords, translateZ)
        anim(RSCM.getReverseMapping(RSCMType.SEQ, type.climbAnim().id))
        delay(1)
        telejump(dest)
    }

    private fun ObjectServerType.climbAnim(): SequenceServerType = param(params.climb_anim)

    private fun destination(loc: BoundLocInfo, approach: CoordGrid, translateZ: Int): CoordGrid =
        if (translateZ < 0 && loc.id == "loc.ladder_from_cellar".asRSCM() &&
            loc.coords == CoordGrid(3097, 9867, 0)
        ) {
            CoordGrid(3096, 3468, 0)
        } else {
            approach.translateZ(translateZ)
        }
}
