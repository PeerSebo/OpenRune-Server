package org.rsmod.content.generic.npcs.person

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TownCrier : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in TownCriers) {
            onOpNpc1(npc) { startDialogue(it.npc) { crier() } }
        }
    }

    private suspend fun Dialogue.crier() {
        chatNpc(happy, "Hello citizen!")
        if (choice2("What do you do around here?", true, "See you later.", false)) {
            chatPlayer(quiz, "What do you do around here?")
            chatNpc(
                neutral,
                "I'm a Town Crier. It's my job to let people know of any recent news. After all, " +
                    "there's all sorts of things happening around here.",
            )
            chatPlayer(neutral, "I see. See you later.")
        } else {
            chatPlayer(neutral, "See you later.")
        }
        chatNpc(happy, "Until next time.")
    }

    companion object {
        internal val TownCriers = listOf(
            "npc.pmod_town_crier_varrock",
            "npc.pmod_town_crier_draynor",
            "npc.pmod_town_crier_falador",
            "npc.pmod_town_crier_east_ardoyne",
            "npc.pmod_town_crier_seers",
            "npc.pmod_town_crier_zeah",
            "npc.pmod_town_crier_kingstown",
            "npc.pmod_town_crier_kingstown_vis",
        )
    }
}
