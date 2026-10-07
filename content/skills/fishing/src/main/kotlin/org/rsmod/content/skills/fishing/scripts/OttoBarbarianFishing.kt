package org.rsmod.content.skills.fishing.scripts

import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.skills.fishing.configs.FishingParams
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OttoBarbarianFishing : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(OTTO) { startDialogue(it.npc) { otto() } }
        onOpLoc1(BED) { searchBed() }
    }

    private suspend fun Dialogue.otto() {
        if (stage() == FishingParams.BARB_STAGE_NOT_STARTED && player.vars[BAREHAND_STAGE] == 0) {
            firstMeeting()
        } else {
            chatNpc(neutral, "Good day to you, hearty warrior! May the spirits guide you.")
            trainingMenu()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(
            neutral,
            "Good day! You seem a hearty warrior. Maybe even some barbarian blood in that body " +
                "of yours?",
        )
        when (
            choice4(
                YOU_THINK_SO,
                1,
                "Who are you and why do you live all the way out here?",
                2,
                NOT_RELATED,
                3,
                "Sorry, I'm too busy to talk about genealogy today.",
                4,
            )
        ) {
            1 -> youThinkSo()
            2 -> whoAreYou()
            3 -> skeptic()
            4 -> {
                chatPlayer(neutral, "Sorry, I'm too busy to talk about genealogy today.")
                chatNpc(neutral, "Well then, we will talk when you learn to be less impetuous.")
            }
        }
    }

    private suspend fun Dialogue.youThinkSo() {
        chatPlayer(neutral, YOU_THINK_SO)
        chatNpc(
            neutral,
            "Who can tell? My forefathers weren't averse to travelling, so it is possible. They " +
                "tended to cause too much trouble in your so-called civilised lands, however, " +
                "so most returned to their ancestral homes.",
        )
        chatNpc(
            neutral,
            "In any case, given you seem the type, I think you are ready to learn our more " +
                "secret tribal feats for yourself.",
        )
        chatPlayer(
            neutral,
            "Oh, that sounds interesting. What sort of skills would these be?",
        )
        chatNpc(
            neutral,
            "To begin with, I can supply knowledge in the ways of firemaking, our special rod " +
                "fishing tricks, a selection of spear skills and our agricultural techniques.",
        )
        chatPlayer(
            neutral,
            "There are more advanced stages though, going by your description?",
        )
        chatNpc(
            neutral,
            "Your perception is creditable. I can eventually teach of more advanced firemaking " +
                "techniques, and the rod fishing skills are but a preliminary to our special " +
                "potions and brews.",
        )
        chatNpc(neutral, "These secrets must, however, wait until you have mastered the basics.")
        trainingMenu()
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(neutral, "Who are you and why do you live all the way out here?")
        chatNpc(
            neutral,
            "Me? I am Otto, known as the Godblessed. I follow the ways of our barbarian " +
                "ancestors, communing with the spirits of the dead and teaching their ways to " +
                "worthy disciples.",
        )
        chatNpc(
            neutral,
            "Perhaps you could be one of them. You look a likely sort to learn of these ways. " +
                "As I say, you might even have a bit of barbarian blood hidden away in you.",
        )
        when (choice2(YOU_THINK_SO, 1, NOT_RELATED, 2)) {
            1 -> youThinkSo()
            2 -> skeptic()
        }
    }

    private suspend fun Dialogue.skeptic() {
        chatPlayer(neutral, NOT_RELATED)
        chatNpc(neutral, "Your scepticism will be your loss.")
    }

    private suspend fun Dialogue.trainingMenu() {
        val caught = stage() >= FishingParams.BARB_STAGE_CAUGHT
        val fishingOption =
            if (caught) {
                "I've fished with a barbarian rod!"
            } else {
                ROD_QUESTION
            }
        when (choice3(fishingOption, 1, "Teach me to fish without a harpoon.", 3, "I'd better get going.", 2)) {
            1 -> if (caught) rodReport() else rodQuestion()
            2 -> chatPlayer(neutral, "I'd better get going.")
            3 -> barehandQuestion()
        }
    }

    private suspend fun Dialogue.barehandQuestion() {
        if (player.fishingLvl < 55 || player.statBase("stat.strength") < 35) {
            chatNpc(neutral, "You need level 55 Fishing and level 35 Strength to learn this technique.")
            return
        }
        if (player.vars[BAREHAND_STAGE] >= 1) {
            chatNpc(neutral, "Use a harpoon fishing spot with your hands free. Your own strength will replace the harpoon.")
            return
        }
        chatNpc(neutral, "Use your arm to attract the fish, then catch it with your hand. Practise where you would normally use a harpoon.")
        VarPlayerIntMapSetter.set(player, BAREHAND_STAGE, 1)
        chatNpc(neutral, "You can now catch tuna and harpoonfish without a harpoon. Stronger fish will require more training.")
    }

    private suspend fun Dialogue.rodQuestion() {
        chatPlayer(neutral, ROD_QUESTION)
        if (stage() == FishingParams.BARB_STAGE_NOT_STARTED) {
            chatNpc(
                neutral,
                "While you civilised folk use small, weak fishing rods, we barbarians are " +
                    "skilled with heavier tackle. We fish in the lake nearby.",
            )
            chatPlayer(neutral, "So can you teach me of your methods?")
            chatNpc(
                neutral,
                "Certainly. Take the rod from under my bed and fish in the lake. When you have " +
                    "caught a few fish, I am sure you will be ready to talk more with me.",
            )
            // Commit the unlock before further dialogue suspension.
            setStage(FishingParams.BARB_STAGE_TAUGHT)
            chatNpc(
                neutral,
                "You will know when you are ready, since inspiration will fill your mind.",
            )
            chatPlayer(neutral, "So I can obtain new foods from these fishing spots?")
            chatNpc(
                neutral,
                "We do not use these fish quite as you might expect. When you return we can " +
                    "speak more of this matter.",
            )
        } else {
            chatNpc(
                neutral,
                "Alas, I do not sense that you have been successful in your fishing yet. The " +
                    "look in your eyes is not that of the osprey.",
            )
            chatPlayer(neutral, "Osprey?")
            chatNpc(
                neutral,
                "Legendary birds, which the ignorant call eagles. They prey upon fish. Do as " +
                    "they do to gain inspiration.",
            )
        }
        moreKnowledge()
    }

    private suspend fun Dialogue.rodReport() {
        chatPlayer(neutral, "I've fished with a barbarian rod!")
        chatNpc(neutral, "Your mind is as clear as the waters you have fished. This is good.")
        chatPlayer(
            neutral,
            "So what do I do with these fish? They are all bloated and so nasty looking that " +
                "even a starving vulture wouldn't eat one.",
        )
        chatNpc(
            neutral,
            "Patience young one. These are fish which are fat with eggs rather than fat of " +
                "flesh. It is these eggs that are the thing to make use of.",
        )
        if (stage() == FishingParams.BARB_STAGE_CAUGHT) {
            setStage(FishingParams.BARB_STAGE_REPORTED)
        }
        moreKnowledge()
    }

    private suspend fun Dialogue.moreKnowledge() {
        when (choice2("I seek more knowledge.", 1, "I have no more questions at this time.", 2)) {
            1 -> {
                chatPlayer(neutral, "I seek more knowledge.")
                trainingMenu()
            }
            2 -> {
                chatPlayer(neutral, "I have no more questions at this time.")
                chatNpc(neutral, "In that case, farewell.")
            }
        }
    }

    private fun Dialogue.stage(): Int = player.vars[FishingParams.BARB_FISHING_VARBIT]

    private fun Dialogue.setStage(stage: Int) {
        VarPlayerIntMapSetter.set(player, FishingParams.BARB_FISHING_VARBIT, stage)
    }

    private fun ProtectedAccess.searchBed() {
        if (vars[FishingParams.BARB_FISHING_VARBIT] < FishingParams.BARB_STAGE_TAUGHT) {
            mes("You find nothing of interest.")
            return
        }
        if (FishingParams.BARBARIAN_ROD in inv) {
            mes("You already have one of Otto's barbarian rods.")
            return
        }
        if (invAdd(inv, FishingParams.BARBARIAN_ROD, count = 1).failure) {
            mes(Constants.dm_invspace)
            return
        }
        mes("You find a barbarian rod under the bed and take it.")
    }

    private companion object {
        private const val OTTO = "npc.brut_otto"
        private const val BED = "loc.brut_barbarian_bed"
        private const val BAREHAND_STAGE = "varbit.brut_fishing_s"

        private const val YOU_THINK_SO = "You think so?"
        private const val NOT_RELATED = "I really don't think I'm related to any barbarian."
        private const val ROD_QUESTION = "What can I learn about the use of a fishing rod?"
    }
}
