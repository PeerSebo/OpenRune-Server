package org.rsmod.content.minigames.sorceressgarden.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.minigames.sorceressgarden.configs.osmanApprenticeUnlocked
import org.rsmod.content.minigames.sorceressgarden.scripts.apprenticeSendOff

suspend fun Dialogue.apprenticeFirstVisit(worldRepo: WorldRepository) {
    chatPlayer(quiz, "Hello. What are you doing?")
    chatNpc(
        sad,
        "Cleaning, cleaning, always cleaning. This apprenticeship isn't all that it was cracked " +
            "up to be.",
    )
    val intro =
        choice2(
            "Whose apprentice are you?",
            1,
            "Surely there must be upsides to the task?",
            2,
        )
    when (intro) {
        1 -> whoseApprentice()
        2 -> upsidesToTheTask(worldRepo)
    }
}

suspend fun Dialogue.apprenticeRepeatVisit(worldRepo: WorldRepository) {
    chatPlayer(happy, "Hey apprentice, do you want to try out your teleport skills again?")
    chatNpc(happy, "Okay, here goes - and remember, to return just drink from the fountain.")
    access.apprenticeSendOff(npc!!, worldRepo)
}

private suspend fun Dialogue.whoseApprentice() {
    chatNpc(
        neutral,
        "Oh, Aadeela, the sorceress upstairs, said she'd teach me magic, she did. And here I am " +
            "scrubbing floors without a spell to help me.",
    )
    chatPlayer(quiz, "I could cast a Water Blast or a Wind Blast spell to hurry things along if you'd like?")
    chatNpc(
        confused,
        "No, no, she'd kill me or worse if she knew I was using Magic to do chores. Her last " +
            "apprentice - well I'd rather not say.",
    )
    chatPlayer(quiz, "Oh go on, what happened to them?")
    chatNpc(sad, "They say she turned them into little spiders.")
    chatPlayer(neutral, "Oh, that's too bad. I had better leave you to it.")
}

private suspend fun Dialogue.upsidesToTheTask(worldRepo: WorldRepository) {
    chatPlayer(quiz, "Surely there must be upsides to the task?")
    chatNpc(
        sad,
        "Nope. Clean this, clean that. When I'm finished cleaning here I have to go help out in " +
            "the garden.",
    )
    chatPlayer(quiz, "What garden?")
    chatNpc(confused, "Oh, I shouldn't have told you.")
    val reply =
        choice2(
            "You're right, you shouldn't have.",
            1,
            "Oh, you can talk to me. I can see you're having a bad day.",
            2,
        )
    when (reply) {
        1 -> {
            chatPlayer(neutral, "You're right, you shouldn't have.")
            chatNpc(neutral, "Well, if you don't mind then, I'm busy and have to get back to work.")
            chatPlayer(neutral, "Don't let me keep you.")
        }
        2 -> badDay(worldRepo)
    }
}

private suspend fun Dialogue.badDay(worldRepo: WorldRepository) {
    chatPlayer(happy, "Oh, you can talk to me. I can see you're having a bad day.")
    chatNpc(sad, "You know you're right. Nobody listens to me.")
    val reply =
        choice2(
            "I can't blame them, all you do is whine.",
            1,
            "A sympathetic ear can do wonders.",
            2,
        )
    when (reply) {
        1 -> {
            chatPlayer(neutral, "I can't blame them, all you do is whine.")
            mesbox("The apprentice is clearly hurt and ignores you.")
        }
        2 -> sympatheticEar(worldRepo)
    }
}

private suspend fun Dialogue.sympatheticEar(worldRepo: WorldRepository) {
    chatPlayer(happy, "A sympathetic ear can do wonders.")
    chatNpc(
        happy,
        "Yes, if I just let my frustrations out, I'd feel a lot better. Now what was I saying?",
    )
    val reply =
        choice3(
            "I don't know. You were whining about something or other.",
            1,
            "To tell you the truth, I wasn't really listening.",
            2,
            "You mentioned something about the garden.",
            3,
        )
    when (reply) {
        1 -> {
            chatPlayer(neutral, "I don't know. You were whining about something or other.")
            mesbox("The apprentice is clearly hurt and ignores you.")
        }
        2 -> {
            chatPlayer(neutral, "To tell you the truth, I wasn't really listening.")
            mesbox("The apprentice is clearly hurt and ignores you.")
        }
        3 -> aboutTheGarden(worldRepo)
    }
}

private suspend fun Dialogue.aboutTheGarden(worldRepo: WorldRepository) {
    chatPlayer(quiz, "You mentioned something about the garden.")
    chatNpc(sad, "Oh yeah, that dreadful garden of hers.")
    chatPlayer(quiz, "Where is it?")
    chatNpc(neutral, "Oh, it's nowhere.")
    chatPlayer(confused, "What do you mean?")
    chatNpc(
        neutral,
        "Well it's here, but not really. You see the sorceress is trying out some new type of " +
            "compression magic.",
    )
    chatPlayer(quiz, "Oh, that sounds interesting - so how does it work?")
    chatNpc(
        confused,
        "It would take too long to explain and, to be honest, I don't really understand how it " +
            "works.",
    )
    chatPlayer(quiz, "Fair enough, but tell me, how do you get to the garden?")
    chatNpc(happy, "By magic! The sorceress did teach me one spell.")
    val reply =
        choice2(
            "Wow, cast the spell on me. It will be good Magic training for you.",
            1,
            "Oh, that's nice. Well it's been great talking to you.",
            2,
        )
    when (reply) {
        1 -> castTheSpell(worldRepo)
        2 -> chatPlayer(happy, "Oh, that's nice. Well it's been great talking to you.")
    }
}

private suspend fun Dialogue.castTheSpell(worldRepo: WorldRepository) {
    chatPlayer(happy, "Wow, cast the spell on me. It will be good Magic training for you.")
    if (!player.osmanApprenticeUnlocked) {
        chatNpc(neutral, "I can't do that now, I'm far too busy sweeping.")
        return
    }
    chatNpc(happy, "You wouldn't mind?")
    chatPlayer(happy, "Of course not. I'd be glad to help.")
    chatNpc(happy, "Okay, here goes! Remember, to return, just drink from the fountain.")
    access.apprenticeSendOff(npc!!, worldRepo)
}
