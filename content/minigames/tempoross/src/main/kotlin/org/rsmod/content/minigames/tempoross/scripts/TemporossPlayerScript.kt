package org.rsmod.content.minigames.tempoross.scripts

import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.minigames.tempoross.configs.migrateTemporossVars
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TemporossPlayerScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.migrateTemporossVars() }
    }
}
