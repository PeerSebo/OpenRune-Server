package org.rsmod.content.minigames.gotr.scripts

import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.minigames.gotr.migrateGotrRewards
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrPlayerScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.migrateGotrRewards() }
    }
}
