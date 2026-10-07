package org.rsmod.content.areas.city.draynor.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Fortunato @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.rag_wine_merchant") { startDialogue(it.npc) { fortunato() } }
        onOpNpc3("npc.rag_wine_merchant") { openWineShop() }
    }

    private suspend fun Dialogue.fortunato() {
        chatNpc(neutral, "Can I help you at all?")
        chatNpc(happy, "Ah! Good afternoon to you. I take it you have come for a refill?")
        if (menu("Yes" to true, "Not today" to false)) {
            chatPlayer(neutral, "Yes.")
            access.openWineShop()
        } else {
            chatPlayer(neutral, "Not today.")
        }
    }

    private fun ProtectedAccess.openWineShop() {
        shops.open(
            player = player,
            title = "Wine Shop.",
            shopInv = "inv.wine_vinegar_merchant",
            buyPercentage = 60.0,
            sellPercentage = 100.0,
            changePercentage = 2.0,
        )
    }
}
