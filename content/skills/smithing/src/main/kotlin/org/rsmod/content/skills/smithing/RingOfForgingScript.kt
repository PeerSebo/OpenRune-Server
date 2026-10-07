package org.rsmod.content.skills.smithing

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RingOfForgingScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld4(RingOfForging.ITEM) { breakRing(it.slot) }
        onOpWorn2(RingOfForging.ITEM) { checkChargesWorn() }
    }

    private fun ProtectedAccess.checkChargesWorn() {
        if (!RingOfForging.isWearing(player)) {
            return
        }
        RingOfForging.checkCharges(player)
    }

    private suspend fun ProtectedAccess.breakRing(slot: Int) {
        inv[slot]?.takeIf { it.isType(RingOfForging.ITEM) } ?: return
        val confirmed =
            choice2(
                choice1 = "Break the ring of forging?",
                result1 = true,
                choice2 = "Cancel.",
                result2 = false,
                title = "This will destroy the ring.",
            )
        if (!confirmed) {
            return
        }
        if (invDel(inv, RingOfForging.ITEM, 1, slot = slot).failure) {
            return
        }
        RingOfForging.prepareBreak(player)
        mes(
            "You destroy the ring. Your next ring of forging will have " +
                "${RingOfForging.MAX_CHARGES} charges."
        )
    }
}
