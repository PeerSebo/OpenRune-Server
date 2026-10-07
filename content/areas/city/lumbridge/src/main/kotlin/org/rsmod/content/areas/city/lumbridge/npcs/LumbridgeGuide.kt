package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LumbridgeGuide : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.lumbridge_guide") { talkTo(it.npc) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(
            happy,
            "Welcome to Lumbridge! My name is Phileas, and I'm here to help newcomers find " +
                "their feet.",
        )

        val next = nextQuest(player)
        if (next == null) {
            chatNpc(
                happy,
                "You've already made a fine start on your adventures around here - I don't have " +
                    "anything new to suggest right now.",
            )
            return
        }

        chatNpc(
            neutral,
            "Why not see if you can help out with '${next.displayName}'? It's a good place to " +
                "start.",
        )
    }

    private fun nextQuest(player: Player): Quest? =
        QuestOrder.mapNotNull(Quest::get).firstOrNull { !it.isQuestCompleted(player) }

    private companion object {
        val QuestOrder =
            listOf(
                "quest_cooksassistant",
                "quest_restlessghost",
                "quest_xmarksthespot",
                "quest_sheepshearer",
                "quest_misthalinmystery",
                "quest_runemysteries",
            )
    }
}
