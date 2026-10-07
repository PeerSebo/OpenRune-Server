package org.rsmod.content.areas.city.varrock.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: Add Champions' Challenge descent through the open cover.
class ChampionsGuildTrapdoor @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TrapdoorClosed) { openTrapdoor(it.loc) }
    }

    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo) {
        if (trapdoor.coords != TrapdoorTile) {
            return
        }
        arriveDelay()
        soundSynth(SynthTrapdoorOpen)
        locRepo.change(trapdoor, TrapdoorOpen, OpenDuration)
    }

    private companion object {
        private const val TrapdoorClosed = "loc.champions_trap_door_closed"

        private const val TrapdoorOpen = "loc.champions_trap_door_open"

        private const val SynthTrapdoorOpen = "synth.TRAPDOOR_OPEN"

        private val TrapdoorTile = CoordGrid(3190, 3355, 0)

        private const val OpenDuration = 500
    }
}
