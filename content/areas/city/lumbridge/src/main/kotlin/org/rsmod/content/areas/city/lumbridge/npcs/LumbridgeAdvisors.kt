package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.adventurePaths by intVarBit("varbit.adventurepath_player_participating")

class LumbridgeAdvisors : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            if (player.adventurePaths == 0) player.adventurePaths = 1
        }
    }
}
