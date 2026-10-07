package org.rsmod.content.other.ironman

import jakarta.inject.Inject
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.ironman.syncGamemodeFromVarbit
import org.rsmod.api.script.advanced.onOpPlayer4
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class IronmanScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        val events = eventBus
        onEvent<SessionStateEvent.Login> { player.syncGamemodeFromVarbit() }
        onOpPlayer4 {
            if (!IronmanRestrictions.blockTrade(player, it.target)) {
                events.publish(org.rsmod.api.player.events.PlayerTradeRequest(player, it.target))
            }
        }
    }
}
