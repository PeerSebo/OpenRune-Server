package org.rsmod.content.quest.area.lumbridge

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

// The stage stores one plus the number of wool balls handed in, not a simple progress enum.
class SheepShearer :
    QuestScript(
        "quest_sheepshearer",
        "varp.sheep",
        rewards {
            xp("stat.crafting", 150.0)
            item(COINS, COIN_REWARD)
        },
        ItemRewardDisplay(SHEARS, zoom = 400),
        completionJingle = 154,
    ) {

    override fun ScriptContext.init() {
        onOpNpc1("npc.fred_the_farmer") { startFredDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startFredDialogue(npc: Npc) {
        startDialogue(npc) { fredDialogue(npc) }
    }

    private suspend fun Dialogue.fredDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest()
            quest.isQuestInProgress(player) -> dialogDuringQuest()
            else -> dialogQuestNotStarted(npc)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Fred the Farmer</col>, north-west of <col=800000>Lumbridge</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Fred the Farmer</red> has asked me to shear his sheep and spin the wool " +
                    "for him. He wants <red>20 balls of wool</red>."
            )

            objective(
                "I need to use a set of <red>shears</red> on Fred's sheep to get their wool, then " +
                    "spin it into balls at the spinning wheel on the first floor of Lumbridge Castle."
            ) {
                hasItem(BALL_OF_WOOL, "I have some balls of wool to give to Fred.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Fred the Farmer's sheep were getting mighty woolly, so I sheared them for him " +
                    "and spun the wool into balls."
            )
            line("He paid me for my trouble, and I picked up a thing or two about Crafting.")
        }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(
            angry,
            "What are you doing on my land? You're not the one who keeps leaving all my gates " +
                "open and letting out all my sheep, are you?",
        )

        val choice = choice2("I'm looking for a quest.", 1, "Sorry, I'll get off your land.", 2)
        if (choice != 1) {
            chatPlayer(neutral, "Sorry, I'll get off your land.")
            return
        }

        chatPlayer(neutral, "I'm looking for a quest.")
        chatNpc(neutral, "You're after a quest, you say? Actually, I could do with a bit of help.")
        chatNpc(
            neutral,
            "My sheep are getting mighty woolly. I'd be much obliged if you could shear them. " +
                "And while you're at it, spin the wool for me too.",
        )
        chatNpc(
            happy,
            "Yes, that's it. Bring me $REQUIRED_BALLS balls of wool. And I'm sure I could sort " +
                "out some sort of payment. Of course, there's the small matter of The Thing.",
        )
        chatPlayer(quiz, "What do you mean, The Thing?")
        chatNpc(
            shifty,
            "Well now, no one has ever seen The Thing. That's why we call it The Thing, 'cos we " +
                "don't know what it is.",
        )
        chatNpc(
            worried,
            "Some say it's a black hearted shapeshifter, hungering for the souls of hard working " +
                "decent folk like me. Others say it's just a sheep.",
        )
        chatNpc(
            angry,
            "Well I don't have all day to stand around and gossip. Are you going to shear my " +
                "sheep or what!",
        )

        val accept =
            choice2(
                "Yes, okay. I can do that.",
                1,
                "That doesn't sound very exciting. I think I'll give it a miss.",
                2,
            )
        if (accept != 1) {
            chatPlayer(neutral, "That doesn't sound very exciting. I think I'll give it a miss.")
            return
        }

        chatPlayer(happy, "Yes, okay. I can do that.")
        quest.advanceQuestStage(access)
        explainShearing(npc)
    }

    private suspend fun Dialogue.explainShearing(npc: Npc) {
        chatNpc(neutral, "Good! Now one more thing, do you actually know how to shear a sheep?")
        chatPlayer(neutral, "Err. No, I don't know actually.")
        chatNpc(
            happy,
            "Well, you're half way there already! You have a set of shears in your inventory. " +
                "Just use those on a Sheep to shear it.",
        )
        chatPlayer(neutral, "That's all I have to do?")
        chatNpc(neutral, "Well once you've collected some wool you'll need to spin it into balls.")
        chatNpc(quiz, "Do you know how to spin wool?")
        chatPlayer(neutral, "I don't know how to spin wool, sorry.")
        chatNpc(neutral, "Don't worry, it's quite simple!")
        chatNpc(
            neutral,
            "The nearest Spinning Wheel can be found on the first floor of Lumbridge Castle.",
        )
        chatNpc(neutral, "To get to Lumbridge Castle just follow the road east.")
        mesbox("This icon denotes a Spinning Wheel on the world map.")
        chatPlayer(happy, "Thank you!")
    }

    private suspend fun Dialogue.dialogDuringQuest() {
        chatNpc(angry, "What are you doing on my land?")
        chatPlayer(happy, "I need to talk to you about shearing these sheep!")
        chatNpc(neutral, "Oh. How are you doing getting those balls of wool?")

        val held = player.inv.count(BALL_OF_WOOL)
        if (held <= 0) {
            chatPlayer(sad, "I haven't got any yet.")
            chatNpc(angry, "Well hurry up then! Those sheep won't shear themselves.")
            return
        }

        chatPlayer(happy, "I have some.")
        chatNpc(neutral, "Give 'em here then.")

        val outstanding = quest.maxSteps - quest.getQuestStage(access.player)
        val handedOver = minOf(held, outstanding)

        if (handedOver < outstanding) {
            if (access.invDel(access.inv, BALL_OF_WOOL, handedOver).failure) {
                return
            }
            repeat(handedOver) { quest.advanceQuestStage(access) }
            chatPlayer(neutral, "That's all I have for now.")
            chatNpc(neutral, "Keep at it then. I'll be here.")
            return
        }

        // Advance hand-in progress immediately, but defer the final reward scroll until closing dialogue finishes.
        if (handedOver > 1 && access.invDel(access.inv, BALL_OF_WOOL, handedOver - 1).failure) {
            return
        }
        repeat(handedOver - 1) { quest.advanceQuestStage(access) }

        chatPlayer(happy, "That's the last of them.")
        chatNpc(sad, "I guess I'd better pay you then.")

        if (access.invDel(access.inv, BALL_OF_WOOL, 1).failure) {
            return
        }
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.dialogAfterQuest() {
        chatNpc(neutral, "What are you doing on my land?")
        chatPlayer(neutral, "I was just passing through.")
        chatNpc(neutral, "Well, mind the gates on your way out.")
    }

    private companion object {
        private const val BALL_OF_WOOL = "obj.ball_of_wool"
        private const val COINS = "obj.coins"
        private const val SHEARS = "obj.shears"

        private const val REQUIRED_BALLS = 20
        private const val COIN_REWARD = 60
    }
}
