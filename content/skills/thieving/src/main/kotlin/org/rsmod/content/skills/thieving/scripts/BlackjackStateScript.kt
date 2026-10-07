package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlackjackStateScript @Inject constructor() : PluginScript(), PlayerDeathCleanupHook {
    override fun ScriptContext.startup() {
        onPlayerLogin { BlackjackState.clear(player) }
        onPlayerLogout { BlackjackState.clear(player) }
    }

    override fun cleanup(player: Player) {
        BlackjackState.clear(player)
    }
}
