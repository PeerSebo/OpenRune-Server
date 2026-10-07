package org.rsmod.content.quest.area.falador

import dev.openrune.util.Wearpos
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.ShieldOfArrav
import org.rsmod.content.quest.area.varrock.reldoMet
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.ScriptContext

internal class TheKnightsSwordDialogue(
    private val quest: Quest,
    private val shieldOfArrav: ShieldOfArrav,
) {

    fun ScriptContext.register() {
        onOpNpc1(TheKnightsSwordRefs.Squire) { startDialogue(it.npc) { squire() } }
        onOpNpc1(TheKnightsSwordRefs.Thurgo) { startDialogue(it.npc) { thurgo() } }
        onOpNpc1(TheKnightsSwordRefs.Reldo) { startDialogue(it.npc) { reldo() } }
    }

    private suspend fun Dialogue.squire() {
        when (quest.getQuestStage(player)) {
            TheKnightsSwordStage.NotStarted -> squireOpener()
            TheKnightsSwordStage.ThurgoWantsPicture -> squireNamesThePortrait()
            TheKnightsSwordStage.Stage5PortraitNamed -> squirePortraitProgress()
            TheKnightsSwordStage.ThurgoWantsMaterials -> squireHandIn()
            TheKnightsSwordStage.Complete -> squireAfterwards()
            else -> squireStillLooking()
        }
    }

    private suspend fun Dialogue.squireOpener() {
        chatNpc(neutral, "Hello. I am the squire to Sir Vyvin.")

        val squireForMe =
            choice2(
                "And how is life as a squire?",
                false,
                "Wouldn't you prefer to be a squire for me?",
                true,
            )

        if (squireForMe) {
            chatPlayer(quiz, "Wouldn't you prefer to be a squire for me?")
            chatNpc(neutral, "No, sorry, I'm loyal to Sir Vyvin.")
            return
        }

        chatPlayer(quiz, "And how is life as a squire?")
        chatNpc(
            sad,
            "Well, Sir Vyvin is a good guy to work for, however, I'm in a spot of trouble today. " +
                "I've gone and lost Sir Vyvin's sword!",
        )

        val option =
            choice3(
                "Do you know where you lost it?",
                1,
                "I can make a new sword if you like...",
                2,
                "Is he angry?",
                3,
            )

        when (option) {
            1 -> squireWhereLost()
            2 -> squireOfferToMakeOne()
            3 -> squireIsHeAngry()
        }
    }

    private suspend fun Dialogue.squireWhereLost() {
        chatPlayer(quiz, "Do you know where you lost it?")
        chatNpc(sad, "Well now, if I knew THAT it wouldn't be lost, would it?")

        val askVague =
            choice2("Well, do you know the VAGUE AREA you lost it in?", true, "Fair enough.", false)

        if (!askVague) {
            chatPlayer(neutral, "Fair enough.")
            return
        }

        chatPlayer(quiz, "Well, do you know the VAGUE AREA you lost it in?")
        chatNpc(
            sad,
            "I was carrying it back from Lumbridge, where it had been taken for a ceremony. It " +
                "could be anywhere between here and there.",
        )
        chatNpc(sad, "I've looked everywhere I can think of and there's no sign of it.")

        squireOfferToMakeOne()
    }

    private suspend fun Dialogue.squireIsHeAngry() {
        chatPlayer(quiz, "Is he angry?")
        chatNpc(
            sad,
            "He doesn't know yet, and I'd very much like to keep it that way for as long as " +
                "possible!",
        )

        squireOfferToMakeOne()
    }

    private suspend fun Dialogue.squireOfferToMakeOne() {
        chatPlayer(neutral, "I can make a new sword if you like...")
        chatNpc(sad, "Thanks for the offer. I'd be surprised if you could though.")
        chatNpc(
            sad,
            "The thing is, this sword is a family heirloom. It has been passed down through " +
                "Vyvin's family for five generations! It was originally made by the Imcando " +
                "dwarves, who were",
        )
        chatNpc(
            sad,
            "a particularly skilled tribe of dwarven smiths. I doubt anyone could make it in the " +
                "style they do.",
        )

        val askDwarves =
            choice2(
                "So would these dwarves make another one?",
                true,
                "Well I hope you find it soon.",
                false,
            )

        if (!askDwarves) {
            chatPlayer(neutral, "Well I hope you find it soon.")
            chatNpc(sad, "Yes, me too. Sir Vyvin will be furious when he finds out.")
            return
        }

        chatPlayer(quiz, "So would these dwarves make another one?")
        chatNpc(
            sad,
            "I'm not a hundred percent sure the Imcando tribe exists anymore. I should think " +
                "Reldo, the palace librarian in Varrock, will know; he has done a lot of research " +
                "on the races of Gielinor.",
        )
        chatNpc(
            quiz,
            "I don't suppose you could try and track down the Imcando dwarves for me? I've got so " +
                "much work to do...",
        )

        offerQuest()
    }

    private suspend fun Dialogue.offerQuest() {
        val lowCombat = player.combatLevel < TheKnightsSwordRefs.RecommendedCombatLevel
        val lowMining = access.statBase("stat.mining") < TheKnightsSwordRefs.RequiredMining

        val skillsClause =
            "one or more of your skill levels are lower than what is required to fully complete it."
        val combatClause =
            "your combat level is lower than the recommended level of " +
                "${TheKnightsSwordRefs.RecommendedCombatLevel}."

        when {
            lowMining && lowCombat ->
                mesbox(
                    "Before starting this quest, be aware that $skillsClause Your combat level is " +
                        "also lower than the recommended level of " +
                        "${TheKnightsSwordRefs.RecommendedCombatLevel}."
                )
            lowMining -> mesbox("Before starting this quest, be aware that $skillsClause")
            lowCombat -> mesbox("Before starting this quest, be aware that $combatClause")
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start The Knight's Sword quest?")

        if (!accept) {
            chatPlayer(neutral, "No. I've got lots of mining work to do.")
            chatNpc(sad, "Oh man... I'm in such trouble... What am I going to do?")
            return
        }

        quest.setQuestStage(access, TheKnightsSwordStage.Started)

        chatPlayer(neutral, "Ok, I'll give it a go.")
        chatNpc(
            happy,
            "Thank you very much! As I say, the best place to start should be with Reldo...",
        )
    }

    private suspend fun Dialogue.squireStillLooking() {
        chatNpc(quiz, "So how are you doing getting a sword?")
        chatPlayer(neutral, "I'm looking for Imcando dwarves to help me.")
        chatNpc(confused, "Please try and find them quickly - I am scared Sir Vyvin will find out!")
    }

    private suspend fun Dialogue.squireNamesThePortrait() {
        chatNpc(quiz, "So how are you doing getting a sword?")
        chatPlayer(
            happy,
            "I've found an Imcando dwarf but he needs a picture of the sword before he can make it.",
        )
        chatNpc(
            confused,
            "A picture eh? Hmmm.... The only one I can think of is in a small portrait of Sir " +
                "Vyvin's father... Sir Vyvin keeps it in a cupboard in his room I think.",
        )

        quest.setQuestStage(access, TheKnightsSwordStage.Stage5PortraitNamed)

        chatPlayer(neutral, "Ok, I'll try and get that then.")
        chatNpc(confused, "Please don't let him catch you! He MUSTN'T know what happened!")
    }

    private suspend fun Dialogue.squirePortraitProgress() {
        chatNpc(quiz, "So how are you doing getting a sword?")

        if (!access.carries(TheKnightsSwordRefs.Portrait)) {
            chatPlayer(neutral, "I didn't get the picture yet.")
            chatNpc(
                confused,
                "Please try and get it quickly - I am scared Sir Vyvin will find out!",
            )
            return
        }

        chatPlayer(happy, "I have the picture. I'll just take it to the dwarf now!")
        chatNpc(confused, "Please hurry!")
    }

    private suspend fun Dialogue.squireHandIn() {
        val carried = access.carries(TheKnightsSwordRefs.FaladianSword)
        val wielded =
            player.worn[Wearpos.RightHand.slot]?.isType(TheKnightsSwordRefs.FaladianSword) == true

        if (!carried) {
            if (wielded) {
                chatNpc(quiz, "So can you un-equip it and hand it over to me now please?")
                return
            }
            chatNpc(quiz, "So how are you doing getting a sword?")
            chatPlayer(neutral, "The dwarf is still working on it.")
            chatNpc(
                confused,
                "Please try and get it quickly - I am scared Sir Vyvin will find out!",
            )
            return
        }

        chatPlayer(happy, "I have retrieved your sword for you.")
        chatNpc(
            happy,
            "Thank you, thank you, thank you! I was seriously worried I would have to own up to " +
                "Sir Vyvin!",
        )
        mesbox("You give the sword to the squire.")

        if (access.invDel(access.inv, TheKnightsSwordRefs.FaladianSword, 1).failure) {
            return
        }

        quest.completeQuest(access)
    }

    private suspend fun Dialogue.squireAfterwards() {
        chatNpc(happy, "Thanks again! Sir Vyvin never suspected a thing.")
    }

    private suspend fun Dialogue.thurgo() {
        val stage = quest.getQuestStage(player)

        when {
            stage >= TheKnightsSwordStage.Complete -> {
                chatPlayer(happy, "Thanks for making that sword for me!")
                chatNpc(happy, "You're welcome - thanks for the pie!")
            }
            stage >= TheKnightsSwordStage.ThurgoWantsMaterials -> thurgoMaterialsMenu()
            stage >= TheKnightsSwordStage.ThurgoWantsPicture -> thurgoPictureMenu()
            stage >= TheKnightsSwordStage.Stage3PieEaten -> thurgoSwordMenu()
            else -> thurgoPieMenu(stage)
        }
    }

    private suspend fun Dialogue.thurgoPieMenu(stage: Int) {
        val canOfferPie =
            stage >= TheKnightsSwordStage.Started && access.carries(TheKnightsSwordRefs.Pie)

        val option =
            if (canOfferPie) {
                choice3(
                    "Are you an Imcando dwarf? I need a special sword.",
                    1,
                    "Would you like a redberry pie?",
                    2,
                    "What is that cape you're wearing?",
                    3,
                )
            } else {
                choice2(
                    "Are you an Imcando dwarf? I need a special sword.",
                    1,
                    "What is that cape you're wearing?",
                    3,
                )
            }

        when (option) {
            1 -> thurgoDenial()
            2 -> givePie(stage)
            3 -> thurgoCape()
        }
    }

    private suspend fun Dialogue.thurgoDenial() {
        chatPlayer(quiz, "Are you an Imcando dwarf? I need a special sword.")
        chatNpc(neutral, "I don't talk about that sort of thing anymore. I'm getting old.")
    }

    private suspend fun Dialogue.thurgoCape() {
        chatPlayer(quiz, "What is that cape you're wearing?")
        chatNpc(neutral, "Just an old thing. It keeps the wind off while I work.")
    }

    private suspend fun Dialogue.givePie(stage: Int) {
        chatPlayer(quiz, "Would you like a redberry pie?")
        mesbox("You see Thurgo's eyes light up.")
        chatNpc(
            happy,
            "I'd never say no to a redberry pie! We Imcando dwarves love them - they're GREAT!",
        )

        if (access.invDel(access.inv, TheKnightsSwordRefs.Pie, 1).failure) {
            return
        }

        try {
            mesboxNp("You hand over the pie.    ")

            if (stage in TheKnightsSwordStage.Started until TheKnightsSwordStage.Stage3PieEaten) {
                quest.setQuestStage(access, TheKnightsSwordStage.Stage3PieEaten)
            }

            delay(PieBeat2Gap)

            mesboxNp("You hand over the pie. Thurgo eats the pie.  ")
            access.soundSynth(EatSound)

            delay(PieBeat3Gap)

            mesbox("You hand over the pie. Thurgo eats the pie. Thurgo pats his stomach.")
        } finally {
            access.ifCloseSub("interface.messagebox")
        }

        chatNpc(
            happy,
            "By Guthix! THAT was good pie! Anyone who makes pie like THAT has got to be alright!",
        )
    }

    private suspend fun Dialogue.thurgoSwordMenu() {
        val askSword =
            choice2(
                "Can you make a special sword for me?",
                true,
                "What is that cape you're wearing?",
                false,
            )

        if (!askSword) {
            thurgoCape()
            return
        }

        chatPlayer(happy, "Can you make a special sword for me?")
        chatNpc(
            neutral,
            "Well, after bringing me my favorite food I guess I should give it a go. What sort of " +
                "sword is it?",
        )
        chatPlayer(
            neutral,
            "I need you to make a sword for one of Falador's knights. He had one which was passed " +
                "down through five generations, but his squire has lost it.",
        )
        chatPlayer(quiz, "So we need an identical one to replace it.")
        chatNpc(
            neutral,
            "A knight's sword eh? Well, I'd need to know exactly how it looked before I could make " +
                "a new one.",
        )

        quest.setQuestStage(access, TheKnightsSwordStage.ThurgoWantsPicture)

        chatNpc(
            neutral,
            "All the Faladian knights used to have swords with unique designs according to their " +
                "position. Could you bring me a picture or something?",
        )
        chatPlayer(neutral, "I'll go and ask his squire and see if I can find one.")
    }

    private suspend fun Dialogue.thurgoPictureMenu() {
        val askSword =
            choice2("About that sword...", true, "What is that cape you're wearing?", false)

        if (!askSword) {
            thurgoCape()
            return
        }

        chatPlayer(happy, "About that sword...")
        chatNpc(quiz, "Have you got a picture of the sword for me yet?")

        if (!access.carries(TheKnightsSwordRefs.Portrait)) {
            chatPlayer(neutral, "Sorry, not yet.")
            chatNpc(neutral, "Well, come back when you do.")
            return
        }

        chatPlayer(neutral, "I have found a picture of the sword I would like you to make.")
        mesbox("You give the portrait to Thurgo. Thurgo studies the portrait.")

        if (access.invDel(access.inv, TheKnightsSwordRefs.Portrait, 1).failure) {
            return
        }

        quest.setQuestStage(access, TheKnightsSwordStage.ThurgoWantsMaterials)

        chatNpc(
            neutral,
            "You'll need to get me some stuff to make this. I'll need two iron bars to make the " +
                "sword, to start with. I'll also need an ore called blurite.",
        )
        chatNpc(
            neutral,
            "Blurite is useless for making actual weapons, except crossbows, but I'll need some " +
                "as decoration for the hilt.",
        )
        chatNpc(
            neutral,
            "It is a fairly rare ore. The only place I know to get it is under this cliff here, " +
                "but it is guarded by a very powerful ice giant.",
        )
        chatNpc(
            neutral,
            "Most of the rocks in that cliff are pretty useless, and don't contain much of " +
                "anything, but there's DEFINITELY some blurite in there.",
        )
        chatNpc(neutral, "You'll need a little bit of mining experience to be able to find it.")
        chatPlayer(neutral, "Okay. I'll go and find them then.")
    }

    private suspend fun Dialogue.thurgoMaterialsMenu() {
        val askSword =
            choice2(
                "Can you make that replacement sword now?",
                true,
                "What is that cape you're wearing?",
                false,
            )

        if (!askSword) {
            thurgoCape()
            return
        }

        chatPlayer(happy, "Can you make that replacement sword now?")
        chatNpc(quiz, "How are you doing finding those sword materials?")

        val bars = access.invTotal(access.inv, TheKnightsSwordRefs.IronBar)
        val ore = access.invTotal(access.inv, TheKnightsSwordRefs.BluriteOre)
        val hasBars = bars >= TheKnightsSwordRefs.IronBarsNeeded
        val hasOre = ore >= 1

        if (!hasBars || !hasOre) {
            thurgoMissingMaterials(hasBars, hasOre)
            return
        }

        chatPlayer(neutral, "I have them right here.")
        forgeSword()
        chatPlayer(happy, "Thank you very much!")
        chatNpc(happy, "Just remember to call in with more pie some time!")
    }

    private suspend fun Dialogue.thurgoMissingMaterials(hasBars: Boolean, hasOre: Boolean) {
        when {
            !hasBars && !hasOre -> {
                chatPlayer(sad, "I don't have any of them yet.")
                chatNpc(
                    neutral,
                    "Two iron bars and a lump of blurite ore, that's all I need. The blurite is " +
                        "in the rocks under this cliff, guarded by a very powerful ice giant.",
                )
            }
            hasBars -> {
                chatPlayer(sad, "I don't have any blurite ore yet.")
                chatNpc(
                    neutral,
                    "The only place I know to get it is under this cliff here, but it is guarded " +
                        "by a very powerful ice giant.",
                )
            }
            else -> {
                chatPlayer(sad, "I don't have two iron bars.")
                chatNpc(
                    neutral,
                    "You'll not get far without them. Iron is easy enough to come by - it's the " +
                        "blurite under this cliff that's the hard part.",
                )
            }
        }
    }

    // Remove all materials in one transaction so a failed forge cannot consume a partial recipe.
    private suspend fun Dialogue.forgeSword() {
        val barsDeleted =
            access.invDel(
                access.inv,
                TheKnightsSwordRefs.IronBar,
                TheKnightsSwordRefs.IronBarsNeeded,
            )
        if (barsDeleted.failure) {
            return
        }

        val oreDeleted = access.invDel(access.inv, TheKnightsSwordRefs.BluriteOre, 1)
        if (oreDeleted.failure) {
            access.invAdd(
                access.inv,
                TheKnightsSwordRefs.IronBar,
                TheKnightsSwordRefs.IronBarsNeeded,
            )
            return
        }

        if (access.invAdd(access.inv, TheKnightsSwordRefs.FaladianSword, 1).failure) {
            access.invAdd(access.inv, TheKnightsSwordRefs.BluriteOre, 1)
            access.invAdd(
                access.inv,
                TheKnightsSwordRefs.IronBar,
                TheKnightsSwordRefs.IronBarsNeeded,
            )
            return
        }

        mesbox("You give the blurite ore and iron bars to Thurgo. Thurgo makes you a sword.")
    }

    private suspend fun Dialogue.reldo() {
        if (access.reldoMet == 0) {
            access.reldoMet = 1
            chatNpc(neutral, "Hello, stranger.")
            chatPlayer(quiz, "Hello there! The name's ${player.displayName}. Who are you?")
            chatNpc(neutral, "My name is Reldo. I'm the palace librarian.")
            chatPlayer(neutral, "Ah, that explains why you're in the library then.")
            chatNpc(
                neutral,
                "Well, truth be told, I would probably be here even if it wasn't where I worked. " +
                    "I very much like reading, not to mention sharing what I learn with all who " +
                    "are interested.",
            )
            chatPlayer(neutral, "Sounds like a very noble goal!")
        } else {
            chatNpc(neutral, "Hello there, ${player.displayName}.")
        }

        val soaOption = shieldOfArrav.reldoQuestOption(access)
        val imcando =
            quest.getQuestStage(player) in
                TheKnightsSwordStage.Started until TheKnightsSwordStage.Complete

        val option =
            if (imcando) {
                choice4(
                    soaOption,
                    1,
                    "What do you know about the Imcando dwarves?",
                    2,
                    "Do you have anything to trade?",
                    3,
                    "I'd better get going.",
                    4,
                )
            } else {
                choice3(
                    soaOption,
                    1,
                    "Do you have anything to trade?",
                    3,
                    "I'd better get going.",
                    4,
                )
            }

        when (option) {
            1 -> shieldOfArrav.reldoQuestBranch(this)
            2 -> reldoOnTheImcando()
            3 -> {
                chatPlayer(quiz, "Do you have anything to trade?")
                chatNpc(neutral, "I'm a librarian, not a merchant. Books are for reading.")
            }
            4 -> chatPlayer(neutral, "I'd better get going.")
        }
    }

    private suspend fun Dialogue.reldoOnTheImcando() {
        chatPlayer(quiz, "What do you know about the Imcando dwarves?")
        chatNpc(neutral, "The Imcando dwarves, you say? Let's see...")
        chatNpc(
            neutral,
            "Ah, yes... For many hundreds of years, they were the world's most skilled smiths. " +
                "They used secret smithing knowledge passed down from generation to generation.",
        )
        chatNpc(
            neutral,
            "Unfortunately, about a century ago, the once thriving group was wiped out during the " +
                "barbarian invasions of that time.",
        )
        chatNpc(
            neutral,
            "I believe a few of them survived, but with the bulk of their population destroyed, " +
                "their numbers dwindled even further. If any are left now, they'll no doubt keep " +
                "to themselves.",
        )
        chatNpc(
            shifty,
            "However, I have heard tales of their love for redberry pie. Perhaps one of those " +
                "could convince one to reveal their true heritage.",
        )
        chatPlayer(quiz, "So I just go around offering random dwarves redberry pie?")

        if (quest.getQuestStage(player) == TheKnightsSwordStage.Started) {
            quest.setQuestStage(access, TheKnightsSwordStage.ReldoAdvised)
        }

        chatNpc(
            neutral,
            "Well, not totally random. Apparently some of the surviving Imcando made a home for " +
                "themselves near the cliffs on the Asgarnian southern peninsula. That would be a " +
                "good place to start.",
        )
    }

    private companion object {
        private const val PieBeat2Gap: Int = 3

        private const val PieBeat3Gap: Int = 6

        private const val EatSound: String = "synth.EAT"
    }
}

private fun org.rsmod.api.player.protect.ProtectedAccess.carries(obj: String): Boolean =
    invTotal(inv, obj) > 0
