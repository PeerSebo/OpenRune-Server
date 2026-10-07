package org.rsmod.content.minigames.sorceressgarden.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GardenDoorScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        for (season in GardenSeason.entries) {
            onOpLoc1(season.doorLoc) { crossGate(it.loc, season) }
        }
    }

    private suspend fun ProtectedAccess.crossGate(door: BoundLocInfo, season: GardenSeason) {
        val leaving = player.coords == door.acrossTile()
        if (!leaving && player.thievingLvl < season.thievingLevel) {
            mes("You need a Thieving level of ${season.thievingLevel} to enter this garden.")
            return
        }

        if (!leaving && player.coords != door.coords) {
            // Wall reach allows flank ops; align to the gate anchor before crossing.
            forcedWalk(door.coords, crossTiles = 1)
        }

        val exit = if (leaving) door.coords else door.acrossTile()
        delay(2)
        soundSynth(GateSound)
        telejump(exit)
    }

    private fun BoundLocInfo.acrossTile(): CoordGrid =
        when (angle.id) {
            0 -> CoordGrid(coords.x - 1, coords.z, coords.level)
            1 -> CoordGrid(coords.x, coords.z + 1, coords.level)
            2 -> CoordGrid(coords.x + 1, coords.z, coords.level)
            else -> CoordGrid(coords.x, coords.z - 1, coords.level)
        }

    private companion object {
        private const val GateSound = "synth.IRON_DOOR_OPEN"
    }
}
