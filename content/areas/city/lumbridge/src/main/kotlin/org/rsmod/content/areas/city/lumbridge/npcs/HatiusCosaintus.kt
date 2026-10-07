package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts.toplevelSidebuttonSwitch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.interfaces.journal.tab.openAchievementDiaryTab
import org.rsmod.content.other.achievementdiaries.AchievementDiaryRewards
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HatiusCosaintus @Inject constructor(
    private val rewards: AchievementDiaryRewards,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.hatius_lumbridge_diary") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Good day.")
            mainMenu()
        }

    private suspend fun Dialogue.mainMenu() {
        val choice =
            choice3("Who are you?", 1, "I have a question about my Achievement Diary", 2, "Bye!", 3)
        when (choice) {
            1 -> interrogate()
            2 -> achievementDiaryQuestion()
            3 -> bye()
        }
    }

    private suspend fun Dialogue.interrogate() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(
            neutral,
            "I am Hatius Cosaintus, the taskmaster for the " +
                "Lumbridge & Draynor Achievement Diary.",
        )
        chatPlayer(quiz, "What is the Achievement Diary?")
        chatNpc(
            neutral,
            "It's a diary that helps you keep track of particular " +
                "achievements. In the Lumbridge and Draynor area it " +
                "can help you discover some quite useful things. " +
                "Eventually, with enough exploration, the inhabitants will " +
                "reward you",
        )
        toplevelSidebuttonSwitch(player, constants.toplevel_details)
        player.openAchievementDiaryTab(eventBus)
        chatNpc(neutral, "You can see the list of tasks on the side-panel.")
        mainMenu()
    }

    private suspend fun Dialogue.achievementDiaryQuestion() {
        chatPlayer(quiz, "I have a question about my Achievement diary.")
        val choice =
            choice5(
                "What is the Achievement Diary?",
                1,
                "What are the rewards?",
                2,
                "Can I claim my diary rewards?",
                3,
                "I have lost my Explorer's ring.",
                5,
                "Bye!",
                4,
            )
        when (choice) {
            1 -> achievementDiaryExplanation()
            2 -> rewardExplanation()
            3 -> claimingRewards()
            4 -> bye()
            5 -> {
                if (rewards.reclaimRing(access)) {
                    chatNpc(happy, "Here is your replacement Explorer's ring.")
                }
            }
        }
    }

    private suspend fun Dialogue.achievementDiaryExplanation() {
        chatPlayer(quiz, "What is the Achievement Diary?")
        chatNpc(
            neutral,
            "It's a diary that helps you keep track of particular " +
                "achievements. In the Lumbridge and Draynor area it " +
                "can help you discover some quite useful things. " +
                "Eventually, with enough exploration, the inhabitants will " +
                "reward you.",
        )
        toplevelSidebuttonSwitch(player, constants.toplevel_details)
        player.openAchievementDiaryTab(eventBus)
        chatNpc(neutral, "You can see the list of tasks on the side-panel.")
        mainMenu()
    }

    private suspend fun Dialogue.rewardExplanation() {
        chatNpc(
            happy,
            "Well, there are four different Explorer's rings, which " +
                "match up with the four levels of difficulty. Each has the " +
                "same rewards as the previous level and some additional " +
                "benefits too... which tier of rewards would you like to " +
                "know more about?",
        )
        val choice =
            choice4(
                "Easy Rewards.",
                1,
                "Medium Rewards.",
                2,
                "Hard Rewards.",
                3,
                "Elite Rewards.",
                4,
            )
        when (choice) {
            1 -> easyRewards()
            2 -> mediumRewards()
            3 -> hardRewards()
            4 -> eliteRewards()
        }
    }

    private suspend fun Dialogue.easyRewards() {
        chatPlayer(neutral, "Tell me more about the Easy rewards please!")
        chatNpc(
            happy,
            "If you complete all of the easy tasks in Lumbridge and " +
                "Draynor, your ring can recharge half of your run " +
                "energy twice per day and cast low level alchemy " +
                "without runes 30 times per day.",
        )
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.mediumRewards() {
        chatPlayer(neutral, "Tell me more about the Medium rewards please!")
        chatNpc(
            happy,
            "In addition to the easy rewards, your ring can restore " +
                "half of your run energy and teleport you to the " +
                "Falador farm cabbage patch three times per day.",
        )
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.hardRewards() {
        chatPlayer(neutral, "Tell me more about the Hard rewards please!")
        chatNpc(
            happy,
            "In addition to the easy and medium benefits, the ring " +
                "will have unlimited cabbage teleports and four charges to " +
                "restore half your run energy. Collecting Tears of " +
                "Guthix will provide increased experience.",
        )
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.eliteRewards() {
        chatPlayer(neutral, "Tell me more about the Elite rewards please!")
        chatNpc(
            happy,
            "In addition to the previous tiers of rewards, you can " +
                "block an additional slayer target with slayer masters, " +
                "recharge all of your run energy three times per day " +
                "and cast high level alchemy thirty times per day. The " +
                "Culinaromancer's chest is 20% cheaper, you can use " +
                "Fairy rings without needing a dramen or lunar staff " +
                "and you'll receive double wire when thieving in " +
                "Dorgesh-Kaan.",
        )
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.claimingRewards() {
        chatPlayer(quiz, "Can I claim my diary rewards?")
        val tier = choice4("Easy rewards.", 1, "Medium rewards.", 2, "Hard rewards.", 3, "Never mind.", 4)
        if (tier == 4) return
        val label = listOf("Easy", "Medium", "Hard")[tier - 1]
        val minimum = listOf(30, 40, 50)[tier - 1]
        val result = when (tier) {
            1 -> rewards.claimEasy(player)
            2 -> rewards.claimMedium(player)
            else -> rewards.claimHard(player)
        }
        when (result) {
            AchievementDiaryRewards.ClaimResult.NotComplete ->
                chatNpc(
                    neutral,
                    if (tier == 3) "The Hard diary has eleven tasks. Four still need content " +
                        "that is not implemented here yet, so its rewards are not available."
                    else "Complete all twelve $label tasks and claim any earlier rewards first.",
                )
            AchievementDiaryRewards.ClaimResult.AlreadyClaimed ->
                chatNpc(happy, "You have already claimed your $label rewards.")
            AchievementDiaryRewards.ClaimResult.InventoryFull ->
                chatNpc(
                    neutral,
                    "You'll need two free inventory slots for your ring and antique lamp.",
                )
            AchievementDiaryRewards.ClaimResult.Claimed ->
                chatNpc(
                    happy,
                    "Congratulations! Here are your Explorer's ring and an antique lamp. " +
                        "Rub the lamp to choose a skill of at least level $minimum.",
                )
        }
        mainMenu()
    }

    private suspend fun Dialogue.bye() {
        chatPlayer(happy, "Bye!")
        chatNpc(happy, "Toodles.")
    }
}
