package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TwiggyOKorn : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.diary_queen") { startDialogue(it.npc) { twiggy() } }
    }

    private suspend fun Dialogue.twiggy() {
        chatNpc(happy, "Hey there!")
        if (access.random.of(0, 49) == 0 && !access.inv.isFull()) {
            chatNpc(happy, "Here, have some chocolate!")
            chatPlayer(happy, "Wow, thanks!")
            access.invAdd(access.inv, CHOCOLATE_BAR)
        }
        var askedWho = false
        while (true) {
            val options = buildList {
                if (!askedWho) add("Who are you?" to 0)
                add("That's a nice cape - please tell me about it." to 1)
                add("Goodbye!" to 2)
            }
            when (menu(options)) {
                0 -> {
                    askedWho = true
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "My name is Twiggy O'Korn and I am the Mistress of all of the " +
                            "Achievement Diaries. It is a pleasure to make your acquaintance.",
                    )
                    chatPlayer(happy, "Likewise.")
                }
                1 -> {
                    chatPlayer(happy, "That's a nice cape - please tell me about it.")
                    chatNpc(
                        happy,
                        "Ah yes, this is a Cape of Accomplishment, available only to those who " +
                            "complete every single Achievement Diary.",
                    )
                    return
                }
                else -> {
                    chatPlayer(happy, "Goodbye!")
                    return
                }
            }
        }
    }

    private companion object {
        const val CHOCOLATE_BAR = "obj.chocolate_bar"
    }
}
