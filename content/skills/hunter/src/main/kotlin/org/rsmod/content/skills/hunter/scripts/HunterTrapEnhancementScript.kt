package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.hunter.HunterTrapEnhancements
import org.rsmod.content.skills.hunter.HunterTrapManager
import org.rsmod.content.skills.hunter.TrapState
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.content.skills.hunter.configs.TrapLocs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HunterTrapEnhancementScript @Inject constructor(private val manager: HunterTrapManager) : PluginScript() {
    override fun ScriptContext.startup() {
        val locs = listOf(TrapLocs.SNARE_SET, TrapLocs.BOX_SET, TrapLocs.DEADFALL_SET) +
            NetTrapColour.entries.flatMap { listOf(TrapLocs.saplingBent(it.setSuffix), TrapLocs.saplingArmed(it.setSuffix)) }
        for (loc in locs) onOpLocU(loc) {
            val trap = manager.ownedTrapAt(player, it.loc.coords, TrapState.SET)
            if (trap == null) { mes("You can only prepare your own armed trap."); return@onOpLocU }
            val item = it.objType.internalName
            if (HunterTrapEnhancements.apply(player, trap, item)) {
                if (item in HunterTrapEnhancements.torches) spam("You smoke the trap.")
                else { anim("seq.hunting_baiting_small_traps"); spam("You add bait to the trap.") }
            } else mes("You cannot use that to prepare this trap.")
        }
    }
}
