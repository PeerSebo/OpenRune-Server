package org.rsmod.content.quest.area.falador

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

class DoricsQuest :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards {
            xp("stat.mining", MiningXpReward)
            item(Coins, RewardCoins)
            extra("Use of Doric's Anvils")
        },
        ItemRewardDisplay("obj.steel_pickaxe"),
        completionJingle = CompletionJingle,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == CompleteStage) {
            "Doric's Quest end state is ${quest.maxSteps} in the cache dbrow, but " +
                "CompleteStage is $CompleteStage."
        }

        onOpNpc1("npc.doric") { talkToDoric(it.npc) }
        onOpLoc1(Whetstone) { useWhetstone() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Doric</col> at his hut north of " + "<col=800000>Falador</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            strike("I have spoken to Doric.")
            line("")
            line("I need to collect some items and bring them to <red>Doric</red>.")

            for (material in Materials) {
                if (player.carriedCount(material.obj) >= material.count) {
                    strike(material.journalLine)
                } else {
                    line(material.journalLine)
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I spoke to Doric, a dwarf who makes pickaxes at his hut north of Falador. He " +
                    "would not let me use his anvils until I brought him materials of his own."
            )
            line(
                "I fetched him six clay, four copper ore and two iron ore. He paid me for the " +
                    "trouble and gave me the run of his smithy."
            )
        }

    private suspend fun ProtectedAccess.talkToDoric(npc: Npc) {
        startDialogue(npc) { doricDialogue() }
    }

    private suspend fun Dialogue.doricDialogue() {
        when {
            quest.isQuestCompleted(player) -> {
                chatNpc(quiz, "Hello again traveller. The anvils are yours whenever you need them.")
            }
            quest.isQuestInProgress(player) -> checkOnMaterials()
            else -> doricOpener()
        }
    }

    private suspend fun Dialogue.doricOpener() {
        chatNpc(quiz, "Hello traveller, what brings you to my humble smithy?")

        val option =
            choice5(
                "I wanted to use your anvils.",
                1,
                "I want to use your whetstone.",
                2,
                "Mind your own business, shortstuff!",
                3,
                "I was just checking out the landscape.",
                4,
                "What do you make here?",
                5,
            )

        when (option) {
            1 -> {
                chatPlayer(neutral, "I wanted to use your anvils.")
                chatNpc(
                    neutral,
                    "My anvils get enough work with my own use. I make pickaxes, and it takes a " +
                        "lot of hard work. If you could get me some more materials, then I could " +
                        "let you use them.",
                )
                offerQuest()
            }
            2 -> {
                chatPlayer(neutral, "I wanted to use your whetstone.")
                chatNpc(
                    neutral,
                    "The whetstone is for more advanced smithing, but I could let you use it as " +
                        "well as my anvils if you could get me some more materials.",
                )
                offerQuest()
            }
            3 -> {
                chatPlayer(angry, "Mind your own business, shortstuff!")
                chatNpc(
                    angry,
                    "How nice to meet someone with such pleasant manners. Do come again when you " +
                        "need to shout at someone smaller than you!",
                )
            }
            4 -> landscapeBranch()
            5 -> pickaxeBranch()
        }
    }

    private suspend fun Dialogue.landscapeBranch() {
        chatPlayer(neutral, "I was just checking out the landscape.")
        chatNpc(
            happy,
            "Hope you like it. I do enjoy the solitude of my little home. If you get time, please " +
                "say hi to my friends in the Dwarven Mine.",
        )

        val askMore = choice2("Dwarven Mine?", true, "Will do!", false)
        if (!askMore) {
            chatPlayer(happy, "Will do!")
            return
        }

        chatPlayer(quiz, "Dwarven Mine?")
        chatNpc(
            neutral,
            "Yep, the entrance is in the side of Ice Mountain just to the east of here. They're a " +
                "friendly bunch. Stop in at Nurmof's store and buy one of my pickaxes!",
        )
    }

    private suspend fun Dialogue.pickaxeBranch() {
        chatPlayer(quiz, "What do you make here?")
        chatNpc(happy, "I make pickaxes. I am the best maker of pickaxes in the whole of Gielinor.")
        chatPlayer(quiz, "Do you have any to sell?")
        chatNpc(neutral, "Sorry, but I've got a running order with Nurmof.")

        val askWho = choice2("Who's Nurmof?", true, "Ah, fair enough.", false)
        if (!askWho) {
            chatPlayer(neutral, "Ah, fair enough.")
            return
        }

        chatPlayer(quiz, "Who's Nurmof?")
        chatNpc(
            neutral,
            "Nurmof has a store over in the Dwarven Mine. You can find the entrance on the side " +
                "of Ice Mountain to the east of here.",
        )
    }

    private suspend fun Dialogue.offerQuest() {
        if (access.statBase("stat.mining") < RecommendedMining) {
            mesbox(
                "Before starting this quest, be aware that one or more of your skill levels are " +
                    "lower than recommended."
            )
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start Doric's Quest?")

        if (!accept) {
            chatPlayer(neutral, "No, hitting rocks is for the boring people, sorry.")
            chatNpc(confused, "That is your choice. Nice to meet you anyway.")
            return
        }

        chatPlayer(happy, "Yes, I will get you the materials.")

        quest.setQuestStage(access, StartedStage)

        chatNpc(
            neutral,
            "Clay is what I use more than anything, to make casts. Could you get me 6 clay, 4 " +
                "copper ore, and 2 iron ore, please? I could pay a little, and let you use my " +
                "anvils. Take this pickaxe with you just in case you need it.",
        )
        access.invAdd(access.inv, StarterPickaxe, 1)

        if (access.hasAllMaterials()) {
            theCoincidence()
            return
        }

        materialsMenu()
    }

    private suspend fun Dialogue.theCoincidence() {
        chatPlayer(happy, "You know, it's funny you should require those exact things!")
        chatNpc(quiz, "What do you mean?")
        chatPlayer(
            neutral,
            "I can usually fit 28 things in my backpack and in a world full of quite literally " +
                "limitless possibilities, a complete coincidence has occurred!",
        )
        chatNpc(quiz, "I don't quite understand what you're saying?")
        chatPlayer(
            neutral,
            "Well, out of pure coincidence, despite definitely not knowing what you were about to " +
                "request, I just so happened to have carried those exact items!",
        )

        if (access.hasExactMaterials()) {
            chatPlayer(happy, "In fact, in the exact quantities too!")
        }

        chatNpc(
            happy,
            "Oh my, that is a coincidence! Pass them here, please. I can spare you " +
                "some coins for your trouble, and please use my anvils any time you want.",
        )
        handIn()
    }

    private suspend fun Dialogue.checkOnMaterials() {
        chatNpc(quiz, "Have you got my materials yet, traveller?")

        if (!access.hasAllMaterials()) {
            chatPlayer(sad, "Sorry, I don't have them all yet.")
            chatNpc(
                neutral,
                "Not to worry, stick at it. Remember, I need 6 clay, 4 copper ore, and 2 iron ore.",
            )
            materialsMenu()
            return
        }

        chatPlayer(happy, "I have everything you need!")
        chatNpc(
            neutral,
            "Many thanks! Pass them here, please. I can spare you some coins for your trouble, " +
                "and please use my anvils any time you want.",
        )
        handIn()
    }

    private suspend fun Dialogue.materialsMenu() {
        val askWhere =
            choice2("Where can I find those?", true, "Certainly, I'll be right back!", false)

        if (!askWhere) {
            chatPlayer(happy, "Certainly, I'll be right back!")
            return
        }

        chatPlayer(quiz, "Where can I find those?")
        chatNpc(
            neutral,
            "You'll be able to find all those ores in the rocks just inside the Dwarven Mine. " +
                "Head east from here and you'll find the entrance in the side of Ice Mountain.",
        )

        if (access.statBase("stat.mining") >= RecommendedMining) {
            return
        }

        chatPlayer(sad, "But I'm not a good enough miner to get iron ore.")
        chatNpc(
            neutral,
            "Oh well, you could practice mining until you can. Can't beat a bit of mining - it's " +
                "a useful skill. Failing that, you might be able to find a more experienced " +
                "adventurer to buy the iron ore off.",
        )
    }

    private suspend fun Dialogue.handIn() {
        objbox(CopperOre, "You hand the clay, copper, and iron to Doric.")

        for (material in Materials) {
            if (access.invDel(access.inv, material.obj, material.count).failure) {
                return
            }
        }

        quest.completeQuest(access)
    }

    private suspend fun ProtectedAccess.useWhetstone() {
        if (!quest.isQuestCompleted(player)) {
            mes("You should probably ask before using that.")
            return
        }
        mes("Nothing here needs sharpening.")
    }

    private fun ProtectedAccess.hasAllMaterials(): Boolean =
        Materials.all { carriedCount(it.obj) >= it.count }

    private fun ProtectedAccess.hasExactMaterials(): Boolean =
        Materials.all { carriedCount(it.obj) == it.count }

    private data class Material(val obj: String, val count: Int, val journalLine: String)

    private companion object {
        private const val QuestKey = "quest_dorics"

        private const val QuestVarp = "varp.doricquest"

        private const val StartedStage = 10

        private const val CompleteStage = 100

        private const val Coins = "obj.coins"
        private const val CopperOre = "obj.copper_ore"
        private const val Whetstone = "loc.devious_whetstone"

        private const val StarterPickaxe = "obj.bronze_pickaxe"

        private val Materials =
            listOf(
                Material("obj.clay", 6, "6 Clay."),
                Material(CopperOre, 4, "4 Copper Ore."),
                Material("obj.iron_ore", 2, "2 Iron Ore."),
            )

        private const val RecommendedMining = 15

        private const val MiningXpReward = 1300.0

        private const val RewardCoins = 180

        private const val CompletionJingle = 154
    }
}

private fun ProtectedAccess.carriedCount(obj: String): Int = invTotal(inv, obj)
