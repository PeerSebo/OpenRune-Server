package org.rsmod.content.interfaces.settings.scripts

import org.rsmod.api.script.onPlayerInit
import org.rsmod.content.interfaces.settings.NewAccountPreferences
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NewAccountPreferencesScript : PluginScript() {
    override fun ScriptContext.startup() {
        val preferences = NewAccountPreferences.load()
        onPlayerInit { preferences.apply(player) }
    }
}
