package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseFarmingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MartinTheMasterFarmer : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.martin_the_master_farmer") { startDialogue(it.npc) { martin() } }
    }

    private suspend fun Dialogue.martin() {
        val topic =
            menu(
                buildList {
                    add("Ask about the Skillcape of Farming." to 0)
                    if (Quest.get(FAIRYTALE_II)?.questState(player)?.isCompleted == true) {
                        add("Ask about the quest." to 1)
                    }
                }
            )
        if (topic == 1) return martinQuest()
        if (player.baseFarmingLvl < MAX_LEVEL) {
            chatPlayer(quiz, "What is that cape you're wearing?")
            chatNpc(
                happy,
                "This is a Skillcape of Farming, isn't it incredible? It's a symbol of my ability " +
                    "as the finest farmer in the land and wearing it increases my herb yield!",
            )
            return
        }
        val hood = menu("Skillcape" to false, "Hood" to true)
        if (hood) {
            chatPlayer(quiz, "May I have another hood for my cape, please?")
            if (access.inv.isFull()) return
            access.invAdd(access.inv, FARMING_HOOD)
            objbox(FARMING_HOOD, "Martin hands you another hood for your skillcape.")
            return
        }
        chatPlayer(quiz, "Can I buy a Skillcape of Farming from you?")
        chatNpc(
            happy,
            "Of course, fellow farmer. If you wear this cape you'll receive increased yields from " +
                "your herbs. That'll be 99000 coins.",
        )
        if (!menu("I'm not paying that!" to false, "Sure, not many people own one." to true)) {
            chatPlayer(angry, "I'm not paying that.")
            chatNpc(
                neutral,
                "No skin off my teeth, but if you change your mind, the price will still be the " +
                    "same.",
            )
            return
        }
        chatPlayer(happy, "Sure, not many people own one.")
        val inv = access.inv
        if (inv.count(COINS) < SKILLCAPE_PRICE) {
            chatPlayer(sad, "But, unfortunately, I don't have enough money with me.")
            chatNpc(neutral, "Well, come back and see me when you do.")
            return
        }
        if (inv.freeSpace() < 2) {
            chatNpc(
                neutral,
                "Unfortunately all Skillcapes are only available with a free hood, it's part of a " +
                    "skill promotion deal; buy one get one free, you know. So you'll need to free " +
                    "up some inventory space before I can sell you one.",
            )
            return
        }
        if (access.invDel(inv, COINS, SKILLCAPE_PRICE).failure) return
        access.invAdd(inv, FARMING_CAPE)
        access.invAdd(inv, FARMING_HOOD)
        chatNpc(happy, "That's true; us Master Farmers are a unique breed.")
    }

    private suspend fun Dialogue.martinQuest() {
        chatPlayer(
            happy,
            "Okay, this time I've really solved your problems! I've woken up the Fairy Queen!",
        )
        chatNpc(
            neutral,
            "Hmm, right. You'll forgive me if I reserve judgement on this until I actually have " +
                "some crops grow.",
        )
        chatPlayer(
            neutral,
            "Of course. The problem was the Godfather, he wanted to take control of Zanaris " +
                "himself, so he didn't give Fairy Nuff the Queen's secateurs!",
        )
        chatNpc(
            angry,
            "Godfather...Fairy Nuff...the Queen, I don't know what you're talking about and I " +
                "don't care. I just want my crops to start growing again. Come back and claim " +
                "your reward once they've had a chance to grow.",
        )
    }

    private companion object {
        const val COINS = "obj.coins"
        const val MAX_LEVEL = 99
        const val SKILLCAPE_PRICE = 99000
        const val FARMING_CAPE = "obj.skillcape_farming"
        const val FARMING_HOOD = "obj.skillcape_farming_hood"
        const val FAIRYTALE_II = "quest_fairytale2"
    }
}
