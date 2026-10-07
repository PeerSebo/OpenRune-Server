package org.rsmod.content.quest.area.lumbridge.lostcity

import jakarta.inject.Inject
import org.rsmod.api.player.output.soundSynth
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

class ZanarisShed @Inject constructor(
    private val lostCity: LostCityQuest,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SHED_DOOR) { openDoor(it.loc) }
        onOpLoc1(ENTRY_RING) { leaveZanaris() }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        if (isEntering(coords, door.coords) && lostCity.wieldsDramenStaff(player)) {
            enterZanaris()
            return
        }
        soundSynth(DOOR_OPEN_SOUND)
        val opened = openedCoords(door)
        val route = crossingRoute(coords, door.coords)
        var from = coords
        val tiles = route.sumOf { waypoint ->
            from.chebyshevDistance(waypoint).also { from = waypoint }
        }
        val openTicks = tiles + 2
        locRepo.del(door, openTicks)
        locRepo.add(opened, SHED_DOOR_OPEN, openTicks, door.turnAngle(1), door.shape) {
            player.soundSynth("synth.DOOR_CLOSE")
        }
        forcedWalk(route, crossTiles = tiles)
    }

    private fun openedCoords(door: BoundLocInfo): CoordGrid =
        when {
            door.shape == LocShape.WallStraight && door.angle == LocAngle.West -> door.coords.translateX(-1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.North -> door.coords.translateZ(1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.East -> door.coords.translateX(1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.South -> door.coords.translateZ(-1)
            else -> door.coords
        }

    private suspend fun ProtectedAccess.enterZanaris() {
        soundSynth(DOOR_OPEN_SOUND)
        delay(1)
        fairyVanish()
        telejump(ZANARIS_ARRIVAL)
        anim(APPEAR_ANIM)
        if (lostCity.quest.isQuestInProgress(player)) {
            lostCity.complete(this)
        }
    }

    private suspend fun ProtectedAccess.leaveZanaris() {
        if (!lostCity.wieldsDramenStaff(player)) {
            mes("The fairy rings only work for those who wield fairy magic.")
            return
        }
        fairyVanish()
        telejump(SHED_INSIDE)
        anim(APPEAR_ANIM)
    }

    private suspend fun ProtectedAccess.fairyVanish() {
        anim(VANISH_ANIM)
        spotanim(FLOWER_RING)
        soundSynth(TELEPORT_SOUND)
        delay(VANISH_TICKS)
    }

    internal companion object {
        fun isEntering(player: CoordGrid, door: CoordGrid): Boolean = player.x < door.x

        fun crossingRoute(player: CoordGrid, door: CoordGrid): List<CoordGrid> {
            val outside = door.translateX(-1)
            val route = if (isEntering(player, door)) listOf(outside, door) else listOf(door, outside)
            return route.dropWhile { it == player }
        }

        const val SHED_DOOR = "loc.zanarisdoor"
        const val SHED_DOOR_OPEN = "loc.zanarisdoor_open"
        const val ENTRY_RING = "loc.fairy_mushroom_ring"

        const val DOOR_OPEN_SOUND = "synth.DOOR_OPEN"

        val ZANARIS_ARRIVAL = CoordGrid(2452, 4473, 0)

        val SHED_INSIDE = CoordGrid(3203, 3169, 0)

        const val VANISH_ANIM = "seq.human_fairy_vanish"
        const val APPEAR_ANIM = "seq.human_fairy_appear"
        const val FLOWER_RING = "spotanim.fairy_flower_ring"
        const val TELEPORT_SOUND = "synth.FT_FAIRY_TELEPORT"
        const val VANISH_TICKS = 2
    }
}
