package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LeaguesTutor : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3("npc.league_tutor_normal") { openTrade(it.npc) }
        onOpNpc4("npc.league_tutor_normal") { claimTrophies(it.npc) }
    }

    private suspend fun ProtectedAccess.openTrade(npc: Npc) {
        startDialogue(npc) {
            chatPlayer(quiz, "Do you have anything to trade?")
            if (!player.members) {
                chatNpc(
                    neutral,
                    "Unfortunately I'm unable to trade with non-members. I'll be happy to help " +
                        "once you are one.",
                )
                return@startDialogue
            }
            notImplemented()
        }
    }

    private suspend fun ProtectedAccess.claimTrophies(npc: Npc) {
        startDialogue(npc) {
            chatPlayer(quiz, "I'd like to claim some League Trophies.")
            if (!player.members) {
                chatNpc(
                    neutral,
                    "Sadly, only Members are eligible to claim League Trophies. I'll be happy to " +
                        "help once you become one.",
                )
                return@startDialogue
            }
            notImplemented()
        }
    }

    private suspend fun Dialogue.notImplemented() {
        chatNpc(neutral, "I've nothing for you at the moment. Come back another time.")
    }
}
