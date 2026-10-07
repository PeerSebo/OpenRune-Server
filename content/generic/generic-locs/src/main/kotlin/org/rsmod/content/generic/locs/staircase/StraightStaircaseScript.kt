package org.rsmod.content.generic.locs.staircase

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.util.Translation
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StraightStaircaseScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(FaladorStairsBottom) { climb(it.loc, FaladorUp) }
        onOpLoc1(FaladorStairsTop) { climb(it.loc, FaladorDown) }

        onOpLoc1(PoorStairsBottom) { climb(it.loc, PoorUp) }
        onOpLoc1(PoorStairsTop) { climb(it.loc, PoorDown) }

        onOpLoc1(NarrowStairsBottom) { climb(it.loc, NarrowUp) }
        onOpLoc1(NarrowStairsTop) { climb(it.loc, NarrowDown) }
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo, step: Translation) {
        arriveDelay()
        telejump(loc.coords.translate(step))
    }

    private companion object {
        private const val FaladorStairsBottom = "loc.fai_falador_stairs"

        private const val FaladorStairsTop = "loc.fai_falador_stairstop"

        private const val PoorStairsBottom = "loc.poor_stairs"

        private const val PoorStairsTop = "loc.poor_stairstop"

        private const val NarrowStairsBottom = "loc.narrowstairs_wooden_bottom"

        private const val NarrowStairsTop = "loc.narrowstairs_wooden_top"

        private val FaladorUp = Translation(x = 0, z = 2, level = 1)

        private val FaladorDown = Translation(x = 0, z = -2, level = -1)

        private val PoorUp = Translation(x = 2, z = 0, level = 1)

        private val PoorDown = Translation(x = -2, z = 0, level = -1)

        private val NarrowUp = Translation(x = 2, z = 0, level = 1)

        private val NarrowDown = Translation(x = -2, z = 0, level = -1)
    }
}
