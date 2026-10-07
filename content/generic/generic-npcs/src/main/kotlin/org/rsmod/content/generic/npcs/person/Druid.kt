package org.rsmod.content.generic.npcs.person

import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Druid : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.druid") {
            startDialogue(it.npc) {
                chatPlayer(neutral, "Hello.")
                chatNpc(happy, "Good day to you, may nature smile upon you.")
            }
        }
    }
}
