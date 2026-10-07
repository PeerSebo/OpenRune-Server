package org.rsmod.content.quest.area.goblinvillage

import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.ScriptContext

internal class GoblinDiplomacyDialogue(
    private val quest: Quest,
    private val random: GameRandom,
    private val cutscene: GoblinDiplomacyCutscene,
) {

    fun ScriptContext.register() {
        onOpNpc1(WartfaceNpc) { startDialogue(it.npc) { generals() } }
        onOpNpc1(BentnozeNpc) { startDialogue(it.npc) { generals() } }

        onOpNpc1(GrubfootParent) { startDialogue(it.npc) { grubfoot() } }
        for (leaf in GrubfootLeaves) {
            onOpNpc1(leaf) { startDialogue(it.npc) { grubfoot() } }
        }

        for (general in listOf(WartfaceNpc, BentnozeNpc)) {
            for (colour in GoblinMailColour.entries) {
                onOpNpcU(general, colour.obj) { startDialogue(it.npc) { offerMail(colour) } }
            }
        }
    }

    private suspend fun Dialogue.generals() {
        val stage = quest.getQuestStage(player)
        val wanted = GoblinMailColour.requestedAt(stage)
        when {
            stage == GoblinDiplomacyState.NotStarted -> theArgument()
            wanted != null -> inProgress(wanted)
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.theArgument() {
        bentnoze(neutral, "All goblins should wear red armour!")
        wartface(worried, "Not red! Red armour make you look fat.")
        bentnoze(happy, "Everything make YOU look fat!")
        wartface(angry, "Shut up!")
        bentnoze(happy, "Fatty!")
        wartface(angry, "SHUT UP!")
        bentnoze(quiz, "Even this human think you look fat! Don't you, human?")
        chatPlayer(confused, "Um...")

        val option =
            choice3(
                "Yes, he looks fat.",
                1,
                "No, he doesn't look fat.",
                2,
                "I'll leave you to it.",
                3,
            )

        when (option) {
            1 -> {
                chatPlayer(laugh, "Yes, he looks fat!")
                bentnoze(happy, "Ha ha! See, fatty? Even human think you fat!")
                wartface(angry, "Me not care what human think! Human ugly!")
                argumentMenu()
            }
            2 -> {
                chatPlayer(neutral, "No, he doesn't look fat.")
                bentnoze(angry, "Shut up human! Wartface fat and human stupid!")
                wartface(angry, "Shut up Bentnoze!")
                argumentMenu()
            }
            3 -> leaveYouToIt()
        }
    }

    private suspend fun Dialogue.argumentMenu() {
        while (true) {
            val option =
                choice4(
                    "Why are you arguing about the colour of your armour?",
                    1,
                    "Wouldn't you prefer peace?",
                    2,
                    "Do you want me to pick an armour colour for you?",
                    3,
                    "I'll leave you to it.",
                    4,
                )

            when (option) {
                1 -> whyArguing()
                2 -> preferPeace()
                3 -> {
                    pickAColour()
                    return
                }
                4 -> {
                    leaveYouToIt()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whyArguing() {
        chatPlayer(quiz, "Why are you arguing about the colour of your armour?")
        bentnoze(
            neutral,
            "We decide to celebrate goblin new century by changing colour of our armour. Brown " +
                "get boring after a bit. We want change.",
        )
        wartface(
            neutral,
            "Problem is, we can't agree on new colour. We think maybe we use old goblin tribe " +
                "colours, but Bentnoze from rubbish Thorobshuun tribe and me from much better " +
                "Garagorshuun tribe.",
        )
        bentnoze(
            angry,
            "Thorobshuun tribe colour is red! Way better than silly green of Garagorshuun tribe.",
        )
        wartface(angry, "Shut up!")

        val askCentury = choice2("What is the goblin new century?", true, "I see.", false)
        if (!askCentury) {
            chatPlayer(neutral, "I see.")
            return
        }

        chatPlayer(quiz, "What is the goblin new century?")
        bentnoze(
            neutral,
            "Goblin century mark year of battle on Plain of Mud. That when Big High War God give " +
                "goblin commandments.",
        )
        chatPlayer(quiz, "Big High War God?")
        wartface(
            neutral,
            "Big High War God is god for goblins. He take us and make us strong, not stupid like " +
                "humans.",
        )
        bentnoze(happy, "Me bet Big High War God would pick red armour.")
        wartface(angry, "Shut up! Big High War God would hate you!")
    }

    private suspend fun Dialogue.preferPeace() {
        chatPlayer(quiz, "Wouldn't you prefer peace?")
        wartface(
            neutral,
            "Goblins not so good at peace. Maybe peace be okay though, as long as it peace " +
                "wearing green armour.",
        )
        bentnoze(neutral, "But green too much like skin. Nearly make you look naked!")
    }

    private suspend fun Dialogue.pickAColour() {
        chatPlayer(confused, "Do you want me to pick an armour colour for you?")
        wartface(neutral, "Yes, as long as you pick green.")
        bentnoze(neutral, "No you have to pick red!")

        val option =
            choice4(
                "You should wear red.",
                1,
                "You should wear green.",
                2,
                "What about a different colour?",
                3,
                "I'll leave you to it.",
                4,
            )

        when (option) {
            1 -> pickRed()
            2 -> pickGreen()
            3 -> pickSomethingElse()
            4 -> leaveYouToIt()
        }
    }

    private suspend fun Dialogue.pickRed() {
        chatPlayer(neutral, "You should wear red.")
        bentnoze(happy, "See? Even stupid human think red best. Now we all wear red!")
        wartface(
            angry,
            "Human not know anything! If we wear red then whole village be ugly like YOU!",
        )
        bentnoze(neutral, "Go away human. You not helping.")
    }

    private suspend fun Dialogue.pickGreen() {
        chatPlayer(neutral, "You should wear green.")
        wartface(happy, "Green! We all wear green now, human has decided!")
        bentnoze(angry, "Why we have to do what human say? He not boss of us!")
        wartface(neutral, "No but he agree with me!")
        bentnoze(angry, "That prove you a filthy human-lover!")
        wartface(angry, "Me hate humans! This human just happen to be right!")
        bentnoze(neutral, "Go away human. You not know anything.")
    }

    private suspend fun Dialogue.pickSomethingElse() {
        chatPlayer(quiz, "What about a different colour?")
        bentnoze(confused, "That would mean me wrong... but at least Wartface<br>not right!")
        wartface(
            confused,
            "Well Bentnoze never been right in his life. Still, maybe<br>new colour good, but " +
                "will have to see armour before<br>decide.",
        )
        bentnoze(happy, "Human! You bring us armour in new colour!")

        val accept =
            choice2("Yes.", true, "No.", false, title = "Start the Goblin Diplomacy quest?")

        if (!accept) {
            chatPlayer(neutral, "Actually, I think I'll leave you to it.")
            return
        }

        chatPlayer(happy, "I can do that.")
        wartface(quiz, "What colour we try?")
        bentnoze(neutral, "Orange armour might be good.")

        quest.setQuestStage(access, GoblinDiplomacyState.WantOrange)

        wartface(neutral, "Yep, bring us orange armour.")
        howToGetIt()
    }

    private suspend fun Dialogue.howToGetIt() {
        chatPlayer(quiz, "How am I meant to get orange armour?")
        bentnoze(neutral, "Well first you get goblin armour...")
        wartface(neutral, "...and then you dye it orange!")
        bentnoze(happy, "Even human should be able to work that out!")

        while (true) {
            val option =
                choice3(
                    "Where do I get goblin armour?",
                    1,
                    "Where do I get dye?",
                    2,
                    "Okay, I'll be back soon.",
                    3,
                )

            when (option) {
                1 -> whereArmour()
                2 -> whereDye()
                3 -> {
                    chatPlayer(neutral, "Okay, I'll be back soon.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whereArmour() {
        chatPlayer(quiz, "Where do I get goblin armour?")
        bentnoze(neutral, "Goblins keep spare armour in crates round village.")
        wartface(neutral, "Look in crates, human. Not hard.")
        access.gobdipKnowsArmour = 1
    }

    private suspend fun Dialogue.whereDye() {
        chatPlayer(quiz, "Where do I get dye?")
        bentnoze(confused, "Dye? What is dye?")
        wartface(angry, "You know! D-Y-E! Stuff that make things change colour!")
        bentnoze(happy, "Oh, THAT dye. Me remember now.")
        wartface(neutral, "Old woman in Draynor Village make dye.")
        bentnoze(neutral, "Maybe you can get more dye from her?")
        access.gobdipKnowsDye = 1
    }

    private suspend fun Dialogue.inProgress(wanted: GoblinMailColour) {
        filler()

        while (true) {
            val handInLabel = "I have some ${wanted.spokenName} armour here."

            val option =
                if (access.carryingObj(wanted.obj)) {
                    choice4(
                        handInLabel,
                        1,
                        "Why are you arguing about the colour of your armour?",
                        2,
                        "Wouldn't you prefer peace?",
                        3,
                        "I'll leave you to it.",
                        4,
                    )
                } else {
                    choice3(
                        "Why are you arguing about the colour of your armour?",
                        2,
                        "Wouldn't you prefer peace?",
                        3,
                        "I'll leave you to it.",
                        4,
                    )
                }

            when (option) {
                1 -> {
                    chatPlayer(neutral, handInLabel)
                    cutscene.handIn(access, wanted)
                    return
                }
                2 -> whyArguing()
                3 -> preferPeace()
                4 -> {
                    leaveYouToIt()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.filler() {
        when (random.of(FillerVariants)) {
            0 -> fillerBiggerGeneral()
            else -> fillerWeBusy()
        }
    }

    private suspend fun Dialogue.fillerBiggerGeneral() {
        wartface(happy, "I tell all goblins in village to wear green armour now!")
        bentnoze(angry, "They not listen to you! I already tell them wear red<br>armour!")
        wartface(angry, "They listen to me not you! They know me bigger<br>general!")
        bentnoze(angry, "Me bigger general! They listen to me!")
        wartface(quiz, "Human! What colour armour they wearing out there?")
        chatPlayer(confused, "Half of them are wearing red and half of them green.")
        wartface(
            angry,
            "Shut up human! They wearing green armour really!<br>Human lying because she scared " +
                "of you!",
        )
        bentnoze(happy, "Human scared of me not you? Then you think me<br>bigger general!")
        wartface(confused, "What? Me mean...")
        wartface(angry, "Shut up! Me bigger general!")
    }

    private suspend fun Dialogue.fillerWeBusy() {
        bentnoze(angry, "Red armour best.")
        wartface(angry, "No it has to be green!")
        bentnoze(neutral, "Go away human, we busy.")
    }

    private suspend fun Dialogue.offerMail(colour: GoblinMailColour) {
        val wanted = GoblinMailColour.requestedAt(quest.getQuestStage(player))

        if (wanted == null) {
            chatPlayer(quiz, "What do you think of this colour?")
            bentnoze(neutral, "We not care about armour colour any more, human.")
            return
        }

        if (wanted == colour) {
            chatPlayer(neutral, "I have some ${colour.spokenName} armour here.")
            cutscene.handIn(access, colour)
            return
        }

        chatPlayer(quiz, "What do you think of this colour?")
        bentnoze(neutral, "That wrong colour.")
        wartface(angry, "We tell you get ${wanted.spokenName} armour.")
    }

    private suspend fun Dialogue.afterQuest() {
        wartface(happy, "Brown armour still best colour.")
        bentnoze(neutral, "Me agree. For once.")
    }

    private suspend fun Dialogue.grubfoot() {
        if (quest.getQuestStage(player) != GoblinDiplomacyState.WantBrown) {
            chatNpc(neutral, "Me just goblin. Me not know anything.")
            return
        }

        chatNpc(sad, "Me not like this blue colour.")
        chatPlayer(quiz, "Why not?")
        chatNpc(sad, "Me not know. It just make me feel...")
        chatPlayer(quiz, "Makes you feel blue?")
        chatNpc(sad, "Makes me feel kind of sad.")
        wartface(angry, "Shut up Grubfoot!")
    }

    private suspend fun Dialogue.leaveYouToIt() {
        chatPlayer(neutral, "I'll leave you to it.")
    }

    private suspend fun Dialogue.wartface(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Wartface", WartfaceNpc, mesanim, text)

    private suspend fun Dialogue.bentnoze(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Bentnoze", BentnozeNpc, mesanim, text)

    private companion object {
        const val WartfaceNpc = "npc.general_wartface_green"
        const val BentnozeNpc = "npc.general_bentnoze_red"

        const val GrubfootParent = "npc.catwalk_goblin"

        val GrubfootLeaves =
            listOf(
                "npc.catwalk_goblin_brown",
                "npc.catwalk_goblin_orange",
                "npc.catwalk_goblin_blue",
            )

        const val FillerVariants = 2
    }
}
