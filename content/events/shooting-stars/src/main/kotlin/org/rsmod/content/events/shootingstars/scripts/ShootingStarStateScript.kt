package org.rsmod.content.events.shootingstars.scripts

import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.events.shootingstars.migrateShootingStarState
import org.rsmod.content.events.shootingstars.miningShootingStar
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ShootingStarStateScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            player.migrateShootingStarState()
            player.miningShootingStar = false
        }
        onPlayerLogout { player.miningShootingStar = false }
    }
}
