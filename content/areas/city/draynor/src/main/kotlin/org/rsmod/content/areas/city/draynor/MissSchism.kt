package org.rsmod.content.areas.city.draynor

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MissSchism : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.wom_gossip") {
            startDialogue(it.npc) {
                chatNpc(shocked, "Oooh, my dear, have you heard the news?")
                when (choice3(News, 1, Identity, 2, Goodbye, 3)) {
                    1 -> news()
                    2 -> identity()
                    3 -> goodbye()
                }
            }
        }
    }

    private suspend fun Dialogue.news() {
        chatPlayer(bored, News)
        chatNpc(shocked, "It's terrible, absolutely terrible! Those poor people!")
        chatPlayer(bored, "Okay, yeah.")
        chatNpc(shocked, "And who'd have ever thought such a sweet old gentleman would do such a thing?")
        chatPlayer(neutral, "I really don't know what you're talking about.")
        chatNpc(shocked, "Oooh, my dear, had you not heard?")
        chatPlayer(bored, "At this rate I don't think I want to know...")
        chatNpc(shocked, "Oh, you must quickly go and speak to the bank guard outside the bank. " +
            "He'll tell you all about it, oooh, such a shock it was...")
        chatPlayer(neutral, "...")
    }

    private suspend fun Dialogue.identity() {
        chatPlayer(quiz, Identity)
        chatNpc(happy, "I, my dear, am a concerned citizen of Draynor Village. " +
            "Ever since the Council allowed those farmers to set up their stalls here, " +
            "we've had a constant flow of thieves and murderers through our fair village, and I decided")
        chatNpc(happy, "that someone HAD to stand up and keep an eye on the situation.")
        chatNpc(happy, "I also do voluntary work for the Draynor Manor Restoration Fund. " +
            "We're campaigning to have Draynor Manor turned into a museum before the wet-rot destroys it completely.")
        chatPlayer(neutral, "Right...")
        when (choice2(News, true, Goodbye, false)) {
            true -> news()
            false -> goodbye()
        }
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(bored, Goodbye)
        chatNpc(sad, "Oooh.")
    }

    private companion object {
        const val News = "Okay, tell me about the news."
        const val Identity = "Who are you?"
        const val Goodbye = "I'm not talking to you, you horrible woman."
    }
}
