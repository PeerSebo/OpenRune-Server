package org.rsmod.content.areas.city.falador

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Wyson @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(WysonNpc) { startDialogue(it.npc) { wyson() } }
    }

    private suspend fun Dialogue.wyson() {
        chatNpc(
            neutral,
            "I'm the head gardener around here. If you're looking for " +
                "woad leaves, or if you need help with owt, I'm yer man.",
        )
        when (choice2(WantLeaves, 1, NotInterested, 2)) {
            1 -> haggle()
            2 -> {
                chatPlayer(neutral, NotInterested)
                chatNpc(sad, "Fair enough.")
            }
        }
    }

    private suspend fun Dialogue.haggle() {
        chatPlayer(neutral, WantLeaves)
        chatNpc(shifty, "How much are you willing to pay?")
        priceLadder(Offer.entries)
    }

    private suspend fun Dialogue.priceLadder(offers: List<Offer>) {
        val labels = offers.map(Offer::label) + ChangedMind
        val picked = selectOffer(labels)
        val offer = offers.getOrNull(picked - 1)
        if (offer == null) {
            chatPlayer(neutral, ChangedMind)
            return
        }
        chatPlayer(neutral, offer.label)
        when (offer) {
            Offer.Five,
            Offer.Ten -> {
                chatNpc(
                    angry,
                    "No no, that's far too little. Woad leaves are hard to get. " +
                        "I used to have plenty but someone kept stealing them off me!",
                )
                priceLadder(offers - offer)
            }
            Offer.Fifteen -> buyOne()
            Offer.Twenty -> buyPair()
        }
    }

    private suspend fun Dialogue.buyOne() {
        chatNpc(neutral, "Mmmm... okay, that sounds fair.")
        if (!player.invTakeFee(fee = Offer.Fifteen.coins)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        player.invAddOrDrop(objRepo, WoadLeaf)
        objbox(WoadLeaf, "You buy a woad leaf from Wyson.")
        chatPlayer(happy, "Thanks.")
        chatNpc(neutral, "I'll be around if you have any more gardening needs.")
    }

    private suspend fun Dialogue.buyPair() {
        chatNpc(happy, "Okay, that's more than fair.")
        chatNpc(happy, "Here, have two, you're a generous person.")
        if (!player.invTakeFee(fee = Offer.Twenty.coins)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        player.invAddOrDrop(objRepo, WoadLeaf, count = 2)
        objbox(WoadLeaf, "You buy a pair of woad leaves from Wyson.")
        chatPlayer(happy, "Thanks.")
    }

    private suspend fun Dialogue.selectOffer(labels: List<String>): Int =
        when (labels.size) {
            3 -> choice3(labels[0], 1, labels[1], 2, labels[2], 3)
            4 -> choice4(labels[0], 1, labels[1], 2, labels[2], 3, labels[3], 4)
            5 -> choice5(labels[0], 1, labels[1], 2, labels[2], 3, labels[3], 4, labels[4], 5)
            else -> error("Unexpected woad leaf offer count: ${labels.size} ($labels)")
        }

    private enum class Offer(val coins: Int, val label: String) {
        Five(5, "How about five coins?"),
        Ten(10, "How about ten coins?"),
        Fifteen(15, "How about 15 coins?"),
        Twenty(20, "How about 20 coins?"),
    }

    private companion object {
        private const val WysonNpc = "npc.wyson"
        private const val WoadLeaf = "obj.woadleaf"

        private const val WantLeaves = "Yes please, I need woad leaves."
        private const val NotInterested = "Sorry, but I'm not interested."
        private const val ChangedMind = "Actually, I've changed my mind."
    }
}
