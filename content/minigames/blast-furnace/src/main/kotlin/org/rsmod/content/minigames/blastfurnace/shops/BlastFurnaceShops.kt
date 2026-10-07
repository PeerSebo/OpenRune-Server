package org.rsmod.content.minigames.blastfurnace.shops

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

class BlastFurnaceShops @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.smithing_guild_ore_seller") { shopDialogue(it.npc, ORDAN_TITLE, ORDAN_INV) }
        onOpNpc3("npc.smithing_guild_ore_seller") { player.openShop(it.npc, ORDAN_TITLE, ORDAN_INV) }

        onOpNpc1("npc.smithing_guild_buyer") { shopDialogue(it.npc, JORZIK_TITLE, JORZIK_INV) }
        onOpNpc3("npc.smithing_guild_buyer") { player.openShop(it.npc, JORZIK_TITLE, JORZIK_INV) }
    }

    private fun Player.openShop(npc: Npc, title: String, inv: String) {
        shops.open(this, npc, title, inv)
    }

    private suspend fun ProtectedAccess.shopDialogue(npc: Npc, title: String, inv: String) =
        startDialogue(npc) { shopKeeper(npc, title, inv) }

    private suspend fun Dialogue.shopKeeper(npc: Npc, title: String, inv: String) {
        chatNpc(happy, "Can I interest you in some trade?")
        val choice = choice2("Yes please.", 1, "No thanks.", 2)
        if (choice == 1) {
            player.openShop(npc, title, inv)
        } else {
            chatPlayer(neutral, "No thanks.")
        }
    }

    private companion object {
        const val ORDAN_TITLE = "Ore seller."
        const val ORDAN_INV = "inv.smithing_guild_ore_seller"
        const val JORZIK_TITLE = "Armour store."
        const val JORZIK_INV = "inv.smithing_guild_buyer"
    }
}
