package org.rsmod.content.areas.misc.miningguild.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GuildShopkeepersScript @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(HENDOR) { talk(it.npc) { hendor(it.npc) } }
        onOpNpc1(YARSUL) { talk(it.npc) { yarsul(it.npc) } }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc, conversation: suspend Dialogue.() -> Unit) =
        startDialogue(npc) { conversation() }

    private suspend fun Dialogue.hendor(npc: Npc) {
        chatNpc(happy, "Hello there! If you have any ores to trade I'm always buying.")
        while (true) {
            when (
                choice3(
                    "Let's trade.",
                    1,
                    "Why don't you ever restock your shop?",
                    2,
                    "Goodbye.",
                    3,
                )
            ) {
                1 -> {
                    shops.openConfigured(player, npc, "inv.mguild_oreshop")
                    return
                }
                2 -> {
                    chatPlayer(quiz, "Why don't you ever restock your shop?")
                    chatNpc(neutral, "The only ores I sell are the ones that are sold to me.")
                    chatNpc(neutral, "Anything else?")
                }
                else -> {
                    chatPlayer(happy, "See you later.")
                    chatNpc(happy, "Until next time.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.yarsul(npc: Npc) {
        chatNpc(
            happy,
            "Good day to you, welcome to my pickaxe shop. Are you interested in making a purchase?",
        )
        if (choice2("Yes please.", true, "No thanks.", false)) {
            shops.openConfigured(player, npc, "inv.pickaxeshop")
            return
        }
        chatPlayer(happy, "No thanks.")
        chatNpc(neutral, "Suit yourself. I'll be here if you change your mind.")
    }

    private companion object {
        const val HENDOR = "npc.mguild_oreseller"
        const val YARSUL = "npc.mguild_pickaxeseller"
    }
}
