package org.rsmod.content.areas.misc.dwarvenmine.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DwarvenMineNpcsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.boot_the_dwarf") { startDialogue(it.npc) { boot() } }
        onOpNpc1("npc.favour_hammerspike_stoutbeard") { startDialogue(it.npc) { hammerspike() } }
        for (gangMember in GANG_MEMBERS) {
            onOpNpc1(gangMember) { startDialogue(it.npc) { gangMember() } }
        }
        onOpNpc1("npc.motherlode_guard") { startDialogue(it.npc) { motherlodeGuard() } }
    }

    private suspend fun Dialogue.boot() {
        chatNpc(neutral, "Hello tall person.")
        while (true) {
            if (choice2("Hello short person.", true, "Why are you called boot?", false)) {
                chatPlayer(neutral, "Hello short person.")
                chatNpc(neutral, "Hello tall person.")
                continue
            }
            chatPlayer(quiz, "Why are you called Boot?")
            chatNpc(
                neutral,
                "I'm called Boot, because when I was very young, I used to sleep, in a large boot.",
            )
            chatPlayer(bored, "Yeah, great, I didn't want your life story.")
            return
        }
    }

    private suspend fun Dialogue.hammerspike() {
        chatNpc(angry, "You looking at me? I don't see nobody else here!")
    }

    private suspend fun Dialogue.gangMember() {
        chatNpc(angry, "Yeah...whada you want?")
        while (true) {
            when (
                choice4(
                    "Who are you?",
                    1,
                    "What're you doing here?",
                    2,
                    "Who do you work for?",
                    3,
                    "Ok, thanks.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        angry,
                        "I'm nobody you need to worry about...who are you? Why are you asking so many " +
                            "questions? Are you with the Varrock guards?",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "What're you doing here?")
                    chatNpc(
                        neutral,
                        "Well, as if it's any of your business, I'm an associate of Hammerspike. He's a great " +
                            "dwarf you know. You could learn a lot from a dwarf like him.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Who do you work for?")
                    chatNpc(
                        neutral,
                        "I have an ongoing contract with Hammerspike, when he gives the word, the hammer " +
                            "starts flying.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.motherlodeGuard() {
        chatNpc(angry, "Halt! Are you here to sabotage the Motherlode Mine?")
        when (
            choice3(
                "What's the Motherlode Mine?",
                1,
                "No, I'm not.",
                2,
                "What would you do if I said 'yes'?",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What's the Motherlode Mine?")
                chatNpc(
                    neutral,
                    "Prospector Percy discovered unusual mineral veins in this cave. Now he's built a machine to " +
                        "let us extract useful ores.",
                )
                chatNpc(
                    neutral,
                    "Percy calls it the Motherlode. I don't know why, and I'm not asking - that's between him " +
                        "and his mother.",
                )
                chatNpc(
                    neutral,
                    "Dwarves don't normally let humans claim areas of the mine like this, but Percy's machine is " +
                        "really useful, so we struck a deal.",
                )
            }
            2 -> {
                chatPlayer(neutral, "No, I'm not.")
                chatNpc(happy, "That's a relief. Have a nice day.")
            }
            else -> {
                chatPlayer(quiz, "What would you do if I said 'yes'?")
                chatNpc(neutral, "I'd ask you not to.")
            }
        }
    }

    private companion object {
        val GANG_MEMBERS = listOf(
            "npc.favour_gangster_dwarf", "npc.favour_gangster_dwarf_2", "npc.favour_gangster_dwarf_3",
        )
    }
}
