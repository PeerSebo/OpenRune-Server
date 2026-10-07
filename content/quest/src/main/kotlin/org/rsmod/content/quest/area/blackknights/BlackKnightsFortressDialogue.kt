package org.rsmod.content.quest.area.blackknights

import dev.openrune.types.MesAnimType
import dev.openrune.util.Wearpos
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.ScriptContext

internal class BlackKnightsFortressDialogue(private val quest: Quest) {

    fun ScriptContext.register() {
        onOpNpc1(BlackKnightsFortressState.SirAmik) { startDialogue(it.npc) { sirAmik() } }

        for (guard in BlackKnightsFortressState.FortressGuards) {
            onOpNpc1(guard) { startDialogue(it.npc) { fortressGuard(guard) } }
        }
    }

    private suspend fun Dialogue.sirAmik() {
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            quest.getQuestStage(player) >= BlackKnightsFortressState.Sabotaged -> reportSabotage()
            quest.getQuestStage(player) == BlackKnightsFortressState.OverheardMeeting ->
                reportMeeting()
            quest.isQuestInProgress(player) -> missionUpdate()
            else -> opener()
        }
    }

    private suspend fun Dialogue.opener() {
        amik(
            quiz,
            "I am the leader of the White Knights of Falador. Why<br>do you seek my audience?",
        )

        val seeksQuest =
            choice2("I seek a quest!", true, "I don't, I'm just looking around.", false)

        if (!seeksQuest) {
            chatPlayer(neutral, "I don't, I'm just looking around.")
            amik(neutral, "Ok. Please don't break anything.")
            return
        }

        chatPlayer(neutral, "I seek a quest.")
        amik(
            neutral,
            "Well, I need some spy work doing but it's quite<br>dangerous. It will involve going " +
                "into the Black Knights'<br>fortress.",
        )

        val brave =
            choice2(
                "I laugh in the face of danger!",
                true,
                "I go and cower in a corner at the first sign of danger!",
                false,
            )

        if (brave) {
            chatPlayer(happy, "I laugh in the face of danger!")
            amik(neutral, "Well that's good. Don't get too overconfident though.")
        } else {
            chatPlayer(neutral, "I go and cower in a corner at the first sign of danger!")
            amik(neutral, "Err....")
            amik(neutral, "Well.")
            amik(neutral, "I... suppose spy work DOES involve a little hiding in<br>corners.")
            chatPlayer(neutral, "Oh. I suppose I'll give it a go then.")
        }

        amik(
            neutral,
            "You've come along at just the right time actually. All of<br>my knights are already " +
                "known to the Black Knights.",
        )
        amik(sad, "Subtlety isn't exactly our strong point.")
        chatPlayer(
            quiz,
            "Can't you just take your White Knights' armour off?<br>They wouldn't recognise you " +
                "then!",
        )
        amik(
            neutral,
            "I am afraid our charter prevents us using espionage in<br>any form, that is the " +
                "domain of the Temple Knights.",
        )
        chatPlayer(quiz, "Temple Knights? Who are they?")
        amik(
            neutral,
            "That information is classified. I am forbidden to share it<br>with outsiders. ",
        )
        chatPlayer(quiz, "So... what do you need doing?")
        amik(
            angry,
            "Well, the Black Knights have started making strange<br>threats to us; demanding " +
                "large amounts of money and<br>land, and threatening to invade Falador if we " +
                "don't pay<br>them.",
        )
        amik(neutral, "Now, NORMALLY this wouldn't be a problem...")
        amik(angry, "But they claim to have a powerful new secret weapon.")
        amik(
            neutral,
            "Your mission, should you decide to accept it, is to<br>infiltrate their fortress, " +
                "find out what their secret<br>weapon is, and then sabotage it.",
        )

        if (access.questPoints() < BlackKnightsFortressState.RequiredQuestPoints) {
            mesbox(
                "You need ${BlackKnightsFortressState.RequiredQuestPoints} Quest Points to start " +
                    "this quest."
            )
            return
        }

        if (player.combatLevel < BlackKnightsFortressState.RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower<br>than the " +
                    "recommended level of ${BlackKnightsFortressState.RecommendedCombat}."
            )
        }

        val accept =
            choice2("Yes.", true, "No.", false, title = "Start the Black Knights' Fortress quest?")

        if (!accept) {
            chatPlayer(neutral, "No, I'm not ready to do that.")
            amik(neutral, "Come see me again if you change your mind.")
            return
        }

        chatPlayer(neutral, "Ok, I'll do my best.")

        quest.setQuestStage(access, BlackKnightsFortressState.Started)

        amik(
            neutral,
            "Good luck! Let me know how you get on. Here's the<br>dossier for the case, I've " +
                "already given you the details.",
        )
        giveDossier()
    }

    private suspend fun Dialogue.giveDossier() {
        if (access.invAdd(access.inv, BlackKnightsFortressState.Dossier, 1).failure) {
            amik(
                neutral,
                "Oh. You don't appear to have any room for the dossier in<br>your inventory. Come " +
                    "back when you do.",
            )
        }
    }

    private suspend fun Dialogue.missionUpdate() {
        amik(quiz, "How's the mission going?")
        chatPlayer(neutral, "I haven't managed to find what the secret weapon is<br>yet...")
        amik(neutral, "Well, keep at it! Falador's future is at stake!")

        if (!access.playerContainsObj(BlackKnightsFortressState.Dossier)) {
            amik(neutral, "Here's the dossier on the case.")
            giveDossier()
        }
    }

    private suspend fun Dialogue.reportMeeting() {
        amik(quiz, "How's the mission going?")
        chatPlayer(
            neutral,
            "I overheard a witch and the Black Knight Captain<br>talking. They're brewing a " +
                "potion of invincibility.",
        )
        amik(
            angry,
            "A potion of invincibility? That is grave news indeed. That<br>must be the secret " +
                "weapon they spoke of.",
        )
        amik(
            neutral,
            "You must destroy it before it is finished. Did you learn<br>anything that might help?",
        )
        chatPlayer(
            neutral,
            "The witch was very clear that only a Draynor Manor<br>cabbage would do, and that any " +
                "other cabbage would<br>wreck the potion entirely.",
        )
        amik(happy, "Then that is your answer. Drop an ordinary cabbage into<br>their cauldron.")
        chatPlayer(neutral, "Ok! I'll get on to sabotaging that potion.")

        if (!access.playerContainsObj(BlackKnightsFortressState.Dossier)) {
            amik(neutral, "Here's the dossier on the case.")
            giveDossier()
        }
    }

    private suspend fun Dialogue.reportSabotage() {
        chatPlayer(
            happy,
            "I have ruined the Black Knights' invincibility potion.<br>That should put a stop to " +
                "your problem and an end to<br>their little schemes.",
        )
        amik(
            neutral,
            "Yes, we have just received a message from the Black<br>Knights saying they withdraw " +
                "their demands, which<br>would seem to confirm your story.",
        )
        chatPlayer(quiz, "Now I believe there was some talk of a cash reward...")
        amik(happy, "Absolutely right. Please accept this reward.")

        mesbox("Sir Amik hands you ${BlackKnightsFortressState.RewardCoins} coins.")

        quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        amik(
            happy,
            "Falador is in your debt, adventurer. The Black Knights<br>have been very quiet since " +
                "you paid them a visit.",
        )
    }

    private suspend fun Dialogue.fortressGuard(npc: String) {
        if (access.isDisguised()) {
            guard(npc, angry, "Hey! Get back on duty!")
            chatPlayer(confused, "Uh...")
            return
        }

        guard(npc, angry, "Get lost. This is private property.")
        notInUniform(npc)
    }

    suspend fun Dialogue.uniformRefusal(npc: String) {
        guard(
            npc,
            angry,
            "Hey! You can't come in here! This is a high security<br>military installation!",
        )

        val bluff = choice2("Yes, but I work here!", true, "Ok, I'll go away then.", false)
        if (!bluff) {
            chatPlayer(neutral, "Ok, I'll go away then.")
            return
        }

        chatPlayer(neutral, "Yes, but I work here!")
        notInUniform(npc)
    }

    private suspend fun Dialogue.notInUniform(npc: String) {
        guard(npc, neutral, "Well, this is the guards' entrance...")
        guard(npc, angry, "You're not even wearing proper guards uniform!")

        val askUniform = choice2("So what is this uniform?", true, "Ok, I'll go away then.", false)
        if (!askUniform) {
            chatPlayer(neutral, "Ok, I'll go away then.")
            return
        }

        chatPlayer(quiz, "So what is this uniform?")
        guard(npc, neutral, "An iron chainbody and a bronze medium helmet, of<br>course.")
    }

    suspend fun Dialogue.meetingWarning(): Boolean {
        guard(
            BlackKnightsFortressState.MeetingGuard,
            neutral,
            "I wouldn't go in there if I were you. Those Black<br>Knights are in an important " +
                "meeting. They said they'd<br>kill anyone who went in there!",
        )

        val insist = choice2("Okay, I won't.", false, "I don't care. I'm going in anyway.", true)

        if (!insist) {
            chatPlayer(neutral, "Okay, I won't.")
            guard(BlackKnightsFortressState.MeetingGuard, neutral, "Wise move.")
            return false
        }

        chatPlayer(neutral, "I don't care. I'm going in anyway.")
        return true
    }

    suspend fun Dialogue.overheardMeeting() {
        captainLine(quiz, "So... how's the secret weapon coming along?")
        witchLine(happy, "The invincibility potion is almost ready...")
        witchLine(happy, "It's taken me FIVE YEARS, but it's almost ready.")
        witchLine(
            neutral,
            "Greldo the Goblin here is just going to fetch the last<br>ingredient for me.",
        )
        witchLine(
            neutral,
            "It's a specially grown cabbage grown by my cousin<br>Helda who lives in Draynor Manor.",
        )
        witchLine(
            neutral,
            "The soil there is slightly magical and it gives the<br>cabbages slight magical " +
                "properties.... ",
        )
        witchLine(shocked, "...not to mention the trees!")
        witchLine(
            neutral,
            "Now remember Greldo, only a Draynor Manor<br>cabbage will do! Don't get lazy and " +
                "bring any old<br>cabbage, THAT would ENTIRELY wreck the potion!",
        )
        greldoLine(neutral, "Yeth, Mithtreth.")

        if (quest.getQuestStage(player) == BlackKnightsFortressState.Started) {
            quest.setQuestStage(access, BlackKnightsFortressState.OverheardMeeting)
        }
    }

    suspend fun Dialogue.wrongCabbage() {
        mesbox("This is the wrong sort of cabbage!")
        chatPlayer(neutral, "I'm not supposed to be HELPING the witch you know...")
    }

    private suspend fun Dialogue.amik(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.SirAmikTitle,
            BlackKnightsFortressState.SirAmik,
            mesanim,
            text,
        )

    private suspend fun Dialogue.guard(npc: String, mesanim: MesAnimType, text: String) =
        chatNpcSpecific(BlackKnightsFortressState.GuardTitle, npc, mesanim, text)

    private suspend fun Dialogue.witchLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.WitchTitle,
            BlackKnightsFortressState.Witch,
            mesanim,
            text,
        )

    private suspend fun Dialogue.captainLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.CaptainTitle,
            BlackKnightsFortressState.Captain,
            mesanim,
            text,
        )

    private suspend fun Dialogue.greldoLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.GreldoTitle,
            BlackKnightsFortressState.Greldo,
            mesanim,
            text,
        )
}

internal fun ProtectedAccess.questPoints(): Int = vars[BlackKnightsFortressState.QuestPointsVarp]

internal fun ProtectedAccess.isDisguised(): Boolean {
    val hat = player.worn[Wearpos.Hat.slot]
    val torso = player.worn[Wearpos.Torso.slot]
    val legs = player.worn[Wearpos.Legs.slot]

    val guardsUniform =
        hat?.isType(BlackKnightsFortressState.BronzeMedHelm) == true &&
            torso?.isType(BlackKnightsFortressState.IronChainbody) == true
    if (guardsUniform) {
        return true
    }

    val blackLegs =
        legs?.isType(BlackKnightsFortressState.BlackPlatelegs) == true ||
            legs?.isType(BlackKnightsFortressState.BlackPlateskirt) == true

    return hat?.isType(BlackKnightsFortressState.BlackFullHelm) == true &&
        torso?.isType(BlackKnightsFortressState.BlackPlatebody) == true &&
        blackLegs
}
