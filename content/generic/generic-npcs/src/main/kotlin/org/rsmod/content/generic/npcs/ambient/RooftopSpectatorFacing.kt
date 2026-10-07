package org.rsmod.content.generic.npcs.ambient

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RooftopSpectatorFacing @Inject constructor(private val npcs: NpcRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Spawn>("npc.rooftops_bigredjapan".asRSCM()) { face(npc) }
        onGameStartup {
            for (coords in Directions.keys) npcs.findAll(coords).forEach(::face)
        }
    }

    internal fun face(npc: Npc) {
        if (npc.id != "npc.rooftops_bigredjapan".asRSCM()) return
        Directions[npc.spawnCoords]?.let(npc::respawnDirection)
    }

    internal companion object {
        val Directions = mapOf(
            CoordGrid(3232, 3401, 3) to Direction.North,
            CoordGrid(2702, 3463, 2) to Direction.North,
            CoordGrid(3511, 3480, 2) to Direction.NorthWest,
            CoordGrid(3302, 3192, 3) to Direction.West,
        )
    }
}
