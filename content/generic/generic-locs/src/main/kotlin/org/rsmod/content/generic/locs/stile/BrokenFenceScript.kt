package org.rsmod.content.generic.locs.stile

import org.rsmod.api.config.Constants as constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Broken fences are blocking scenery; align with the near tile before crossing.
class BrokenFenceScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.gertrudefence") { climbOver(it.loc) }
    }

    private suspend fun ProtectedAccess.climbOver(fence: BoundLocInfo) {
        val crossing = Crossings[fence.coords] ?: return

        val fromSouth = coords.z <= fence.coords.z
        val near = if (fromSouth) crossing.south else crossing.north
        val far = if (fromSouth) crossing.north else crossing.south

        arriveDelay()

        // Op'd from a flanking tile rather than from the near tile: step onto the near tile first,
        // so the hop is always the straight two-tile one the exact-move below describes.
        if (coords != near) {
            forcedWalk(listOf(near), crossTiles = 1)
        }

        val angle = if (far.z > near.z) constants.em_face_north else constants.em_face_south
        exactMove(near, far, ClimbStartDelay, ClimbEndDelay, angle)
        anim(ClimbAnim, delay = ClimbStartDelay)
    }

    private data class Crossing(val south: CoordGrid, val north: CoordGrid)

    private companion object {
        private const val ClimbAnim = "seq.human_walk_style"

        private const val ClimbStartDelay = 30
        private const val ClimbEndDelay = 94

        private val Crossings: Map<CoordGrid, Crossing> =
            mapOf(
                CoordGrid(3308, 3492, 0) to
                    Crossing(CoordGrid(3308, 3491, 0), CoordGrid(3308, 3493, 0)),
                CoordGrid(4012, 4964, 0) to
                    Crossing(CoordGrid(4012, 4963, 0), CoordGrid(4012, 4965, 0)),
            )
    }
}
