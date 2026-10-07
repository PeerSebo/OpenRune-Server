package org.rsmod.content.areas.city.portsarim.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Prices mirror each shop's inv config, in tenths of a percent. */
private enum class PortSarimShop(
    val npc: String,
    val title: String,
    val inv: String,
    val sell: Double,
    val buy: Double,
    val change: Double,
) {
    Fishing("npc.gerrant", "Gerrant's Fishy Business.", "inv.fishingshop", 100.0, 70.0, 1.0),
    Food("npc.wydin", "Food Store", "inv.wydinstore", 100.0, 70.0, 1.0),
    Jewellery("npc.grum", "Grum's Gold Exchange.", "inv.goldshop", 100.0, 70.0, 2.0),
    Magic("npc.betty", "Betty's Magic Emporium.", "inv.magicshop", 100.0, 60.0, 0.1),
    Battleaxes("npc.brian", "Brian's Battleaxe Bazaar.", "inv.battleaxeshop", 100.0, 55.0, 1.0),
}

class PortSarimShopkeepersScript @Inject constructor(private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (shop in listOf(PortSarimShop.Fishing, PortSarimShop.Battleaxes)) {
            onOpNpc3(shop.npc) { player.openShop(shop) }
        }
        onTradeAcross(PortSarimShop.Jewellery.npc) { player.openShop(PortSarimShop.Jewellery) }
        onTalkAcross(PortSarimShop.Jewellery.npc) { startDialogue(it) { grum() } }
        onOpNpc1(PortSarimShop.Fishing.npc) { startDialogue(it.npc) { gerrant() } }
        onOpNpc1(PortSarimShop.Battleaxes.npc) { startDialogue(it.npc) { brian() } }
    }

    private suspend fun Dialogue.gerrant() {
        chatNpc(
            neutral,
            "Welcome! You can buy fishing equipment at my store. We'll also buy anything you " +
                "catch off you.",
        )
        if (choice2("Let's see what you've got then.", true, "Sorry, I'm not interested.", false)) {
            chatPlayer(neutral, "Let's see what you've got then.")
            player.openShop(PortSarimShop.Fishing)
        } else {
            chatPlayer(neutral, "Sorry, I'm not interested.")
        }
    }

    private suspend fun Dialogue.grum() {
        chatNpc(happy, "Would you like to buy or sell some gold jewellery?")
        if (choice2("Yes please.", true, "No, I'm not that rich.", false)) {
            player.openShop(PortSarimShop.Jewellery)
            return
        }
        chatPlayer(sad, "No, I'm not that rich.")
        chatNpc(angry, "Get out then! We don't want any riff-raff in here.")
    }

    private suspend fun Dialogue.buyPinkDye() {
        chatPlayer(quiz, "Could I buy some more pink dye?")
        chatNpc(happy, "Of course. It's $PINK_DYE_PRICE coins per bottle.")
        val inv = access.inv
        if (inv.count("obj.coins") < PINK_DYE_PRICE) {
            chatPlayer(sad, "Oh, I don't have enough money with me.")
            return
        }
        if (inv.freeSpace() == 0 && inv.count("obj.coins") > PINK_DYE_PRICE) {
            chatPlayer(sad, "Oh, I don't have enough room for any.")
            return
        }
        if (!choice2("Sounds good.", true, "Actually, I'll pass.", false)) {
            chatPlayer(neutral, "Actually, I'll pass.")
            chatNpc(neutral, "Fair enough.")
            return
        }
        chatPlayer(happy, "Sounds good.")
        if (access.invDel(inv, "obj.coins", PINK_DYE_PRICE).failure) {
            return
        }
        access.invAdd(inv, PINK_DYE)
        objbox(
            PINK_DYE,
            zoom = 400,
            "Betty sells you some pink dye in exchange for $PINK_DYE_PRICE coins.",
        )
    }

    private suspend fun Dialogue.brian() {
        if (choice2("So, are you selling something?", true, "'Ello.", false)) {
            chatPlayer(quiz, "So, are you selling something?")
            chatNpc(happy, "Yep, take a look at these great axes!")
            player.openShop(PortSarimShop.Battleaxes)
        } else {
            chatPlayer(happy, "'Ello.")
            chatNpc(happy, "'Ello!")
        }
    }

    private fun Player.openShop(shop: PortSarimShop) {
        shops.open(
            player = this,
            title = shop.title,
            shopInv = shop.inv,
            buyPercentage = shop.buy,
            sellPercentage = shop.sell,
            changePercentage = shop.change,
        )
    }

    private enum class BettyTopic {
        Wares,
        Dye,
        Leave,
    }

    private companion object {
        const val HAND_IN_THE_SAND = "quest_handinthesand"
        const val PINK_DYE = "obj.handsand_pink_dye"
        const val PINK_DYE_PRICE = 20
    }
}
