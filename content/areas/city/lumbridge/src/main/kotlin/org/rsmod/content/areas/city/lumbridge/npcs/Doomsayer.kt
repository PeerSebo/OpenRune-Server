package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Leaf ops are op1 `Talk-to` and op3 `Toggle warnings`.
class Doomsayer : PluginScript() {
    override fun ScriptContext.startup() {
        for (doomsayer in listOf("npc.cws_doomsayer", "npc.doomsayer_normal")) {
            onOpNpc1(doomsayer) { talkTo(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { warning() }
    }

    private suspend fun Dialogue.warning() {
        chatNpc(
            neutral,
            "Beware, adventurer! There is danger in every corner of this land, and it grows the " +
                "further you stray from home.",
        )
        chatPlayer(quiz, "What kind of danger?")
        chatNpc(
            neutral,
            "Wild beasts, foul magic, and worse still - other adventurers who would see you dead " +
                "for the contents of your pack. Keep your wits about you, and heed my warnings.",
        )
    }
}
