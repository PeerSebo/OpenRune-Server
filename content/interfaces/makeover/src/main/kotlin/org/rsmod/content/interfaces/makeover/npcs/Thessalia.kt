package org.rsmod.content.interfaces.makeover.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.interfaces.makeover.MakeoverPage
import org.rsmod.content.interfaces.makeover.openMakeover
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Thessalia @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        for (thessalia in THESSALIA_IDS) {
            onOpNpc1(thessalia) { op -> startDialogue(op.npc) { talkTo(op.npc) } }
            onOpNpc3(thessalia) { op -> player.openFineClothesShop(op.npc) }
            onOpNpc4(thessalia) { op -> startDialogue(op.npc) { makeoverMenu() } }
        }
    }

    private suspend fun Dialogue.makeoverMenu() {
        val choice =
            choice4(
                "Change your top.",
                MakeoverPage.Top,
                "Change your bottoms.",
                MakeoverPage.Bottoms,
                "Change your wristwear.",
                MakeoverPage.Wrist,
                "Cancel.",
                null,
                title = "A makeover is free!",
            )
        choice?.let { access.openMakeover(it) }
    }

    private suspend fun Dialogue.talkTo(npc: Npc) {
        chatNpc(happy, "Do you want to buy any fine clothes?")

        val tier1 = choice2("What have you got?", Pick.Browse, "No, thank you.", Pick.Decline)
        if (tier1 == Pick.Decline) {
            decline()
            return
        }

        chatPlayer(quiz, "What have you got?")
        chatNpc(
            happy,
            "Well, I have a number of fine pieces of clothing on sale or, if you prefer, I can " +
                "offer you an exclusive, total- clothing makeover?",
        )

        val tier2 =
            choice3(
                "Tell me more about this makeover.",
                Pick.Makeover,
                "I'd just like to buy some clothes.",
                Pick.Shop,
                "No, thank you.",
                Pick.Decline,
            )

        when (tier2) {
            Pick.Shop -> {
                player.openFineClothesShop(npc)
                return
            }
            Pick.Decline -> {
                decline()
                return
            }
            else -> Unit
        }

        chatPlayer(quiz, "Tell me more about this makeover.")
        chatNpc(happy, "Certainly!")
        chatNpc(
            happy,
            "Here at Thessalia's fine clothing boutique, we offer a unique service where we will " +
                "totally revamp your outfit to your choosing.",
        )
        chatNpc(
            neutral,
            "It's on the house, completely free! Tired of always wearing the same old outfit, " +
                "day in, day out? This is the service for you!",
        )
        chatNpc(
            quiz,
            "So what do you say? Interested? We can change either your top, your bottoms or your " +
                "wristwear!",
        )

        val tier3 =
            choice5(
                "I'd like to change my top please.",
                Pick.Top,
                "I'd like to change my bottoms please.",
                Pick.Bottoms,
                "I'd just like to buy some clothes.",
                Pick.Shop,
                "I'd like to change my wristwear.",
                Pick.Wrist,
                "No, thank you.",
                Pick.Decline,
            )

        when (tier3) {
            Pick.Top -> access.openMakeover(MakeoverPage.Top)
            Pick.Bottoms -> access.openMakeover(MakeoverPage.Bottoms)
            Pick.Wrist -> access.openMakeover(MakeoverPage.Wrist)
            Pick.Shop -> player.openFineClothesShop(npc)
            else -> decline()
        }
    }

    private suspend fun Dialogue.decline() {
        chatPlayer(neutral, "No, thank you.")
        chatNpc(neutral, "Well, please return if you change your mind.")
    }

    private fun Player.openFineClothesShop(npc: Npc) {
        shops.open(this, npc, "Thessalia's Fine Clothes.", "inv.clotheshop")
    }

    private enum class Pick {
        Browse,
        Makeover,
        Shop,
        Top,
        Bottoms,
        Wrist,
        Decline,
    }

    private companion object {
        val THESSALIA_IDS = listOf("npc.thessalia", "npc.thessalia_normal")
    }
}
