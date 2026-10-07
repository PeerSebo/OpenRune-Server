package org.rsmod.content.skills.thieving.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ThievingEquipmentScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld3(ThievingNpcs.GLOVES_OF_SILENCE) { checkGloves() }
        onOpWorn2(ThievingNpcs.GLOVES_OF_SILENCE) { checkGloves() }
        onOpHeld3(ThievingNpcs.DODGY_NECKLACE) { checkNecklace(inv[it.slot]) }
        onOpWorn2(ThievingNpcs.DODGY_NECKLACE) { checkNecklace(worn[it.slot]) }
        onOpHeld4(ThievingNpcs.DODGY_NECKLACE) { confirmBreak(it.slot) }
    }

    private fun ProtectedAccess.checkGloves() {
        val remaining = ThievingRates.SILENT_GLOVES_BREAK_THRESHOLD -
            vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT]
        mes(when {
            remaining >= ThievingRates.SILENT_GLOVES_BREAK_THRESHOLD -> "Your gloves are new."
            remaining > 33 -> "Your gloves are in good condition."
            remaining > 19 -> "Your gloves are starting to look quite shabby."
            remaining > 9 -> "Your gloves are starting to need repair."
            remaining > 1 -> "Your gloves are in need of repair!"
            remaining > 0 -> "Your gloves are about to fall apart!"
            else -> "Your gloves of silence have worn out. They will need repairing before they work again."
        })
    }

    private fun ProtectedAccess.checkNecklace(necklace: InvObj?) {
        if (!necklace.isType(ThievingNpcs.DODGY_NECKLACE)) return
        val charges = ThievingGear.necklaceCharges(checkNotNull(necklace))
        mes("Your dodgy necklace has $charges charge${if (charges == 1) "" else "s"} left.")
    }

    private suspend fun ProtectedAccess.confirmBreak(slot: Int) {
        val necklace = inv[slot] ?: return
        if (!necklace.isType(ThievingNpcs.DODGY_NECKLACE)) return
        startDialogue {
            val choice = choice2("Yes, break it.", 1, "No, keep it.", 2)
            if (choice == 1 && access.inv[slot] == necklace &&
                !access.invDel(access.inv, ThievingNpcs.DODGY_NECKLACE, 1, slot = slot).failure) {
                access.mes("You break the dodgy necklace and it crumbles to dust.")
            }
        }
    }
}
