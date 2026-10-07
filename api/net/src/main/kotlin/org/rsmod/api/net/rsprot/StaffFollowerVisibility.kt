package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StaffFollowerVisibility @Inject constructor(
    private val npcs: NpcList,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<StaffVisibilityChanged> { synchronize() }
        // Pet timers (including cat growth) and login spawns have run. NPC info has not been sent.
        onEvent<GameLifecycle.LateCycle> { synchronize() }
    }

    private fun synchronize() {
        for (npc in npcs) {
            val protocol = npc.infoProtocol as? RspNpcInfo ?: continue
            val owner = if (npc.type.isFollower) npc.spawnOwner.resolve(players) else null
            protocol.setStaffFollowerHidden(owner?.staffInvisible == true)
        }
    }
}
