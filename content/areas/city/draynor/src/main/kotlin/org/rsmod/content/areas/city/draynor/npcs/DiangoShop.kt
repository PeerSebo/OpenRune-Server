package org.rsmod.content.areas.city.draynor.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.draynor.npcs.DiangoHolidayItems.Companion.openHolidayItems
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoShop @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aprilfoolshorsesalesman") { startDialogue(it.npc) { diango() } }
        onOpNpc3("npc.aprilfoolshorsesalesman") { openToyShop() }
    }

    private suspend fun Dialogue.diango() {
        chatNpc(
            happy,
            "Howdy there partner! Want to see my spinning plates? Or did ya want a holiday item " +
                "back?",
        )
        val topic =
            menu(
                "Spinning plates?" to 0,
                "I'd like to check holiday items please!" to 3,
                "What else are you selling?" to 1,
                "I'm fine, thanks." to 2,
            )
        when (topic) {
            0 -> {
                chatPlayer(quiz, "Spinning plates?")
                chatNpc(
                    happy,
                    "That's right. There's a funny story behind them, their shipment was held up " +
                        "by thieves.",
                )
                chatNpc(
                    laugh,
                    "The crate was marked 'Dragon Plates'. Apparently they thought it was some " +
                        "kind of armour, when really it's just a plate with a dragon on it!",
                )
                access.openToyShop()
            }
            1 -> {
                chatPlayer(quiz, "What else are you selling?")
                access.openToyShop()
            }
            3 -> {
                chatPlayer(happy, "I'd like to check holiday items please!")
                chatNpc(happy, "Sure thing, let me just see what you're missing.")
                access.openHolidayItems()
            }
            else -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    private fun ProtectedAccess.openToyShop() {
        shops.open(
            player = player,
            title = "Diango's Toy Store",
            shopInv = "inv.aprilfoolshorseshop",
            buyPercentage = 50.0,
            sellPercentage = 150.0,
            changePercentage = 2.0,
        )
    }
}
