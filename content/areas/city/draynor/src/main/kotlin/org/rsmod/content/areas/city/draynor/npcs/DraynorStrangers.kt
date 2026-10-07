package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorStrangers : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.wgs_lucien_spy") { startDialogue(it.npc) { shadyStranger() } }
        onOpNpc1("npc.wgs_spy2") { startDialogue(it.npc) { suspiciousOutsider() } }
    }

    private suspend fun Dialogue.shadyStranger() {
        chatPlayer(quiz, "Hello there. What are you doing here?")
        chatNpc(shifty, "Oh, nothing much. I'd just heard Draynor was a nice place to visit.")
        chatPlayer(confused, "Fair enough.")
    }

    private suspend fun Dialogue.suspiciousOutsider() {
        chatPlayer(quiz, "Hello there. What are you doing here?")
        chatNpc(quiz, "That's not really any of your business, is it?")
        chatPlayer(confused, "Fair enough.")
    }
}
