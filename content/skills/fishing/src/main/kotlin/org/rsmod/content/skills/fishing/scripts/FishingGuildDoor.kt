package org.rsmod.content.skills.fishing.scripts

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingGuildDoor @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fishguilddoor") { openDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        val acrossTile = door.acrossTile() ?: return

        val inside = if (acrossTile.z > door.coords.z) acrossTile else door.coords
        val outside = if (inside == acrossTile) door.coords else acrossTile

        val onInsideSide =
            if (inside.x != outside.x) {
                (coords.x - outside.x).sign == (inside.x - outside.x).sign
            } else {
                (coords.z - outside.z).sign == (inside.z - outside.z).sign
            }
        val entering = !onInsideSide

        if (entering && player.fishingLvl < REQUIRED_FISHING_LEVEL) {
            mes("You need a Fishing level of $REQUIRED_FISHING_LEVEL to enter the Fishing Guild.")
            return
        }

        // Route through the doorway anchor to avoid diagonal wall clipping.
        val waypoints =
            (if (entering) listOf(outside, inside) else listOf(inside, outside)).filter {
                it != coords
            }
        val openTicks = crossingOpenTicks(waypoints.size)

        // Remove the closed leaf's collision and redraw the open leaf across its edge.
        locRepo.del(door, openTicks)
        locRepo.add(
            coords = acrossTile,
            internal = "loc.poshdooropen",
            duration = openTicks,
            angle = door.turnAngle(rotations = 1),
            shape = door.shape,
        )
        soundSynth("synth.NICEDOOR_OPEN")

        if (waypoints.isNotEmpty()) {
            forcedWalk(waypoints, crossTiles = waypoints.size)
        }
    }

    private fun BoundLocInfo.acrossTile(): CoordGrid? {
        if (shape != LocShape.WallStraight) {
            return null
        }
        return when (angle) {
            LocAngle.West -> coords.translateX(-1)
            LocAngle.North -> coords.translateZ(1)
            LocAngle.East -> coords.translateX(1)
            LocAngle.South -> coords.translateZ(-1)
        }
    }

    private companion object {
        private const val REQUIRED_FISHING_LEVEL = 68

        private fun crossingOpenTicks(tiles: Int): Int =
            maxOf(MIN_OPEN_TICKS, tiles + OPEN_TAIL_TICKS)

        private const val MIN_OPEN_TICKS = 4

        private const val OPEN_TAIL_TICKS = 3
    }
}
