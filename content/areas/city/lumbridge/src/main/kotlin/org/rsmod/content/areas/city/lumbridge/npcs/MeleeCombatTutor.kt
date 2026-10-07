package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MeleeCombatTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_melee") { talkTo(it.npc) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(
            happy,
            "Hello there! I'm Harlan, the Melee combat tutor. I can teach you the basics of " +
                "fighting with a weapon and shield.",
        )

        giveEquipmentIfRequired()

        chatNpc(
            neutral,
            "Simply wield a weapon, then click on a monster to attack it. Keep an eye on your " +
                "hitpoints, and don't take on anything too tough while you're starting out.",
        )
    }

    private suspend fun Dialogue.giveEquipmentIfRequired() {
        if (!AideTutorSupplyEligibility.melee(player)) {
            chatNpc(
                neutral,
                "Free training swords and shields are only available while your base Attack, " +
                    "Strength and Defence levels are all below ${AideTutorSupplyEligibility.LevelLimit}. " +
                    "You're ready to obtain your own equipment.",
            )
            return
        }

        val hasWeapon =
            TrainingSword in player.inv || TrainingSword in player.worn ||
                TrainingSword in access.bank
        val hasShield =
            TrainingShield in player.inv || TrainingShield in player.worn ||
                TrainingShield in access.bank
        if (hasWeapon && hasShield) {
            return
        }

        // The greeting is still a modal after Continue; close it before inventory mutations.
        access.ifClose()
        var gaveWeapon = false
        var gaveShield = false
        if (!hasWeapon && player.invAdd(player.inv, TrainingSword).success) {
            gaveWeapon = true
        }
        if (!hasShield && player.invAdd(player.inv, TrainingShield).success) {
            gaveShield = true
        }

        val grantedText =
            when {
                gaveWeapon && gaveShield -> "a training sword and shield"
                gaveWeapon -> "a training sword"
                gaveShield -> "a training shield"
                else -> null
            }
        if (grantedText != null) {
            chatNpc(
                happy,
                "Here, take $grantedText so you can practise on the rats and chickens nearby.",
            )
        }
    }

    private companion object {
        const val TrainingSword = "obj.aide_shortsword"
        const val TrainingShield = "obj.aide_shield"
    }
}
