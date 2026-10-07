package org.rsmod.content.generic.npcs.security

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Nigel : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.deadman_nigel_regular") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) = startDialogue(npc) { greeting() }

    private suspend fun Dialogue.greeting() {
        chatPlayer(quiz, "Hey, who are you?")
        chatNpc(happy, "I'm Nigel. It is my job to tell you about Deadman Mode.")
        chatPlayer(quiz, "I have a question about Deadman Mode.")
        questions()
    }

    private suspend fun Dialogue.questions() {
        chatNpc(neutral, "What would you like to know?")
        firstPage()
    }

    private suspend fun Dialogue.firstPage() {
        val choice =
            choice5(
                "What is Deadman Mode?",
                1,
                "How do I play Deadman Mode?",
                2,
                "What happens when I die in Deadman Mode?",
                3,
                "What happens when I kill a player in Deadman Mode?",
                4,
                "More options...",
                5,
            )
        when (choice) {
            1 -> whatIsIt()
            2 -> howToPlay()
            3 -> unanswered("What happens when I die in Deadman Mode?")
            4 -> unanswered("What happens when I kill a player in Deadman Mode?")
            5 -> secondPage()
        }
    }

    private suspend fun Dialogue.secondPage() {
        val choice =
            choice3(
                "How can I protect my items in Deadman Mode?",
                1,
                "What are safe zones in Deadman Mode?",
                2,
                "That's all, thanks.",
                3,
            )
        when (choice) {
            1 -> unanswered("How can I protect my items in Deadman Mode?")
            2 -> unanswered("What are safe zones in Deadman Mode?")
            3 -> return
        }
    }

    private suspend fun Dialogue.whatIsIt() {
        chatPlayer(quiz, "What is Deadman Mode?")
        chatNpc(
            neutral,
            "Deadman Mode is an incredibly dangerous survival game Mode. Anyone can attack " +
                "anyone, anywhere. Death comes at a huge cost and killing other players can be " +
                "extremely lucrative.",
        )
        chatPlayer(quiz, "Lucrative, you say?")
        chatPlayer(neutral, "Wow, that is brutal!")
        chatNpc(neutral, "It is, but it is no simple task to kill someone in Deadman Mode.")
        chatNpc(
            neutral,
            "Very high level guards patrol every major city and bank. They will kill anyone who " +
                "has a skull. Getting yourself a kill is only half the battle.",
        )
        firstPage()
    }

    private suspend fun Dialogue.howToPlay() {
        chatPlayer(quiz, "How do I play Deadman Mode?")
        chatNpc(
            neutral,
            "You can log into Deadman Mode by selecting a Deadman Mode world from the world list " +
                "before you log in.",
        )
        chatPlayer(quiz, "So I don't need a new account to play Deadman Mode?")
        chatNpc(
            happy,
            "Nope! You can use your existing account on any Deadman world, providing you have " +
                "Membership.",
        )
        chatPlayer(quiz, "Do my stats and items transfer over to Deadman Mode?")
        chatNpc(
            neutral,
            "No, they do not. Your Deadman Mode progress is completely separate from normal Old " +
                "School worlds.",
        )
        firstPage()
    }

    private suspend fun Dialogue.unanswered(question: String) {
        chatPlayer(quiz, question)
        firstPage()
    }
}
