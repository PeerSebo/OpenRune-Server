package org.rsmod.content.quest.area.lumbridge

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.mechanics.dig.DigSites
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Agreed
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.AncientCasket
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.AntiqueLamp
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.BeginnerScrollBox
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.CasketFound
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.CasketGiven
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue1
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue1Dig
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue1Given
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue2
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue2Dig
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue2Found
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue3Dig
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue4
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue4Dig
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Clue4Found
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.CoinReward
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Coins
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Complete
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.CompletionJingle
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.LampXp
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.NotStarted
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.Orb
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.OrbFound
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.QuestKey
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.QuestVarp
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.StageVarbit
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.SynthLampReward
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.SynthOrbCold
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.SynthOrbHot
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.SynthOrbWarm
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.VeosLumbridge
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpotState.VeosSarim
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.XpRewardLamp
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class XMarksTheSpot :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards {
            item(AntiqueLamp, 1, label = "An Antique Lamp")
            item(Coins, CoinReward)
            extra("Ability to receive Clue Scroll")
            extra("Boxes")
            extra("A Beginner Clue Scroll Box")
        },
        ItemRewardDisplay(AncientCasket, zoom = 400),
        questVarbit = StageVarbit,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "X Marks the Spot end state is ${quest.maxSteps} in the cache dbrow, but " +
                "Complete is $Complete."
        }

        onPlayerLogin { syncVeosVisibility(player) }

        onOpNpc1(VeosLumbridge) { startDialogue(it.npc) { veos(inPortSarim = false) } }
        onOpNpc1(VeosSarim) { startDialogue(it.npc) { veos(inPortSarim = true) } }

        onOpHeld1(Clue1) { readScroll(FirstClueText) }
        onOpHeld1(Clue2) { readMapClue() }
        onOpHeld1(Orb) { feelOrb() }
        onOpHeld1(Clue4) { readScroll(CipherClueText) }
        onOpHeld1(AncientCasket) { refuseToOpenCasket() }
        onOpHeld1(AntiqueLamp) { rubAntiqueLamp() }

        registerDigSites()
    }

    override fun subTitle(): String =
        "talking to <col=800000>Veos</col> in <col=800000>The Sheared Ram</col> in " +
            "<col=800000>Lumbridge</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            strike(JournalIntro)

            val stage = quest.getQuestStage(player.player)

            if (stage >= OrbFound) strike(JournalFoundSecondScroll)
            if (stage >= Clue4Found) strike(JournalFoundOrb)
            if (stage >= CasketFound) strike(JournalFoundThirdScroll)

            when (stage) {
                Clue1Given ->
                    line(
                        "<red>Veos</red> gave me a <red>Treasure Scroll</red>. I should use it to " +
                            "find the treasure. If I get stuck, <red>Veos</red> might be able to " +
                            "give me some extra guidance."
                    )
                Clue2Found ->
                    line(
                        "<red>Veos</red> gave me a <red>Treasure Scroll</red> which I used to help " +
                            "me find a second <red>Treasure Scroll</red>. I should see if the " +
                            "second scroll leads me to the treasure. If I get stuck, " +
                            "<red>Veos</red> might be able to give me some extra guidance."
                    )
                OrbFound ->
                    line(
                        "Using the second <red>Treasure Scroll</red>, I managed to find a " +
                            "<red>Mysterious Orb</red>. I should use it to find the treasure. If I " +
                            "get stuck, <red>Veos</red> might be able to give me some extra " +
                            "guidance."
                    )
                Clue4Found ->
                    line(
                        "The <red>Mysterious Orb</red> led me to a third <red>Treasure " +
                            "Scroll</red>. I should see if it leads me to the treasure. If I get " +
                            "stuck, <red>Veos</red> might be able to give me some extra guidance."
                    )
                else ->
                    line(
                        "By using the third <red>Treasure Scroll</red>, I managed to find an " +
                            "<red>Ancient Casket</red>. I should take it to <red>Veos</red> at his " +
                            "ship on the northernmost dock in <red>Port Sarim</red>."
                    )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(JournalIntro)
            line(JournalFoundSecondScroll)
            line(JournalFoundOrb)
            line(JournalFoundThirdScroll)
            line("By using the third Treasure Scroll, I managed to find an Ancient Casket.")
            line(
                "I took the Ancient Casket to Veos at his ship in Port Sarim. He thanked me for " +
                    "my help and rewarded me."
            )
        }

    private suspend fun Dialogue.veos(inPortSarim: Boolean) {
        when (quest.getQuestStage(player)) {
            NotStarted -> veosUnstarted()
            Agreed -> veosReadyToHelp()
            CasketFound -> if (inPortSarim) veosHandIn() else veosInProgress(inPortSarim)
            CasketGiven -> veosReward()
            Complete -> veosAfterQuest()
            else -> veosInProgress(inPortSarim)
        }
    }

    private suspend fun Dialogue.veosUnstarted() {
        if (access.cluequestVeosAlreadyMet) {
            chatNpc(happy, "Hello there.")
            chatPlayer(quiz, "Hello Veos. What brings you to Lumbridge?")
            chatNpc(
                neutral,
                "I'm here on a bit of a treasure hunt. Back in Kourend I came across a scroll. I " +
                    "believe it will lead me to something of great value.",
            )
            veosBlocker()
            return
        }

        chatNpc(happy, "Hello there.")

        val opening = choice3("Who are you?", 1, "I'm looking for a quest.", 2, "I have to go.", 3)

        when (opening) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    neutral,
                    "The name's Veos. I'm a treasure hunter from the wonderous Kingdom of Great " +
                        "Kourend.",
                )
            }
            2 -> {
                chatPlayer(quiz, "I'm looking for a quest.")
                chatNpc(
                    neutral,
                    "Hmmm. Well now that you mention it, I could do with some help. The name's " +
                        "Veos. I'm a treasure hunter from the wonderous Kingdom of Great Kourend.",
                )
            }
            else -> {
                chatPlayer(neutral, "I have to go.")
                return
            }
        }

        chatPlayer(quiz, "Great Kourend? Where's that?")
        chatNpc(neutral, "Across the sea to the far west. It is a truly magnificent place.")
        chatPlayer(quiz, "Interesting. So what brings you to Lumbridge?")
        chatNpc(
            neutral,
            "I am here on a bit of a hunt. The hunt for treasure. Back in my home of Great " +
                "Kourend I came across a scroll. I believe it will lead me to something of great " +
                "value.",
        )
        veosBlocker()
    }

    private suspend fun Dialogue.veosBlocker() {
        chatNpc(
            neutral,
            "Alas, I've hit a bit of a blocker. The scroll has led me here but as I don't really " +
                "know the area, I'm not sure what to do next.",
        )

        if (choice2("Can I help?", 1, "Well good luck with it.", 2) != 1) {
            chatPlayer(neutral, "Well good luck with it.")
            return
        }

        chatPlayer(quiz, "Can I help?")
        chatNpc(
            neutral,
            "Hmmm. Maybe you can. You probably know this area better than I do. You might be able " +
                "to work the scroll out. I'll be able to reward you if you help.",
        )

        if (choice2("Yes.", 1, "No.", 2, title = "Start the X Marks the Spot quest?") != 1) {
            return
        }

        quest.setQuestStage(access, Agreed)
        chatPlayer(happy, "Sounds good, what should I do?")
        veosGiveFirstScroll()
    }

    private suspend fun Dialogue.veosReadyToHelp() {
        chatNpc(quiz, "Ready to help with this treasure hunt?")
        chatPlayer(neutral, "What do you need me to do?")
        veosGiveFirstScroll()
    }

    private suspend fun Dialogue.veosGiveFirstScroll() {
        if (!player.inv.hasFreeSpace()) {
            chatNpc(
                neutral,
                "First you should go and make some room in your inventory. Once you've done that, " +
                    "you can have a look at this scroll.",
            )
            return
        }

        quest.setQuestStage(access, Clue1Given)
        access.invAdd(access.inv, Clue1, 1)
        chatNpc(
            neutral,
            "Take this scroll. It should lead you to the treasure I seek. Once you've found it, " +
                "meet me at my ship. It's docked at the northernmost pier in Port Sarim.",
        )
        mesbox("Veos has given you a scroll.")
        chatPlayer(quiz, "Awesome. Anything else I should know?")
        chatNpc(
            neutral,
            "You'll probably want a spade, I find they're pretty much always needed when it comes " +
                "to hunting treasure. Check the general store if you don't have one.",
        )
        chatNpc(
            neutral,
            "I'll be sticking around here a little longer before heading back to my ship. If you " +
                "need extra guidance with anything, just let me know and I'll see what I can do.",
        )

        if (choice2("Okay, thanks Veos.", 1, "Tell me more about Great Kourend.", 2) != 1) {
            veosAboutKourend()
            return
        }

        chatPlayer(happy, "Okay, thanks Veos.")
        chatNpc(happy, "Good luck.")
    }

    private suspend fun Dialogue.veosInProgress(inPortSarim: Boolean) {
        chatNpc(happy, "Hello there.")
        chatPlayer(happy, "Hello Veos.")

        if (veosReturnLostItem()) {
            return
        }

        val topic =
            choice3(
                "Let's talk about my quest.",
                1,
                "Tell me more about Great Kourend.",
                2,
                "See you later.",
                3,
            )

        when (topic) {
            2 -> {
                veosAboutKourend()
                return
            }
            3 -> {
                chatPlayer(neutral, "See you later.")
                return
            }
        }

        chatPlayer(neutral, "Let's talk about my quest.")
        chatNpc(quiz, "How's the treasure hunt going?")

        if (choice2("I could do with some extra help.", 1, "I'm still working on it.", 2) != 1) {
            chatPlayer(neutral, "I'm still working on it.")
            veosKeepAtIt(inPortSarim)
            return
        }

        chatPlayer(neutral, "I could do with some extra help.")

        when (quest.getQuestStage(player)) {
            Clue1Given -> {
                showToVeos(ScrollBoxObj, "You show the scroll to Veos.")
                chatNpc(
                    neutral,
                    "This is the scroll I gave you. I don't know how to solve it I'm afraid. " +
                        "Maybe look for someone called Bob?",
                )
            }
            Clue2Found -> {
                showToVeos(ScrollBoxObj, "You show the scroll to Veos.")
                chatNpc(
                    neutral,
                    "Looks like a map to me. I'd assume the X marks where you need to go.",
                )
            }
            OrbFound -> {
                showToVeos(Orb, "You show the orb to Veos.")
                chatNpc(
                    neutral,
                    "Interesting, these are quite rare. They work using temperature. The closer " +
                        "you are, the hotter the orb.",
                )
            }
            Clue4Found -> {
                showToVeos(ScrollBoxObj, "You show the scroll to Veos.")
                chatNpc(
                    neutral,
                    "Ah a cipher, I've seen these before. Try shifting the letters to the left or " +
                        "right. That normally works.",
                )
            }
            else -> veosKeepAtIt(inPortSarim)
        }
    }

    private suspend fun Dialogue.veosKeepAtIt(inPortSarim: Boolean) {
        if (inPortSarim) {
            chatNpc(neutral, "Well keep at it. Once you've found the treasure, bring it to me.")
        } else {
            chatNpc(
                neutral,
                "Well keep at it. Once you've found the treasure, meet me at my ship. It's " +
                    "docked at the northernmost pier in Port Sarim.",
            )
        }
    }

    private suspend fun Dialogue.veosReturnLostItem(): Boolean {
        val expected = stageItem(quest.getQuestStage(player)) ?: return false
        if (access.carryingObj(expected)) {
            return false
        }
        if (!player.inv.hasFreeSpace()) {
            return false
        }

        val noun = if (expected == Orb) "orb" else "scroll"
        chatNpc(neutral, "I came across this $noun. Did you lose it by any chance?")
        access.invAdd(access.inv, expected, 1)
        mesbox("Veos has given you a $noun.")
        chatPlayer(neutral, "I did, thanks Veos.")
        chatNpc(neutral, "Not to worry. Just try to be careful with it.")
        return true
    }

    private suspend fun Dialogue.veosHandIn() {
        chatNpc(happy, "Hello there.")
        chatPlayer(happy, "Hello Veos.")
        chatNpc(quiz, "How's the treasure hunt going?")

        if (!access.carryingObj(AncientCasket)) {
            chatPlayer(neutral, "I'm still working on it.")
            veosKeepAtIt(inPortSarim = true)
            return
        }

        chatPlayer(happy, "I found the treasure!")
        chatNpc(happy, "Excellent. I'll take it off your hands now.")

        quest.setQuestStage(access, CasketGiven)
        access.invDel(access.inv, AncientCasket, 1)
        objbox(AncientCasket, ObjBoxZoom, "You give Veos the Ancient Casket.")

        veosReward()
    }

    private suspend fun Dialogue.veosReward() {
        chatNpc(
            happy,
            "Brilliant. This is just what I was looking for. Thank you so much for your help.",
        )
        chatPlayer(quiz, "No problem. So what is this treasure anyway?")
        chatNpc(
            shifty,
            "Oh err... nothing important. Just something that might be of use to me back in Great " +
                "Kourend.",
        )
        chatPlayer(confused, "If you say so...")
        chatNpc(happy, "Anyway, as promised, a reward for you.")

        if (!access.cluequestClueReward) {
            access.cluequestClueReward = true
            if (access.inv.hasFreeSpace()) {
                access.invAdd(access.inv, BeginnerScrollBox, 1)
            }
        }
        access.cluequestCoinReward = true

        quest.completeQuest(access)

        access.midiJingle(CompletionJingle)

        quest.awaitRewardScroll(access)

        chatNpc(
            happy,
            "Anyway, I'd best be getting back to Great Kourend. I'm sure I'll be back here before " +
                "long though, there's always more treasure to be found.",
        )
        chatNpc(
            happy,
            "If you ever fancy visiting the kingdom, come find me here. I'm more than happy to " +
                "take you there. Consider it an extra thank you for your help.",
        )
        chatPlayer(happy, "Sounds great, thanks Veos.")
    }

    private suspend fun Dialogue.veosAfterQuest() {
        chatNpc(happy, "Hello there.")
        chatPlayer(happy, "Hello Veos.")
        chatNpc(
            happy,
            "If you ever fancy visiting Great Kourend, come find me here. I'm more than happy to " +
                "take you there.",
        )
        chatPlayer(happy, "Thanks Veos.")
    }

    private suspend fun Dialogue.veosAboutKourend() {
        chatPlayer(quiz, "Tell me more about Great Kourend.")
        chatNpc(
            neutral,
            "It's a kingdom far to the west, across the sea. Five great houses rule it between " +
                "them, and each has its own way of doing things.",
        )
        chatNpc(
            neutral,
            "It's a fair trip by ship, but well worth the seeing. Perhaps I'll take you there one " +
                "day.",
        )
    }

    private suspend fun Dialogue.showToVeos(obj: String, text: String) {
        objbox(obj, ObjBoxZoom, text)
    }

    private fun registerDigSites() {
        DigSites.register("$QuestKey.clue1", Clue1Dig) {
            unearth(
                requiredStage = Clue1Given,
                newStage = Clue2Found,
                replacing = Clue1,
                granted = Clue2,
                shown = Clue2,
                text = "You dig up a Treasure Scroll.",
            )
        }

        DigSites.register("$QuestKey.clue2", Clue2Dig) {
            unearth(
                requiredStage = Clue2Found,
                newStage = OrbFound,
                replacing = Clue2,
                granted = Orb,
                shown = Orb,
                text = "You dig up a Mysterious Orb.",
            )
        }

        DigSites.register("$QuestKey.clue3", Clue3Dig) {
            unearth(
                requiredStage = OrbFound,
                newStage = Clue4Found,
                replacing = Orb,
                granted = Clue4,
                shown = Clue2,
                text = "You dig up a Treasure Scroll.",
            )
        }

        DigSites.register("$QuestKey.casket", Clue4Dig) {
            val dug =
                unearth(
                    requiredStage = Clue4Found,
                    newStage = CasketFound,
                    replacing = Clue4,
                    granted = AncientCasket,
                    shown = AncientCasket,
                    text =
                        "You dig up an Ancient Casket. As you do, you hear a faint whispering. " +
                            "You can't make out what it says though...",
                    onDug = { player.veosLumbridgeVis = VeosLumbridgeVisibility.Gone },
                )

            if (dug) {
                startDialogue {
                    chatPlayer(confused, "Hmm... Must have been the wind.")
                    chatPlayer(
                        happy,
                        "Anyway, this must be the treasure that Veos is after. I should take it " +
                            "to him. If I remember right, he's docked at the northernmost pier in " +
                            "Port Sarim.",
                    )
                }
            }
            dug
        }
    }

    private suspend fun ProtectedAccess.unearth(
        requiredStage: Int,
        newStage: Int,
        replacing: String,
        granted: String,
        shown: String,
        text: String,
        onDug: ProtectedAccess.() -> Unit = {},
    ): Boolean {
        if (quest.getQuestStage(player) != requiredStage) {
            return false
        }

        if (carryingObj(replacing)) {
            invReplace(inv, replacing, 1, granted)
        } else {
            if (!inv.hasFreeSpace()) {
                mes("You don't have enough inventory space to dig that up.")
                return true
            }
            invAdd(inv, granted, 1)
        }

        quest.setQuestStage(this, newStage)
        onDug()
        objbox(shown, ObjBoxZoom, text)
        return true
    }

    private fun ProtectedAccess.readScroll(text: String) {
        ifSetText("component.trail_cluetext:text", text)
        ifOpenMainModal("interface.trail_cluetext")
    }

    private fun ProtectedAccess.readMapClue() {
        ifOpenMainModal("interface.cluequest_map")
    }

    private fun ProtectedAccess.feelOrb() {
        val distance = chebyshev(player.coords.x, player.coords.z) + 1
        val previous = cluequestPrevDistance

        val band =
            when {
                distance > ColdThreshold -> OrbBand("cold", SynthOrbCold)
                distance > WarmThreshold -> OrbBand("warm", SynthOrbWarm)
                distance > HotThreshold -> OrbBand("hot", SynthOrbHot)
                else -> OrbBand("very hot", SynthOrbHot)
            }

        val comparison =
            when {
                previous == 0 -> ""
                distance < previous -> ", and warmer than last time"
                distance > previous -> ", but colder than last time"
                else -> ""
            }

        cluequestPrevDistance = distance
        cluequestPrevCoord = player.coords

        mes("The orb is ${band.word}$comparison.")
        soundSynth(band.synth)
    }

    private fun chebyshev(x: Int, z: Int): Int = max(abs(x - Clue3Dig.x), abs(z - Clue3Dig.z))

    private suspend fun ProtectedAccess.refuseToOpenCasket() {
        startDialogue {
            chatPlayer(
                neutral,
                "I don't think Veos would want me to open his treasure. I should take it to him. " +
                    "If I remember right, it's docked at the northernmost pier in Port Sarim.",
            )
        }
    }

    private suspend fun ProtectedAccess.rubAntiqueLamp() {
        val stat = XpRewardLamp.choose(this) ?: return
        if (!carryingObj(AntiqueLamp)) {
            return
        }

        cluequestLampReward = true
        invDel(inv, AntiqueLamp, 1)
        val granted = statAdvance(stat, LampXp, globalRate = 1.0)
        soundSynth(SynthLampReward)
        mesbox(
            "<col=0000ff>Your wish has been granted!</col> You have been awarded $granted " +
                "${XpRewardLamp.displayName(stat)} experience!"
        )
    }

    private fun syncVeosVisibility(player: Player) {
        val lumbridge =
            if (quest.getQuestStage(player) >= CasketFound) {
                VeosLumbridgeVisibility.Gone
            } else {
                VeosLumbridgeVisibility.Present
            }

        if (player.veosLumbridgeVis != lumbridge) {
            player.veosLumbridgeVis = lumbridge
        }
        if (player.veosSarimVis != VeosSarimVisibility.Present) {
            player.veosSarimVis = VeosSarimVisibility.Present
        }
    }

    private fun stageItem(stage: Int): String? =
        when (stage) {
            Clue1Given -> Clue1
            Clue2Found -> Clue2
            OrbFound -> Orb
            Clue4Found -> Clue4
            CasketFound -> AncientCasket
            else -> null
        }

    private class OrbBand(val word: String, val synth: String)

    private companion object {
        private const val ObjBoxZoom = 400

        private const val ScrollBoxObj = Clue2

        private const val ColdThreshold = 90
        private const val WarmThreshold = 50
        private const val HotThreshold = 25

        private const val FirstClueText =
            "Within the town of Lumbridge lives a<br>man named Bob. He walks out of his<br>" +
                "door and takes 1 step east, 7 steps<br>north, 5 steps west and 1 step south.<br>" +
                "<br>Once he arrives, he digs a hole and<br>buries his treasure."

        private const val CipherClueText =
            "The cipher reveals<br>where to dig next:<br><br>ESBZOPS QJH QFO"

        private const val JournalIntro =
            "I spoke to Veos in the Sheared Ram Pub in Lumbridge. He told me that he was a " +
                "traveller from a land to the far west known as Great Kourend and that he had " +
                "come to Lumbridge in search of treasure. He asked me to help him find this " +
                "treasure."

        private const val JournalFoundSecondScroll =
            "Veos gave me a Treasure Scroll which I used to help me find a second Treasure Scroll."

        private const val JournalFoundOrb =
            "Using the second Treasure Scroll, I managed to find a Mysterious Orb."

        private const val JournalFoundThirdScroll =
            "The Mysterious Orb led me to a third Treasure Scroll."
    }
}

private fun ProtectedAccess.carryingObj(obj: String): Boolean = invTotal(inv, obj) > 0
