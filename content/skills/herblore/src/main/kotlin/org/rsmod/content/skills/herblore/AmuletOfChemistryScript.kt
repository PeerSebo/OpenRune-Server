package org.rsmod.content.skills.herblore

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AmuletOfChemistryScript : PluginScript() {

    override fun ScriptContext.startup() {
        val checkable = listOf(BASE_AMULET, "obj.amulet_of_chemistry_imbued_charged")
        checkable.forEach { amulet ->
            onOpHeld3(amulet) { openOptions(it.slot) }
            onOpWorn2(amulet) { openOptionsWorn() }
        }
        onOpHeld4(BASE_AMULET) { breakAmulet(it.slot) }
    }

    private suspend fun ProtectedAccess.openOptions(slot: Int) {
        val amulet = inv[slot]?.takeIf { amulet -> AmuletOfChemistry.AMULET_TYPES.any { amulet.isType(it) } } ?: return
        showOptionsMenu(alchemistsAmulet = !amulet.isType(BASE_AMULET))
    }

    private suspend fun ProtectedAccess.openOptionsWorn() {
        if (!AmuletOfChemistry.isWearing(player)) {
            return
        }
        showOptionsMenu(alchemistsAmulet = AmuletOfChemistry.isAlchemistsAmulet(player))
    }

    private suspend fun ProtectedAccess.showOptionsMenu(alchemistsAmulet: Boolean) {
        val maxCharges = if (alchemistsAmulet) AmuletOfChemistry.ALCHEMIST_MAX_CHARGES else AmuletOfChemistry.MAX_CHARGES
        val charges = player.chemistryChargeCount(maxCharges)
        if (alchemistsAmulet) {
            mesbox("Alchemist's amulet ($charges charges).")
            return
        }
        val stop = player.shouldStopBrewingOnChemistryCrumble()
        val stopOnCrumble = choice2(
            choice1 = "Continue making potions when the amulet crumbles.",
            result1 = false,
            choice2 = "Stop making potions when the amulet crumbles.",
            result2 = true,
            title = "Amulet of chemistry ($charges charges)"
        )
        if (stopOnCrumble) {
            player.chemistryStopOnCrumble = true
            if (!stop) {
                mes("You will stop making potions when your amulet crumbles.")
            }
        } else {
            player.chemistryStopOnCrumble = false
            if (stop) {
                mes("You will continue making potions when your amulet crumbles.")
            }
        }
    }

    private suspend fun ProtectedAccess.breakAmulet(slot: Int) {
        inv[slot]?.takeIf { it.isType(BASE_AMULET) } ?: return
        val confirmed = choice2(
            choice1 = "Break the amulet of chemistry?",
            result1 = true,
            choice2 = "Cancel.",
            result2 = false,
            title = "This will destroy the amulet."
        )
        if (!confirmed) {
            return
        }
        if (invDel(inv, BASE_AMULET, 1, slot = slot).failure) {
            return
        }
        AmuletOfChemistry.resetCharges(player)
        mes("You destroy the amulet. Your next amulet of chemistry will have 5 charges.")
    }

    private companion object {
        const val BASE_AMULET = "obj.amulet_of_chemistry"
    }
}
