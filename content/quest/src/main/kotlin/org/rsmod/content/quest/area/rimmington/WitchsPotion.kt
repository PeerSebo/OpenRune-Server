package org.rsmod.content.quest.area.rimmington

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

class WitchsPotion :
    QuestScript(
        "quest_witchspotion",
        "varp.hetty",
        rewards {
            xp("stat.magic", MagicXpReward)
        },
        ItemRewardDisplay(EyeOfNewt, zoom = 400),
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == CompleteStage) {
            "Witch's Potion end state is ${quest.maxSteps} in the cache dbrow, but " +
                "CompleteStage is $CompleteStage."
        }

        onOpNpc1("npc.hetty") { startHetty(it.npc) }
        onOpLoc1("loc.hettycauldron") { drinkFromCauldron() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Hetty</col> in her house in <col=800000>Rimmington</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            strike(
                "I spoke to Hetty in her house at Rimmington. Hetty told me she could increase " +
                    "my magic power if I can bring her certain ingredients for a potion."
            )

            if (stage >= PotionReadyStage) {
                strike(
                    "I brought Hetty an eye of newt, a rat's tail, an onion and a piece of burnt " +
                        "meat, and she chanted them into her cauldron."
                )
                line("I should <red>drink</red> from <red>Hetty's cauldron</red>.")
                return@questJournal
            }

            line("Hetty needs me to bring her the following:")

            for (ingredient in Ingredients) {
                if (player.carryingObj(ingredient.obj)) {
                    strike(ingredient.journalHeld)
                } else {
                    line(ingredient.journalNeeded)
                }
            }

            line("I should take these ingredients to <red>Hetty</red>.")
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Hetty, the witch of Rimmington, offered to brew a potion that would bring out my " +
                    "darker side and tap into my hidden magical potential."
            )
            line(
                "I fetched her an eye of newt, a rat's tail, an onion and a piece of burnt meat, " +
                    "and drank the result from her cauldron. It tasted horrible."
            )
        }

    private suspend fun ProtectedAccess.startHetty(npc: Npc) {
        startDialogue(npc) { hettyDialogue() }
    }

    private suspend fun Dialogue.hettyDialogue() {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest()
            quest.getQuestStage(player) >= PotionReadyStage -> dialogPotionReady()
            quest.isQuestInProgress(player) -> dialogGatheringIngredients()
            else -> dialogQuestNotStarted()
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted() {
        chatNpc(neutral, "What could you want with an old woman like me?")

        var askedAboutWitch = false
        while (true) {
            val wantsQuest =
                if (askedAboutWitch) {
                    choice2("I am in search of a quest.", true, "Goodbye.", false)
                } else {
                    choice2(
                        "I am in search of a quest.",
                        true,
                        "I've heard that you are a witch.",
                        false,
                    )
                }

            if (wantsQuest) {
                break
            }

            if (askedAboutWitch) {
                chatPlayer(neutral, "Goodbye.")
                return
            }

            chatPlayer(neutral, "I've heard that you are a witch.")
            chatNpc(neutral, "Yes it does seem to be getting fairly common knowledge.")
            chatNpc(
                worried,
                "I fear I may be getting a visit from the witch hunters of Falador before long.",
            )
            askedAboutWitch = true
        }

        chatPlayer(neutral, "I am in search of a quest.")
        chatNpc(neutral, "Hmmm... Maybe I can think of something for you.")
        chatNpc(happy, "Would you like to become more proficient in the dark arts?")

        val accept = choice2("Yes.", true, "No.", false, title = "Start the Witch's Potion quest?")
        if (!accept) {
            chatPlayer(neutral, "No, I have my principles and honour.")
            chatNpc(neutral, "Suit yourself, but you're missing out.")
            return
        }

        chatPlayer(happy, "Yes, help me become one with my darker side.")
        chatNpc(happy, "Okay, I'm going to make a potion to help bring out your darker self.")
        chatNpc(neutral, "You will need certain ingredients.")
        chatPlayer(neutral, "What do I need?")

        quest.advanceQuestStage(access)

        chatNpc(
            neutral,
            "You need an eye of newt, a rat's tail, an onion... Oh and a piece of burnt meat.",
        )
        chatPlayer(happy, "Great, I'll go and get them.")
    }

    private suspend fun Dialogue.dialogGatheringIngredients() {
        chatPlayer(happy, "I've been looking for those ingredients.")
        chatNpc(happy, "So what have you found so far?")

        val held = Ingredients.filter { access.carryingObj(it.obj) }

        when (held.size) {
            0 -> {
                chatPlayer(sad, "I'm afraid I don't have any of them yet.")
                chatNpc(
                    angry,
                    "Well I can't make the potion without them! Remember... You need an eye of " +
                        "newt, a rat's tail, an onion, and a piece of burnt meat. Off you go dear!",
                )
            }
            Ingredients.size -> {
                chatPlayer(happy, "In fact I have everything!")
                chatNpc(happy, "Excellent, can I have them then?")
                handInIngredients()
            }
            else -> {
                chatPlayer(neutral, partialInventoryLine())
                chatNpc(neutral, "Great, but I'll need the other ingredients as well.")
            }
        }
    }

    private suspend fun Dialogue.handInIngredients() {
        mesbox(
            "You pass the ingredients to Hetty and she puts them all into her cauldron. Hetty " +
                "closes her eyes and begins to chant. The cauldron bubbles mysteriously."
        )
        chatPlayer(neutral, "Well, is it ready?")

        for (ingredient in Ingredients) {
            access.invDel(access.inv, ingredient.obj)
        }
        quest.setQuestStage(access, PotionReadyStage)

        chatNpc(happy, "Ok, now drink from the cauldron.")
    }

    private suspend fun Dialogue.dialogPotionReady() {
        chatNpc(quiz, "Well are you going to drink the potion or not?")
        chatPlayer(neutral, "Yes, I will.")
    }

    private suspend fun Dialogue.dialogAfterQuest() {
        chatNpc(quiz, "How's your magic coming along?")
        chatPlayer(neutral, "I'm practicing and slowly getting better.")
        chatNpc(happy, "Good, good.")
    }

    private suspend fun ProtectedAccess.drinkFromCauldron() {
        if (quest.getQuestStage(player) != PotionReadyStage) {
            mes("You have no reason to drink from the cauldron.")
            return
        }

        mesbox(
            "You drink from the cauldron. It tastes horrible! You feel yourself imbued with power."
        )
        quest.completeQuest(this)

        midiJingle(CompletionJingle)
    }

    private fun Dialogue.partialInventoryLine(): String {
        val clauses =
            Ingredients.map { ingredient ->
                if (access.carryingObj(ingredient.obj)) {
                    ingredient.spokenHeld
                } else {
                    ingredient.spokenMissing
                }
            }
        return clauses.dropLast(1).joinToString(", ") + ", and " + clauses.last() + "."
    }

    private data class Ingredient(
        val obj: String,
        val spokenHeld: String,
        val spokenMissing: String,
        val journalHeld: String,
        val journalNeeded: String,
    )

    private companion object {
        private const val EyeOfNewt = "obj.eye_of_newt"

        private val Ingredients =
            listOf(
                Ingredient(
                    obj = "obj.rats_tail",
                    spokenHeld = "I have the rat's tail (ewww)",
                    spokenMissing = "I don't have a rat's tail",
                    journalHeld = "I have a rat's tail with me.",
                    journalNeeded = "I need to find a rat's tail.",
                ),
                Ingredient(
                    obj = "obj.burnt_meat",
                    spokenHeld = "I have the burnt meat",
                    spokenMissing = "I don't have any burnt meat",
                    journalHeld = "I have a piece of burnt meat with me.",
                    journalNeeded = "I need to find a piece of burnt meat.",
                ),
                Ingredient(
                    obj = "obj.onion",
                    spokenHeld = "I have an onion",
                    spokenMissing = "I don't have an onion",
                    journalHeld = "I have an onion with me",
                    journalNeeded = "I need to find an onion.",
                ),
                Ingredient(
                    obj = EyeOfNewt,
                    spokenHeld = "I have the eye of newt, yum!",
                    spokenMissing = "I don't have an eye of newt",
                    journalHeld = "I have an eye of newt with me.",
                    journalNeeded = "I need to find an eye of newt.",
                ),
            )

        private const val MagicXpReward = 325.0

        private const val PotionReadyStage = 2

        private const val CompleteStage = 3

        private const val CompletionJingle = 154
    }
}

private fun ProtectedAccess.carryingObj(obj: String): Boolean = invTotal(inv, obj) > 0
