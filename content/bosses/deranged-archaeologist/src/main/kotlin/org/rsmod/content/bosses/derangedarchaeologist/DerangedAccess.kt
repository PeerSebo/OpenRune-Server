package org.rsmod.content.bosses.derangedarchaeologist

import jakarta.inject.Inject
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class DerangedAccess @Inject constructor(private val collision: CollisionFlagMap) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fossil_flora_swamp_fallen_trunk") {
            arriveDelay()
            val destination = crossingDestination(player.coords)
            if (!openLanding(collision, destination)) return@onOpLoc1
            val approach = CoordGrid(destination.x, if (destination.z == 3715) 3717 else 3715)
            if (player.coords != approach) {
                walk(approach)
                var remaining = 6
                while (player.coords != approach && remaining-- > 0) delay(1)
                if (player.coords != approach) return@onOpLoc1
            }
            if (!openLanding(collision, destination)) return@onOpLoc1
            anim("seq.regicide_stepover")
            try { forcedWalk(destination, crossTiles = 2) } finally { resetAnim() }
        }
    }

    internal companion object {
        fun crossingDestination(origin: CoordGrid): CoordGrid =
            CoordGrid(3682, if (origin.z > 3716) 3715 else 3717)

        fun openLanding(collision: CollisionFlagMap, tile: CoordGrid): Boolean =
            collision[tile.x, tile.z, tile.level] and
                (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) == 0
    }
}
