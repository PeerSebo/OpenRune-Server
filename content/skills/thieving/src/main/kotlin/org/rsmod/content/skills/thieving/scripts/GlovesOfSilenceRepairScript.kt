package org.rsmod.content.skills.thieving.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GlovesOfSilenceRepairScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(ThievingNpcs.GLOVES_OF_SILENCE, DARK_KEBBIT_FUR) { repair() }
    }

    private fun ProtectedAccess.repair() {
        if (player.craftingLvl < CRAFTING_LEVEL_REQ) {
            mes("You need a Crafting level of $CRAFTING_LEVEL_REQ to repair these gloves.")
            return
        }
        if (invTotal(inv, KNIFE) == 0) {
            mes("You need a knife to repair these gloves.")
            return
        }
        if (invTotal(inv, NEEDLE) == 0) {
            mes("You need a needle to repair these gloves.")
            return
        }
        if (vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT] == 0) {
            mes("Your gloves of silence don't need repairing.")
            return
        }

        val deleted = invDel(inv, DARK_KEBBIT_FUR, 1, THREAD, 1)
        if (!deleted.success) {
            mes("You need some thread to repair these gloves.")
            return
        }

        vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT] = 0
        mes("You repair your gloves of silence.")
    }

    private companion object {
        private const val DARK_KEBBIT_FUR = "obj.huntingbeast_silent_fur"
        private const val KNIFE = "obj.knife"
        private const val NEEDLE = "obj.needle"
        private const val THREAD = "obj.thread"
        private const val CRAFTING_LEVEL_REQ = 64
    }
}
