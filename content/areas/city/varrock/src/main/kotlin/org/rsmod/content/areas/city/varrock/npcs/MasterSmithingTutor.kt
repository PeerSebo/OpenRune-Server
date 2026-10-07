package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSmithingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MasterSmithingTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_smithing_master") { talkTo(it.npc) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(
            happy,
            "Hello there! I'm the Master smithing tutor. I teach smiths who already know the " +
                "basics how to work the finer metals.",
        )

        giveHammerIfRequired()

        when {
            player.baseSmithingLvl >= 99 ->
                chatNpc(
                    happy,
                    "You've mastered the anvil - there's nothing left for me to teach you.",
                )
            player.baseSmithingLvl >= 38 ->
                chatNpc(
                    neutral,
                    "You're smithing the finer metals well. Keep at it, and you'll be working " +
                        "runite before long.",
                )
            player.baseSmithingLvl >= 21 ->
                chatNpc(
                    neutral,
                    "You've got the basics down. Practise on mithril and adamant, and your " +
                        "smithing will come along nicely.",
                )
            else ->
                chatNpc(
                    neutral,
                    "Smelt your ore into bars at a furnace, then use a hammer on an anvil to " +
                        "smith it into equipment.",
                )
        }
    }

    private suspend fun Dialogue.giveHammerIfRequired() {
        if (Hammer in player.inv || Hammer in player.worn) {
            return
        }
        if (player.invAdd(player.inv, Hammer).success) {
            chatNpc(happy, "Here, take a hammer - you'll need one to smith at an anvil.")
        }
    }

    private companion object {
        const val Hammer = "obj.hammer"
    }
}
