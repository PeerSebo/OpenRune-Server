package org.rsmod.content.areas.city.portsarim.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PortSarimTownsfolkScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.macro_pirate") { startDialogue(it.npc) { capnHand() } }
        onApNpc1("npc.macro_pirate") {
            if (isWithinDistance(it.npc, 2)) startDialogue(it.npc) { capnHand() } else apRange(2)
        }
        onOpNpc1("npc.jail_guard_sleeping") { startDialogue(it.npc) { sleepingGuard() } }
    }

    private suspend fun Dialogue.capnHand() {
        chatNpc(confused, "Arrr, what do ye want?")
        if (!choice2("What are you doing here?", true, "Nothing, thanks.", false)) {
            chatPlayer(neutral, "Nothing, thanks.")
            chatNpc(sad, "Nothing, eh? Nothing be all I got, anyway.")
            return
        }
        chatPlayer(quiz, "What are you doing here?")
        chatNpc(
            sad,
            "Arrr, it be a sad tale. I used to sail the seven seas, a-lootin' and a-plunderin' " +
                "whatever took my fancy.",
        )
        chatNpc(
            neutral,
            "Then I got a conscience, so I stopped being a pirate, and travelled the world " +
                "giving back some of the loot I'd taken.",
        )
        chatNpc(
            sad,
            "Seems people didn't appreciate me interruptin' their business to give 'em free " +
                "stuff, so now I'm locked up for harassment.",
        )
        chatPlayer(sad, "Oh, I see.")
    }

    private suspend fun Dialogue.sleepingGuard() {
        chatNpc(mesanim("mesanim.sleep"), "Guh... mwww... zzzzz...")
        chatPlayer(neutral, "Maybe I should let him sleep.")
    }
}
