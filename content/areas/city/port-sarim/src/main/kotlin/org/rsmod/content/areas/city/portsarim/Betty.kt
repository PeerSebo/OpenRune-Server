package org.rsmod.content.areas.city.portsarim

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Betty @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BettyNpc) { talkToBetty(it.npc) }
        onOpNpc3(BettyNpc) { player.openShop(it.npc) }
    }

    private suspend fun ProtectedAccess.talkToBetty(npc: Npc) {
        startDialogue(npc) { bettyDialogue(npc) }
    }

    private suspend fun Dialogue.bettyDialogue(npc: Npc) {
        chatNpc(happy, "Hello there. Welcome to my magic emporium.")
        when (choice2("Can I see your wares?", 1, "Sorry, I'm not into magic.", 2)) {
            1 -> {
                chatPlayer(happy, "Can I see your wares?")
                chatNpc(happy, "Of course.")
                access.player.openShop(npc)
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I'm not into magic.")
                chatNpc(neutral, "Well, if you see anyone who is, please send them my way.")
            }
        }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, ShopTitle, ShopInv)
    }

    private companion object {
        private const val BettyNpc = "npc.betty"

        private const val ShopTitle = "Betty's Magic Emporium."
        private const val ShopInv = "inv.magicshop"
    }
}
