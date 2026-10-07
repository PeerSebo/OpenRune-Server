package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Iffie : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.varrock_granny_1") { startDialogue(it.npc) { iffie() } }
    }

    private suspend fun Dialogue.iffie() {
        chatNpc(confused, "Sorry, dearie, if I stop to chat I'll lose count.")
    }
}
