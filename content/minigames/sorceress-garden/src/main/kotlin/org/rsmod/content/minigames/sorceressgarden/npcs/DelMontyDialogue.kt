package org.rsmod.content.minigames.sorceressgarden.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess

suspend fun Dialogue.delMontyChat() {
    if (!access.wearingCatspeakAmulet()) {
        chatPlayer(happy, "Hey kitty!")
        chatNpc(angry, "Hiss!")
        return
    }

    chatNpc(quiz, "Hello, no-fur. What are you doing in my mistress's garden?")
    val intro =
        choice2(
            "Looking for sq'irks.",
            1,
            "Nothing much.",
            2,
        )
    when (intro) {
        1 -> {
            chatPlayer(neutral, "Looking for sq'irks.")
            chatNpc(
                happy,
                "If it's sq'irks you're after then you've come to the right place. We've got " +
                    "four seasons' worth of them.",
            )
            questionMenu()
        }
        2 -> {
            chatPlayer(bored, "Nothing much.")
            chatNpc(bored, "Yawn! Nice talking to you then, no-fur.")
        }
    }
}

private suspend fun Dialogue.questionMenu() {
    while (true) {
        val question =
            choice5(
                "What are the creatures inside the gardens?",
                1,
                "How do you get into the seasonal gardens?",
                2,
                "How do I get out of here?",
                3,
                "Who are you?",
                4,
                "Thanks for your help.",
                5,
            )
        when (question) {
            1 -> {
                chatPlayer(quiz, "What are the creatures inside the gardens?")
                chatNpc(quiz, "Oh, you mean the gardeners?")
                chatPlayer(neutral, "The strange, floaty creatures.")
                chatNpc(
                    neutral,
                    "Yes, the gardeners. Don't let them see you or they'll teleport you out " +
                        "here. They're very protective of their crops.",
                )
            }
            2 -> {
                chatPlayer(quiz, "How do you get into the seasonal gardens?")
                chatNpc(
                    neutral,
                    "I get in through gaps in the hedge. You are a little too big to squeeze " +
                        "through. I think you'll have to use the gates.",
                )
                chatNpc(
                    neutral,
                    "I think the gates are locked, so you'll have to have good Thieving skills " +
                        "to get in.",
                )
            }
            3 -> {
                chatPlayer(quiz, "How do I get out of here?")
                chatNpc(neutral, "Take a drink from the fountain.")
            }
            4 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(happy, "Del-Monty the cat, at your service.")
                chatPlayer(
                    quiz,
                    "Are you a famous adventurer who was turned into a cat by a vindictive mage?",
                )
                chatNpc(
                    neutral,
                    "No; as I said, I'm Del-Monty the cat, connoisseur of exotic fruits.",
                )
                chatPlayer(quiz, "In that case, can you tell me anything about sq'irks?")
                chatNpc(
                    happy,
                    "But of course! Their juice is an excellent source of energy for runners, " +
                        "and in the riper varieties they are known to heighten one's Thieving " +
                        "abilities.",
                )
            }
            else -> {
                chatPlayer(happy, "Thanks for your help.")
                return
            }
        }
        chatPlayer(quiz, "Thanks, I have another question though.")
    }
}

private fun ProtectedAccess.wearingCatspeakAmulet(): Boolean =
    worn.any { CatspeakAmulets.contains(it?.id ?: -1) }

private val CatspeakAmulets: List<Int> =
    listOf(
        "obj.ics_little_amulet_of_catspeak".asRSCM(RSCMType.OBJ),
        "obj.twocats_amuletofcatspeak".asRSCM(RSCMType.OBJ),
    )
