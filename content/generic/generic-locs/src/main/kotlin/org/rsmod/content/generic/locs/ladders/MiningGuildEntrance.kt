package org.rsmod.content.generic.locs.ladders

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MiningGuildEntrance @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.mguild_ladder") { climbDown(it.loc) }
        onOpLoc1("loc.mguild_door") { crossGuildDoor(it.loc) }
        onOpLoc1("loc.mguild_door_inner1") { crossUngatedDoor(it.loc) }
        onOpLoc1("loc.mguild_door_inner2") { crossUngatedDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDown(ladder: BoundLocInfo) {
        if (ladder.coords !in LadderRing) {
            return
        }
        if (!requireMiningLevel()) {
            return
        }
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(player.coords.translateZ(UndergroundOffset))
    }

    private suspend fun ProtectedAccess.crossGuildDoor(door: BoundLocInfo) {
        val across = door.acrossTile() ?: return
        val inside = door.coords
        val outside = across

        val entering = !onSideOf(inside, outside)
        if (entering && !requireMiningLevel()) {
            return
        }
        walkThrough(door, inside, outside, entering)
    }

    private suspend fun ProtectedAccess.crossUngatedDoor(door: BoundLocInfo) {
        val across = door.acrossTile() ?: return
        walkThrough(door, across, door.coords, entering = onSideOf(door.coords, across))
    }

    private fun ProtectedAccess.onSideOf(inside: CoordGrid, outside: CoordGrid): Boolean =
        if (inside.x != outside.x) {
            (coords.x - outside.x).sign == (inside.x - outside.x).sign
        } else {
            (coords.z - outside.z).sign == (inside.z - outside.z).sign
        }

    private suspend fun ProtectedAccess.walkThrough(
        door: BoundLocInfo,
        inside: CoordGrid,
        outside: CoordGrid,
        entering: Boolean,
    ) {
        val waypoints =
            (if (entering) listOf(outside, inside) else listOf(inside, outside)).filter {
                it != coords
            }
        val openTicks = crossingOpenTicks(waypoints.size)

        locRepo.del(door, openTicks)
        soundSynth(DoorOpenSynth)

        if (waypoints.isNotEmpty()) {
            forcedWalk(waypoints, crossTiles = waypoints.size)
        }
    }

    private fun ProtectedAccess.requireMiningLevel(): Boolean {
        if (player.miningLvl >= RequiredMiningLevel) {
            return true
        }
        mes("You need a Mining level of $RequiredMiningLevel to enter.")
        return false
    }

    private fun BoundLocInfo.acrossTile(): CoordGrid? {
        if (shape != LocShape.WallStraight) {
            return null
        }
        return DoorTranslations.translateOpen(coords, shape, angle)
    }

    private companion object {
        private const val RequiredMiningLevel = 60

        private const val ClimbAnim = "seq.human_reachforladder"

        private const val DoorOpenSynth = "synth.DOOR_OPEN"

        private const val UndergroundOffset = 6400

        private val LadderRing =
            setOf(
                CoordGrid(3018, 3339, 0),
                CoordGrid(3019, 3338, 0),
                CoordGrid(3019, 3340, 0),
                CoordGrid(3020, 3339, 0),
            )

        private fun crossingOpenTicks(tiles: Int): Int = maxOf(MinOpenTicks, tiles + OpenTailTicks)

        private const val MinOpenTicks = 4

        private const val OpenTailTicks = 3
    }
}
