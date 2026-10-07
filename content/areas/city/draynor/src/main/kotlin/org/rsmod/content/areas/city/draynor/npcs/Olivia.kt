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

class Olivia @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.seed_merchant") { startDialogue(it.npc) { olivia() } }
        onOpNpc3("npc.seed_merchant") { openSeedShop() }
    }

    private suspend fun Dialogue.olivia() {
        chatNpc(happy, "Would you like to trade in seeds?")
        val topic = menu("Yes" to 0, "No" to 1, "Where do I get rarer seeds from?" to 2)
        when (topic) {
            0 -> access.openSeedShop()
            1 -> chatPlayer(neutral, "No, thanks.")
            else -> {
                chatPlayer(quiz, "Where do I get rarer seeds from?")
                chatNpc(
                    neutral,
                    "The Master Farmers usually carry a few rare seeds around with them, " +
                        "although I don't know if they'd want to part with them for any price " +
                        "to be honest.",
                )
            }
        }
    }

    private fun ProtectedAccess.openSeedShop() {
        shops.open(
            player = player,
            title = "Draynor Seed Market",
            shopInv = "inv.seed_stall",
            buyPercentage = 60.0,
            sellPercentage = 120.0,
            changePercentage = 3.0,
        )
    }
}
