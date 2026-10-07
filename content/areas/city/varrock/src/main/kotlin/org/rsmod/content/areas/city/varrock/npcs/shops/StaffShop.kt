package org.rsmod.content.areas.city.varrock.npcs.shops

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

// TODO: Gate battlestave stock on What Lies Below progress.
class StaffShop @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.zaff") { shopDialogue(it.npc) }
        onOpNpc3("npc.zaff") { player.openStaffShop(it.npc) }
    }

    private fun Player.openStaffShop(npc: Npc) {
        shops.open(this, npc, "Zaff's Superior Staffs!", "inv.staffshop")
    }

    private suspend fun ProtectedAccess.shopDialogue(npc: Npc) = startDialogue(npc) { shopKeeper(npc) }

    private suspend fun Dialogue.shopKeeper(npc: Npc) {
        chatNpc(happy, "Would you like to buy or sell some staffs?")

        val choice = choice2("Yes, please!", 1, "No, thank you.", 2)

        when (choice) {
            1 -> {
                chatPlayer(happy, "Yes, please!")
                player.openStaffShop(npc)
            }
            2 -> {
                chatPlayer(neutral, "No, thank you.")
                chatNpc(
                    happy,
                    "Well, 'stick' your head in again if you change your mind.",
                )
                chatPlayer(
                    happy,
                    "Huh, terrible pun! You just can't get the 'staff' these days!",
                )
            }
        }
    }
}
