package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Facing is spawn-specific; changing the shared banker type would turn bankers in other cities.
class VarrockWestBankFacing @Inject constructor(private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { faceWestBankTellers() }
    }

    private fun faceWestBankTellers() {
        for ((coords, direction) in TellerDirections) {
            for (npc in npcRepo.findAll(coords)) {
                if (npc.type.name.equals("Banker", ignoreCase = true)) {
                    npc.respawnDirection(direction)
                }
            }
        }
    }

    internal companion object {
        val TellerDirections = buildMap {
            for (z in 3436..3444 step 2) put(CoordGrid(3187, z, 0), Direction.West)
            for (x in 3251..3256) put(CoordGrid(x, 3418, 0), Direction.North)
            for (x in 3147..3148) put(CoordGrid(x, 3448, 0), Direction.North)
        }
    }
}
