package org.rsmod.api.mechanics.dig

import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class SpadeDigScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.spade") { digWithSpade() }
    }
}
