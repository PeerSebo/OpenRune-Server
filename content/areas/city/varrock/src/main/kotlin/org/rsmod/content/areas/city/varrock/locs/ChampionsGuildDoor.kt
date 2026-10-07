package org.rsmod.content.areas.city.varrock.locs

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChampionsGuildDoor @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.championdoor") { openDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        if (vars["varp.qp"] < REQUIRED_QUEST_POINTS) {
            refuseEntry()
            return
        }
        walkThroughDoor(door)
    }

    private suspend fun ProtectedAccess.refuseEntry() {
        mes("The door won't open - you need at least 32 Quest Points.")
        startDialogue {
            chatNpcSpecific(
                title = "Guildmaster",
                type = "npc.guildmaster",
                mesanim = angry,
                text = "You have not proved yourself worthy to enter here yet.",
            )
        }
    }

    private suspend fun ProtectedAccess.walkThroughDoor(door: BoundLocInfo) {
        val acrossTile = door.acrossTile() ?: return

        val outside = door.coords
        val inside = acrossTile
        val onInsideSide =
            if (inside.x != outside.x) {
                (coords.x - outside.x).sign == (inside.x - outside.x).sign
            } else {
                (coords.z - outside.z).sign == (inside.z - outside.z).sign
            }
        val entering = !onInsideSide

        // Route through the near doorway tile to avoid cutting diagonally through the wall.
        val waypoints =
            (if (entering) listOf(outside, inside) else listOf(inside, outside)).filter {
                it != coords
            }
        val openTicks = crossingOpenTicks(waypoints.size)

        locRepo.del(door, openTicks)
        locRepo.add(
            coords = acrossTile,
            internal = "loc.championdoor_open",
            duration = openTicks,
            angle = door.turnAngle(rotations = 1),
            shape = door.shape,
        )
        soundSynth("synth.BIGDOOR_OPEN")

        if (waypoints.isNotEmpty()) {
            forcedWalk(waypoints, crossTiles = waypoints.size)
        }

        if (entering) {
            startDialogue {
                chatNpcSpecific(
                    title = "Guildmaster",
                    type = "npc.guildmaster",
                    mesanim = happy,
                    text = "Greetings bold adventurer. Welcome to the guild of Champions.",
                )
            }
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
        private const val REQUIRED_QUEST_POINTS = 32

        // Keep crossing duration consistent with Misthalin door traversal.
        private fun crossingOpenTicks(tiles: Int): Int =
            maxOf(MIN_OPEN_TICKS, tiles + OPEN_TAIL_TICKS)

        private const val MIN_OPEN_TICKS = 4

        private const val OPEN_TAIL_TICKS = 3
    }
}
