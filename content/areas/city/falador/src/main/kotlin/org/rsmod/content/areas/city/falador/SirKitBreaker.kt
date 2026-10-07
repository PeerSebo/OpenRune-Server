package org.rsmod.content.areas.city.falador

import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SirKitBreaker : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.pride24_kit_glade") {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello, there!")
                chatNpc(happy, "Hello! The name's Kit. Sir Kit Breaker.")
                chatPlayer(happy, "Sir Kit? That's a nice name!")
                chatNpc(happy, "Thank you! I chose it myself!")
                chatPlayer(quiz, "... Thinking about it, I chose mine as well. I'm ${player.displayName}, by the way.")
                chatNpc(happy, "Very nice to meet you!")
                chatPlayer(happy, "What do you do around here?")
                chatNpc(happy, "I'm helping Gilbert with a project of his, on behalf of the White knights. " +
                    "It will take us a while but I can let you know when we're closer to completion!")
                chatPlayer(happy, "Sounds good!")
            }
        }
    }
}
