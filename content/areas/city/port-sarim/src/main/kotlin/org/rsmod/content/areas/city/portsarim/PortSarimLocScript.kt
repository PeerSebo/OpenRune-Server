package org.rsmod.content.areas.city.portsarim

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PortSarimLocScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.vc_manhole_open") { climbDownManhole() }
        onOpLoc1("loc.vc_ladder") { climbUpFromSewer() }
        onOpLoc1("loc.jewellersdoor") { walkThroughDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDownManhole() {
        arriveDelay()
        anim("seq.human_pickupfloor")
        delay(1)
        mes("You climb through the manhole")
        telejump(SEWER_LANDING)
    }

    private suspend fun ProtectedAccess.climbUpFromSewer() {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(MANHOLE_EXIT)
    }

    private suspend fun ProtectedAccess.walkThroughDoor(door: BoundLocInfo) {
        arriveDelay()
        val inside = door.coords.translateX(-1)
        val dest = if (coords.x >= door.coords.x) inside else door.coords
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, DOOR_OPEN_TICKS)
        locRepo.add(openCoords, OPEN_DOOR, DOOR_OPEN_TICKS, door.turnAngle(rotations = 1), door.shape)
        soundSynth("synth.DOOR_OPEN")
        playerMove(dest)
    }

    private companion object {
        const val UNDERGROUND_OFFSET = 6400
        const val OPEN_DOOR = "loc.inactiveposhdoor"
        const val DOOR_OPEN_TICKS = 3
        const val WYVERN_TUNNEL_LEVEL = 82
        const val UNDERWALL_TUNNEL_LEVEL = 42
        const val STILE_START_CYCLES = 30
        const val STILE_END_CYCLES = 94
        const val STILE_TICKS = 3
        const val FACE_SOUTH = 0
        const val FACE_WEST = 512
        const val FACE_NORTH = 1024
        const val FACE_EAST = 1536

        val SEWER_LANDING = CoordGrid(2962, 9650, 0)
        val MANHOLE_EXIT = CoordGrid(3018, 3233, 0)
        val UNDERWALL_EAST_EXIT = CoordGrid(3070, 3257, 0)
        val UNDERWALL_WEST_EXIT = CoordGrid(3066, 3257, 0)
    }
}
