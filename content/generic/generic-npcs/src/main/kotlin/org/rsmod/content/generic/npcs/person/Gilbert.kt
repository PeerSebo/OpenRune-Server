package org.rsmod.content.generic.npcs.person

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Gilbert : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.pride22_gilbert") {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello, Gilbert!")
                chatNpc(happy, "Hey, ${player.displayName}!")
                when (choice3(Meadow, 1, News, 2, Reclaim, 3)) {
                    1 -> meadow()
                    2 -> parade()
                    3 -> reclaim()
                }
            }
        }
    }

    private suspend fun Dialogue.meadow() {
        chatPlayer(happy, Meadow)
        chatNpc(happy, "I know! It didn't always look like this though. It used to be dull and grey!")
        chatPlayer(quiz, "Dull and grey? How did it become so colourful?")
        chatNpc(happy, "An adventurer came along and helped me out. We cast a magic spell using " +
            "flower seeds, and it created this lovely sunburst!")
        chatPlayer(happy, "Oh, I'd have liked to have seen that spell. It sounds impressive!")
        chatNpc(happy, "It was! The news travelled far. I've been getting lots of visitors. " +
            "Many flower traders from across the lands have paid the meadow a visit.")
        chatNpc(happy, "I've even traded with a few for some new flower seeds with different colours!")
        if (vars[TakenCrown] != 0) {
            chatPlayer(quiz, "New colours?")
            chatNpc(happy, "Your flower crown can change its colours now.")
            chatNpc(happy, "That spell created eight extra colour choices.")
            chatPlayer(happy, "A small splash of colour went a long way.")
            chatNpc(happy, "Colour can surprise you!")
            return
        }
        chatPlayer(happy, "That's really cool. What do you do with all the flowers?")
        chatNpc(happy, "I make flower crowns in many different colours. I'm wearing one right now!")
        if (!choice2("Can I have a flower crown?", true, "Sounds very colourful!", false)) {
            chatPlayer(happy, "Sounds very colourful!")
            chatNpc(happy, "You can come back for a crown whenever you like.")
            return
        }
        chatPlayer(quiz, "Can I have a flower crown?")
        chatNpc(happy, "Of course you can. I'll even give you a lovely rainbow scarf to go with it!")
        val received = give(listOf(Reward.Scarf, Reward.Crown)) ?: return
        for (reward in received) {
            access.mes("You've received a holiday item: <col=ef1020>${reward.displayName}</col>")
        }
        mesbox(if (received.size == 2) "Gilbert hands you a flower crown and rainbow scarf."
            else "Gilbert gives you the items you were missing.")
        chatNpc(happy, "There you go.")
        chatPlayer(happy, "Thank you, I'll wear them with Pride!")
    }

    private suspend fun Dialogue.parade() {
        chatPlayer(quiz, News)
        chatNpc(happy, "There's been a colourful parade through here. The meadow was its starting point.")
        if (choice2("I joined the parade.", true, "Tell me about it.", false)) {
            chatPlayer(happy, "I joined the parade.")
            chatNpc(quiz, "Really? I didn't spot you in that crowd!")
            chatPlayer(laugh, "I was wearing something colourful... along with everyone else!")
        } else {
            chatPlayer(quiz, "Tell me about it.")
            chatNpc(happy, "All the colourful outfits made the crowd look like a moving rainbow.")
        }
        chatNpc(happy, "I'd made a mountain of flower crowns and nearly gave them all away.")
        chatPlayer(quiz, "Will you make more for next time?")
        chatNpc(happy, "If another parade comes through, I'll be ready.")
        chatPlayer(happy, "Perhaps they'll do it every year.")
        chatNpc(happy, "That would give me plenty of time to get ready. I'd better start!")
    }

    private suspend fun Dialogue.reclaim() {
        chatPlayer(quiz, Reclaim)
        chatNpc(happy, "Absolutely! I have a cape, shirt, jumper, flower crown and scarf for you. " +
            "Which would you like?")
        while (true) {
            val selected = choice5("All of them", 0, "Cape", 1, "Shirt", 2, "Jumper", 3, "More", 4)
            val reward = when (selected) {
                0 -> {
                    give(Reward.entries) ?: return
                    chatNpc(happy, "You should have all of the old rainbow items now!")
                    return
                }
                1 -> Reward.Cape
                2 -> Reward.Shirt
                3 -> Reward.Jumper
                else -> when (choice3("Flower Crown", 1, "Scarf", 2, "Previous", 3)) {
                    1 -> Reward.Crown
                    2 -> Reward.Scarf
                    else -> continue
                }
            }
            val received = give(listOf(reward)) ?: return
            if (received.isEmpty()) chatNpc(neutral, "You already own that item.")
            else mesbox("Gilbert hands you a ${reward.displayName.lowercase()}.")
            return
        }
    }

    private suspend fun Dialogue.give(rewards: List<Reward>): List<Reward>? {
        val missing = rewards.filterNot { reward ->
            reward.variants.any { item -> item in player.inv || item in player.worn || item in access.bank }
        }
        if (missing.isNotEmpty()) {
            access.ifClose()
            val transaction = player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                for (reward in missing) add(inventory, reward.item.asRSCM(), 1)
            }
            if (transaction.failure) {
                chatNpc(neutral, "Please make room for ${missing.size} item${if (missing.size == 1) "" else "s"} first.")
                return null
            }
        }
        if (Reward.Crown in rewards) vars[TakenCrown] = 1
        return missing
    }

    internal enum class Reward(val item: String, val displayName: String, variantBase: String = item) {
        Cape("obj.pride24_cape", "Rainbow cape"),
        Shirt("obj.pride24_shirt", "Rainbow crown shirt"),
        Jumper("obj.pride23_jumper", "Rainbow jumper"),
        Crown("obj.pride22_flower_crown", "Flower crown"),
        Scarf("obj.pride17_scarf", "Rainbow scarf", "obj.pride23_scarf");

        val variants = listOf(item) + listOf("bisexual", "asexual", "transgender", "pansexual",
            "nonbinary", "genderqueer", "lesbian", "gay").map { "${variantBase}_$it" }
    }

    internal companion object {
        const val TakenCrown = "varbit.pride22_taken_reward"
        private const val Meadow = "The meadow is looking lovely!"
        private const val News = "Anything happening around here?"
        private const val Reclaim = "Do you have any rainbow items I can claim?"
    }
}
