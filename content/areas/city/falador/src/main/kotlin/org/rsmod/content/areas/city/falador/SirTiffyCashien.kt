package org.rsmod.content.areas.city.falador

import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SirTiffyCashien : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(TiffyNpc) { startDialogue(it.npc) { tiffy() } }
    }

    private suspend fun Dialogue.tiffy() {
        chatPlayer(neutral, "Hello.")
        chatNpc(happy, "What ho, ${address()}. Spiffing day for a walk in the park, what?")
        chatPlayer(confused, "...spiffing?")
        chatNpc(
            happy,
            "Absolutely, top-hole! Well, can't stay and chat all day, dontchaknow! " +
                "Ta-ta for now!",
        )
        chatPlayer(confused, "Erm... goodbye.")
    }

    private fun Dialogue.address(): String =
        if (player.appearance.bodyType == Constants.bodytype_a) "sirrah" else "milady"

    private companion object {
        private const val TiffyNpc = "npc.rd_teleporter_guy"
    }
}
