package org.rsmod.content.minigames.blastfurnace.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceOreCount
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Rev-240 chimney has Check only; no Empty op.
// TODO: verify and spawn the 12 dynamic blast_furnace_<ore> display NPCs.
class MeltingPotScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blast_furnace_chimney") { checkPot() }
    }

    private suspend fun ProtectedAccess.checkPot() {
        val held = ORE_ITEMS.filter { player.blastFurnaceOreCount(it) > 0 }
        if (held.isEmpty()) {
            mes("The melting pot is empty.")
            return
        }
        val readout =
            held.joinToString(", ") { ore ->
                "${player.blastFurnaceOreCount(ore)} x ${oreLabel(ore)}"
            }
        mesbox("The melting pot contains: $readout.")
    }

    private fun oreLabel(internal: String): String = internal.removePrefix("obj.").replace('_', ' ')

    private companion object {
        val ORE_ITEMS =
            listOf(
                "obj.copper_ore",
                "obj.tin_ore",
                "obj.iron_ore",
                "obj.mithril_ore",
                "obj.adamantite_ore",
                "obj.runite_ore",
                "obj.silver_ore",
                "obj.gold_ore",
                "obj.coal",
                "obj.lead_ore",
                "obj.nickel_ore",
                "obj.smithing_catalyst",
                "obj.perfect_gold_ore",
            )
    }
}
