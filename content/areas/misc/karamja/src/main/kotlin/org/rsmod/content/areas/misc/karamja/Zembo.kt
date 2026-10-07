package org.rsmod.content.areas.misc.karamja

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Zembo @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.zembo") { talkToZembo(it.npc) }
        onOpNpc3("npc.zembo") { openShop(it.npc) }
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, "Karamja Wines, Spirits, and Beers.", "inv.boozeshop")
    }

    private suspend fun ProtectedAccess.talkToZembo(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Welcome to the Karamja bar. What can I get you?")
            when (choice2("Let's see what you've got.", 1, "Nothing, thanks.", 2)) {
                1 -> {
                    chatPlayer(happy, "Let's see what you've got.")
                    access.openShop(npc)
                }
                2 -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }
}
