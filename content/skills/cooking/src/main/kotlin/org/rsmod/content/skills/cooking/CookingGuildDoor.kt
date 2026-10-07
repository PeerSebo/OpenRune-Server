package org.rsmod.content.skills.cooking

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CookingGuildDoor @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.chefdoor") { openDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        val (inside, outside) = insideAndOutside(door) ?: return

        val onInsideSide =
            if (inside.x != outside.x) {
                (coords.x - outside.x).sign == (inside.x - outside.x).sign
            } else {
                (coords.z - outside.z).sign == (inside.z - outside.z).sign
            }
        val entering = !onInsideSide

        if (entering && !meetsGuildRequirements()) {
            refuseEntry()
            return
        }
        walkThroughDoor(door, inside, outside, entering)
    }

    private fun ProtectedAccess.meetsGuildRequirements(): Boolean =
        player.hasGuildEntryOutfit() && player.cookingLvl >= REQUIRED_COOKING_LEVEL

    private suspend fun ProtectedAccess.refuseEntry() {
        val hat = player.hasGuildEntryOutfit()
        val level = player.cookingLvl >= REQUIRED_COOKING_LEVEL
        val text =
            when {
                level && !hat ->
                    "You can't come in here unless you're wearing a chef's hat, or something " +
                        "like that."
                !level && !hat ->
                    "Sorry. Only the finest chefs are allowed in here. Get your cooking level " +
                        "up to $REQUIRED_COOKING_LEVEL and come back wearing a chef's hat."
                else ->
                    "Sorry. Only the finest chefs are allowed in here. Get your cooking level " +
                        "up to $REQUIRED_COOKING_LEVEL."
            }
        startDialogue {
            chatNpcSpecific(
                title = HEAD_CHEF_TITLE,
                type = "npc.head_chef",
                mesanim = neutral,
                text = text,
            )
        }
    }

    private suspend fun ProtectedAccess.walkThroughDoor(
        door: BoundLocInfo,
        inside: CoordGrid,
        outside: CoordGrid,
        entering: Boolean,
    ) {
        // Route through the doorway anchor to avoid cutting diagonally through the wall.
        val waypoints =
            (if (entering) listOf(outside, inside) else listOf(inside, outside)).filter {
                it != coords
            }
        val openTicks = crossingOpenTicks(waypoints.size)

        // Remove the closed leaf's collision; redraw the open leaf across the edge with a quarter turn.
        val openAngle = door.turnAngle(rotations = 1)
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, openTicks)
        locRepo.add(openCoords, "loc.chefdoor_open", openTicks, openAngle, door.shape)

        soundSynth("synth.DOOR_OPEN")

        if (waypoints.isNotEmpty()) {
            forcedWalk(waypoints, crossTiles = waypoints.size)
        }
    }

    private fun insideAndOutside(door: BoundLocInfo): Pair<CoordGrid, CoordGrid>? {
        val across = door.acrossTile() ?: return null
        return if (across.z > door.coords.z) {
            across to door.coords
        } else {
            door.coords to across
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
        private const val REQUIRED_COOKING_LEVEL = 32

        private const val HEAD_CHEF_TITLE = "Head chef"

        private fun crossingOpenTicks(tiles: Int): Int =
            maxOf(MIN_OPEN_TICKS, tiles + OPEN_TAIL_TICKS)

        private const val MIN_OPEN_TICKS = 4

        private const val OPEN_TAIL_TICKS = 3
    }
}
