package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryKovacViewScript @Inject constructor(private val npcs: NpcList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Spawn>(KOVAC.asRSCM()) { update(npc) }
        onGameStartup { npcs.forEach(::update) }
    }

    private fun update(npc: Npc) {
        if (!npc.isType(KOVAC) || npc.spawnCoords != SPAWN) return
        npc.transmog(requireNotNull(ServerCacheManager.getNpc(UNLOCKED_KOVAC.asRSCM())), Int.MAX_VALUE)
        npc.infoProtocol.setBaseAnimations(npc.type)
    }

    internal companion object {
        const val KOVAC = "npc.giants_foundry_kovac_multi"
        const val UNLOCKED_KOVAC = "npc.giants_foundry_kovac"
        val SPAWN = CoordGrid(3369, 11486, 0)
    }
}
