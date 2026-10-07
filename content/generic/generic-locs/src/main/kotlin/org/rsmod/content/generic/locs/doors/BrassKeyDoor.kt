package org.rsmod.content.generic.locs.doors

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrassKeyDoor @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Door) { openDoor(it.loc, usedKey = false) }
        onOpLocU(Door, BrassKey) { openDoor(it.loc, usedKey = true) }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo, usedKey: Boolean) {
        val acrossTile = door.acrossTile() ?: return

        val locTile = door.coords
        val onAcrossSide =
            if (acrossTile.x != locTile.x) {
                (coords.x - locTile.x).sign == (acrossTile.x - locTile.x).sign
            } else {
                (coords.z - locTile.z).sign == (acrossTile.z - locTile.z).sign
            }

        val entering = onAcrossSide
        if (entering && invTotal(inv, BrassKey) == 0) {
            refuseEntry()
            return
        }

        if (usedKey) {
            mes("You unlock the door using the key you're carrying.")
        }

        // Route through the near doorway tile; a direct far-side route cuts diagonally through the wall.
        val waypoints =
            (if (entering) listOf(acrossTile, locTile) else listOf(locTile, acrossTile)).filter {
                it != coords
            }
        val openTicks = crossingOpenTicks(waypoints.size)

        locRepo.del(door, openTicks)
        locRepo.add(
            coords = acrossTile,
            internal = OpenLeaf,
            duration = openTicks,
            angle = door.turnAngle(rotations = 1),
            shape = door.shape,
        )
        soundSynth(OpenSound)

        if (waypoints.isNotEmpty()) {
            forcedWalk(waypoints, crossTiles = waypoints.size)
        }
    }

    private suspend fun ProtectedAccess.refuseEntry() {
        soundSynth(LockedSound)
        mesbox("The door is securely locked.")
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
        private const val Door = "loc.brasskeydoor"

        // The open leaf has no operations, so it reverts automatically rather than accepting Close.
        private const val OpenLeaf = "loc.inactivepoordoor"

        private const val BrassKey = "obj.edgevilledungeonkey"

        private const val OpenSound = "synth.DOOR_OPEN"

        private const val LockedSound = "synth.LOCKED"

        // Keep the crossing duration consistent with ChampionsGuildDoor and Misthalin door traversal.
        private fun crossingOpenTicks(tiles: Int): Int =
            maxOf(MIN_OPEN_TICKS, tiles + OPEN_TAIL_TICKS)

        private const val MIN_OPEN_TICKS = 4

        private const val OPEN_TAIL_TICKS = 3
    }
}
