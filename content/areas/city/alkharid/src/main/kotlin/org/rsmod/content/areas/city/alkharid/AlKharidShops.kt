package org.rsmod.content.areas.city.alkharid

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AlKharidShops @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin { MorrisaneGossipState.migrate(player) }
        onOpNpc1(Zeke) { talkToZeke(it.npc) }
        onOpNpc3(Zeke) { player.openScimitarShop(it.npc) }

        onOpNpc1(LouieLegs) { talkToLouie(it.npc) }
        onOpNpc3(LouieLegs) { player.openLegsShop(it.npc) }

        onOpNpc1(Ranael) { talkToRanael(it.npc) }
        onOpNpc3(Ranael) { player.openSkirtShop(it.npc) }

        for (clerk in GeneralStoreClerks) {
            onOpNpc1(clerk) { talkToGeneralStore(it.npc) }
            onOpNpc3(clerk) { player.openGeneralStore(it.npc) }
        }

        onOpNpc1(KebabSeller) { talkToKarim(it.npc) }

        onOpNpc1(Dommik) { talkToDommik(it.npc) }
        onOpNpc3(Dommik) { player.openCraftingShop(it.npc) }
    }

    private fun Player.openCraftingShop(npc: Npc) {
        shops.open(this, npc, "Dommik's Crafting Store", "inv.craftingshop2")
    }

    private suspend fun ProtectedAccess.talkToDommik(npc: Npc) {
        startDialogue(npc) { dommikDialogue(npc) }
    }

    private suspend fun Dialogue.dommikDialogue(npc: Npc) {
        chatNpc(happy, "Do you want to buy any crafting equipment?")

        val browse = choice2("Yes please.", true, "No thanks.", false)

        if (!browse) {
            chatPlayer(neutral, "No thanks.")
            return
        }

        chatPlayer(neutral, "Yes please.")
        access.player.openCraftingShop(npc)
    }

    private fun Player.openScimitarShop(npc: Npc) {
        shops.open(this, npc, "Zeke's Superior Scimitars", "inv.scimitarshop")
    }

    private suspend fun ProtectedAccess.talkToZeke(npc: Npc) {
        startDialogue(npc) { zekeDialogue(npc) }
    }

    private suspend fun Dialogue.zekeDialogue(npc: Npc) {
        chatNpc(happy, "A thousand greetings to you, friend.")

        val option =
            choice3(
                "Do you want to trade?",
                1,
                "Nice cloak.",
                2,
                "Could you sell me a dragon scimitar?",
                3,
            )

        when (option) {
            1 -> {
                chatPlayer(quiz, "Do you want to trade?")
                chatNpc(happy, "Yes, certainly. I deal in scimitars.")
                access.player.openScimitarShop(npc)
            }
            2 -> {
                chatPlayer(neutral, "Nice cloak.")
                chatNpc(neutral, "Thank you.")
            }
            3 -> {
                chatPlayer(quiz, "Could you sell me a dragon scimitar?")
                chatNpc(shocked, "A dragon scimitar? A DRAGON scimitar?")
                chatNpc(laugh, "No way, friend!")
                chatNpc(
                    laugh,
                    "The banana-brained nitwits who make them would never dream of selling any to " +
                        "me.",
                )
                chatNpc(
                    neutral,
                    "Seriously, you'll be a monkey's uncle before you'll ever hold a dragon " +
                        "scimitar.",
                )
                chatPlayer(happy, "Oh well, thanks anyway.")
                chatNpc(neutral, "Perhaps you'd like to take a look at my stock?")

                val browse = choice2("Yes please, Zeke.", true, "Not today, thank you.", false)
                if (browse) {
                    access.player.openScimitarShop(npc)
                } else {
                    chatPlayer(neutral, "Not today, thank you.")
                }
            }
        }
    }

    private fun Player.openLegsShop(npc: Npc) {
        shops.open(this, npc, "Louie's Armoured Legs Bazaar", "inv.legsshop")
    }

    private suspend fun ProtectedAccess.talkToLouie(npc: Npc) {
        startDialogue(npc) { louieDialogue(npc) }
    }

    private suspend fun Dialogue.louieDialogue(npc: Npc) {
        chatNpc(neutral, "Hey, wanna buy some armour?")

        val browse = choice2("What have you got?", true, "No, thank you.", false)

        if (!browse) {
            chatPlayer(neutral, "No, thank you.")
            return
        }

        chatPlayer(neutral, "What have you got?")
        chatNpc(happy, "I provide items to help you keep your legs!")
        access.player.openLegsShop(npc)
    }

    private fun Player.openSkirtShop(npc: Npc) {
        shops.open(this, npc, "Ranael's Super Skirt Store", "inv.skirtshop")
    }

    private suspend fun ProtectedAccess.talkToRanael(npc: Npc) {
        startDialogue(npc) { ranaelDialogue(npc) }
    }

    private suspend fun Dialogue.ranaelDialogue(npc: Npc) {
        chatNpc(
            neutral,
            "Do you want to buy any armoured skirts? Designed especially for ladies who like to " +
                "fight.",
        )

        val browse = choice2("Yes please.", true, "No thank you, that's not my scene.", false)

        if (!browse) {
            chatPlayer(neutral, "No thank you, that's not my scene.")
            return
        }

        chatPlayer(neutral, "Yes please.")
        access.player.openSkirtShop(npc)
    }

    private fun Player.openGeneralStore(npc: Npc) {
        shops.open(this, npc, "Al Kharid General Store", "inv.generalshop1")
    }

    private suspend fun ProtectedAccess.talkToGeneralStore(npc: Npc) {
        startDialogue(npc) { generalStoreDialogue(npc) }
    }

    private suspend fun Dialogue.generalStoreDialogue(npc: Npc) {
        chatNpc(happy, "Can I help you at all?")

        val toldStory = MorrisaneGossipState.heardGossip(access.player)

        val option =
            if (toldStory) {
                if (choice2("Yes please. What are you selling?", 1, "No thanks.", 2) == 1) 1 else 2
            } else {
                choice3(
                    "Yes please. What are you selling?",
                    1,
                    "No thanks.",
                    2,
                    "What do you think of Ali Morrisane?",
                    3,
                )
            }

        when (option) {
            1 -> {
                chatPlayer(neutral, "Yes please. What are you selling?")
                access.player.openGeneralStore(npc)
            }
            2 -> {
                chatPlayer(neutral, "No thanks.")
            }
            3 -> {
                morrisaneGossip()
                MorrisaneGossipState.setHeardGossip(access.player, true)

                chatNpc(neutral, "Can I interest you in anything?")
                val browse = choice2("Yes please. What are you selling?", true, "No thanks.", false)
                if (browse) {
                    chatPlayer(neutral, "Yes please. What are you selling?")
                    access.player.openGeneralStore(npc)
                } else {
                    chatPlayer(neutral, "No thanks.")
                }
            }
        }
    }

    private suspend fun Dialogue.morrisaneGossip() {
        chatPlayer(neutral, "What do you think of Ali Morrisane?")
        chatNpc(neutral, "I don't trust him, not after he's sent his men round to threaten us.")
        chatNpc(
            worried,
            "Did you know, we've had four different thugs come round and threaten us if we don't " +
                "join them.",
        )
        chatNpc(worried, "The owner's had hammers, rakes, swords and even scissors waved at him!")
        chatPlayer(quiz, "What will you do about it?")
        chatNpc(happy, "We'll carry on as normal with the store. Business as usual!")
    }

    private suspend fun ProtectedAccess.talkToKarim(npc: Npc) {
        startDialogue(npc) { karimDialogue() }
    }

    private suspend fun Dialogue.karimDialogue() {
        chatNpc(happy, "Would you like to buy a nice kebab? Only one gold.")

        val buy = choice2("I think I'll give it a miss.", false, "Yes please.", true)

        if (!buy) {
            chatPlayer(neutral, "I think I'll give it a miss.")
            return
        }

        chatPlayer(neutral, "Yes please.")

        if (access.invTotal(access.inv, Coins) < KebabPrice) {
            chatPlayer(sad, "Oh dear, I don't seem to have a gold coin.")
            return
        }

        if (access.invDel(access.inv, Coins, KebabPrice).failure) {
            return
        }
        if (access.invAdd(access.inv, Kebab, 1).failure) {
            access.invAdd(access.inv, Coins, KebabPrice)
            return
        }

        access.spam("You buy a kebab.")
    }

    private companion object {
        private const val Zeke = "npc.zeke"
        private const val LouieLegs = "npc.louie_legs"
        private const val Ranael = "npc.ranael"
        private const val KebabSeller = "npc.kebab_seller"
        private const val Dommik = "npc.dommik"

        private val GeneralStoreClerks = listOf("npc.generalshopkeeper3", "npc.generalassistant3")

        private const val Coins = "obj.coins"
        private const val Kebab = "obj.kebab"

        private const val KebabPrice = 1
    }
}
