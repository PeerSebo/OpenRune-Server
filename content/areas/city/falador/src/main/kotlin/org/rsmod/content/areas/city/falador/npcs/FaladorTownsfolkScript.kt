package org.rsmod.content.areas.city.falador.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorTownsfolkScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.partyroom_pete") { startDialogue(it.npc) { partyPete(it.npc) } }
        onOpNpc1("npc.sir_vyvin") { startDialogue(it.npc) { sirVyvin() } }
        onOpNpc1("npc.aluft_gnome_diplomat_falador") { startDialogue(it.npc) { spanfipple() } }
        onOpNpc1("npc.falador_man1") { startDialogue(it.npc) { drunkenMan() } }
        onOpNpc1("npc.falador_woman") { startDialogue(it.npc) { cecilia() } }
        onOpNpc1("npc.falador_workman_fat") { startDialogue(it.npc) { workman() } }
        onOpNpc1("npc.falador_workman_young") { startDialogue(it.npc) { apprenticeWorkman() } }
    }

    private suspend fun Dialogue.partyPete(npc: Npc) {
        chatNpc(happy, "Hi! I'm Party Pete. Welcome to the Party Room!")
        when (
            choice5(
                "So what's this room for?",
                1,
                "What's the big lever over there for?",
                2,
                "What's the gold chest for?",
                3,
                "Have you always been here in Falador?",
                4,
                "I wanna party!",
                5,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "So what's this room for?")
                chatNpc(happy, "This room is for partying the night away!")
                chatPlayer(quiz, "How do you have a party in Gielinor?")
                chatNpc(happy, "Get a few mates round, get the beers in and have fun!")
                chatNpc(happy, "Some players organise parties so keep an eye open!")
                chatPlayer(laugh, "Woop! Thanks Pete!")
            }
            2 -> {
                chatPlayer(quiz, "What's the big lever over there for?")
                chatNpc(happy, "Simple. With the lever you can do some fun stuff.")
                chatPlayer(quiz, "What kind of stuff?")
                chatNpc(
                    happy,
                    "A balloon drop costs 1000 gold. For this you get 200 balloons dropped across the " +
                        "whole of the party room. You can then have fun popping the balloons! If there " +
                        "are items in the Party Drop Chest they will be inside",
                )
                chatNpc(
                    happy,
                    "the balloons! For 500 gold you can summon the Party Room Knights who will dance " +
                        "for your delight.",
                )
                chatNpc(laugh, "Their singing isn't a delight though!")
            }
            3 -> {
                chatPlayer(quiz, "What's the gold chest for?")
                chatNpc(
                    happy,
                    "Any items that are in the chest will be dropped inside the balloons when you pull " +
                        "the lever!",
                )
                chatPlayer(happy, "Cool! Sounds like a fun way to do a drop party!")
                chatNpc(happy, "Exactly!")
                chatNpc(
                    neutral,
                    "A word of warning though. Any items that you put into the chest can't be taken " +
                        "out again and it costs 1000 gold pieces for each balloon drop.",
                )
            }
            4 -> {
                chatPlayer(quiz, "Have you always been here in Falador?")
                chatNpc(
                    neutral,
                    "We used to be in Seers' Village, far to the west, but we had to move - the seers " +
                        "were complaining about the noise level, and the knights of Camelot got it into " +
                        "their heads that the Party Room knights were making fun of",
                )
                chatNpc(neutral, "them.")
                chatNpc(
                    happy,
                    "We're doing well here, though. The people of Falador are happy we're here, and " +
                        "we've hardly ever had the White Knights telling us to keep the noise down.",
                )
                chatNpc(laugh, "We're going to turn Falador into the party capital of Gielinor!")
            }
            else -> {
                chatPlayer(happy, "I wanna party!")
                chatNpc(happy, "I've won the Dance Trophy at the Kandarin Ball three years in a trot!")
                chatPlayer(happy, "Show me your moves Pete!")
                npc.anim(SEQ_DANCE)
                access.delay(DANCE_CYCLES)
                npc.anim(SEQ_CHEER)
                access.delay(CHEER_CYCLES)
                npc.anim(SEQ_BOW)
            }
        }
    }

    private suspend fun Dialogue.sirVyvin() {
        chatPlayer(happy, "Hello.")
        chatNpc(neutral, "Greetings traveller.")
        when (
            choice3(
                "Do you have anything to trade?",
                1,
                "Why are there so many knights in this city?",
                2,
                "Can I just distract you for a minute?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Do you have anything to trade?")
                chatNpc(neutral, "No, I'm sorry.")
            }
            2 -> {
                chatPlayer(quiz, "Why are there so many knights in this city?")
                chatNpc(
                    neutral,
                    "We are the White Knights of Falador. We are the most powerful order of knights in " +
                        "the land. We are helping the king Vallance rule the kingdom as he is getting old " +
                        "and tired.",
                )
            }
            else -> {
                chatPlayer(
                    happy,
                    "Can I just talk to you very slowly for a few minutes, while I distract you, so " +
                        "that my friend over there can do something while you're busy being distracted " +
                        "by me?",
                )
                chatNpc(confused, "... ...what?")
                chatNpc(
                    confused,
                    "I'm... not sure what you're asking me... you want to join the White Knights?",
                )
                chatPlayer(happy, "Nope. I'm just trying to distract you.")
                chatNpc(confused, "... ...you are very odd.")
                chatPlayer(happy, "So can I distract you some more?")
                chatNpc(confused, "... ...I don't think I want to talk to you any more.")
                chatPlayer(happy, "Ok. My work here is done. 'Bye!")
            }
        }
    }

    private suspend fun Dialogue.spanfipple() {
        chatNpc(neutral, "It's all very bright round here, isn't it?")
        chatPlayer(neutral, "Well, it is the White Knights' Castle.")
        chatNpc(
            neutral,
            "I think it would all look better in a nice dark green. At least then I wouldn't be " +
                "squinting all the time.",
        )
        chatPlayer(
            neutral,
            "Yes, but then they'd have to become the Dark Green Knights. Doesn't really have the same " +
                "ring to it.",
        )
        chatNpc(angry, "Bah, humans have no sense of style...")
    }

    private suspend fun Dialogue.drunkenMan() {
        chatPlayer(happy, "Hello.")
        chatNpc(drunk, "... whassup?")
        chatPlayer(quiz, "Are you alright?")
        chatNpc(drunk, "... see... two of you... why there two of you?")
        chatPlayer(neutral, "There's only one of me, friend.")
        chatNpc(drunk, "... no, two of you... you can't count... ... maybe you drunk too much...")
        chatPlayer(neutral, "Whatever you say, friend.")
        chatNpc(drunk, "... giant hairy cabbages...")
    }

    private suspend fun Dialogue.cecilia() {
        chatNpc(
            happy,
            "Greetings! Have you come to gaze in rapture at the natural beauty of Falador's parkland?",
        )
        chatPlayer(neutral, "Um, yes, very nice. Lots of... trees and stuff.")
        chatNpc(laugh, "Trees! I do so love trees! And flowers! And squirrels!")
        chatPlayer(worried, "Sorry, I have a strange urge to be somewhere else.")
        chatNpc(happy, "Come back to me soon and we can talk again about trees!")
        chatPlayer(bored, "...")
    }

    private suspend fun Dialogue.workman() {
        chatPlayer(happy, "Hiya.")
        chatNpc(angry, "What do you want? I've got work to do!")
        chatPlayer(quiz, "Can you teach me anything?")
        chatNpc(
            angry,
            "No - I've got one lousy apprentice already, and that's quite enough hassle! Go away!",
        )
    }

    private suspend fun Dialogue.apprenticeWorkman() {
        chatPlayer(happy, "Hiya.")
        chatNpc(
            neutral,
            "Sorry, I haven't got time to chat. We've only just finished a colossal order of furniture " +
                "for the Varrock area, and already there's more work coming in.",
        )
        chatPlayer(quiz, "Varrock?")
        chatNpc(neutral, "Yeah, the Council's had it redecorated.")
        chatNpcSpecific(
            "Workman",
            "npc.falador_workman_fat",
            angry,
            "- Oi - stop gabbing and get that chair finished!",
        )
        chatNpc(worried, "You'd better let me get on with my work.")
        chatPlayer(neutral, "Ok, bye.")
    }

    private companion object {
        const val SEQ_DANCE = "seq.emote_dance"
        const val SEQ_CHEER = "seq.emote_cheer"
        const val SEQ_BOW = "seq.emote_bow"
        const val DANCE_CYCLES = 5
        const val CHEER_CYCLES = 3
    }
}
