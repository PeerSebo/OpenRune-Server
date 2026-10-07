package org.rsmod.content.areas.city.falador

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorParkGardeners : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(GardenerNpc) { startDialogue(it.npc) { gardener() } }
        onOpNpc1(GardenerNpc2) { startDialogue(it.npc) { gardener() } }
    }

    private suspend fun Dialogue.gardener() {
        chatPlayer(quiz, "Hello.")
        chatNpc(
            angry,
            "Oi'm busy. If tha' wants owt, tha' can go find Wyson. He's ta boss " +
                "'round here. And KEEP YER TRAMPIN' FEET ORFF MA' FLOWERS!",
        )
        chatPlayer(shocked, "Right...")
    }

    private companion object {
        private const val GardenerNpc = "npc.falador_gardener"
        private const val GardenerNpc2 = "npc.falador_gardener2"
    }
}
