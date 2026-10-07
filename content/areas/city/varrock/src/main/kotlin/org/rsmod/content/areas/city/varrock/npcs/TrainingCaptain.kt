package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrainingCaptain : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.fai_varrock_guard_trainer02") {
            startDialogue(it.npc) { greeting() }
        }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(angry, "What do you want, civilian?")
        when (choice3("What are you doing?", 1, "Can I have some armour like yours?", 2, "I want nothing.", 3)) {
            1 -> recruits()
            2 -> recruitment()
            3 -> {
                chatPlayer(neutral, "I want nothing.")
                chatNpc(angry, "Lucky you. Go away.")
            }
        }
    }

    private suspend fun Dialogue.recruits() {
        chatPlayer(quiz, "What are you doing?")
        chatNpc(angry, "What does it look like I'm doing? I'm training this useless bunch of recruits!")
        val fit = choice2("Oh yes, they look very fit.", true, "Oh yes, they look very weak.", false)
        chatPlayer(neutral, if (fit) "Oh yes, they look very fit." else "Oh yes, they look very weak.")
        if (fit) {
            chatNpc(angry, "They're nothing but a useless bunch of weaklings who won't survive an hour once they're out on patrol.")
        } else {
            chatNpc(angry, "Show some respect when you talk about my men, civilian!")
        }
        val opposite = if (fit) "weak" else "fit"
        if (choice2("Can I have some armour like yours?", true, "Oh yes, they look very $opposite.", false)) {
            recruitment()
        } else {
            val previous = if (fit) "fit" else "weak"
            chatNpc(angry, "Have you lost your mind? Earlier you said they looked $previous! Just go away and leave me alone.")
        }
    }

    private suspend fun Dialogue.recruitment() {
        chatPlayer(quiz, "Can I have some armour like yours?")
        chatNpc(neutral, "Only guards of Varrock can wear this armour.")
        chatPlayer(happy, "Well, I could become a guard of Varrock.")
        if (player.baseThievingLvl >= 31) {
            chatNpc(angry, "Oh yeah? I see you've got a Thieving level of ${player.baseThievingLvl}. That must give you quite a long criminal record.")
            chatPlayer(neutral, "I could repent of my evil ways if you like.")
        }
        chatNpc(quiz, "So you really want to be a guard, huh?")
        chatPlayer(happy, "Yes, that's right.")
        chatNpc(quiz, "Does it worry you that the life expectancy of a guard is about 30 seconds?")
        if (choice2("No, that doesn't worry me.", true, "Actually, forget it.", false)) {
            chatPlayer(neutral, "No, that doesn't worry me.")
            chatNpc(angry, "Bah! You just failed the intelligence test. Go away, civilian.")
        } else {
            chatPlayer(sad, "Actually, forget it.")
            chatNpc(angry, "Go away, coward.")
        }
    }
}
