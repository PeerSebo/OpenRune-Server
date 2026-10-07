package org.rsmod.content.minigames.giantsfoundry

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.*
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsService
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryMouldScript @Inject constructor(private val interfaces: FoundryInterfaces) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton(FoundryInterfaces.MOULD_TABS) {
            FoundryInterfaces.mouldTab(it.comsub)?.let { tab ->
                VarPlayerIntMapSetter.set(player, TAB, tab)
                interfaces.refreshMould(player)
            }
        }
        onIfModalButton(FoundryInterfaces.MOULD_LIST) {
            val index = FoundryInterfaces.mouldIndex(it.comsub, 11) ?: return@onIfModalButton
            if (!editable()) return@onIfModalButton
            val part = FoundryMouldPart.entries.getOrNull(player.vars[TAB])
                ?: return@onIfModalButton
            val mould = FoundryData.mould(part, index) ?: return@onIfModalButton
            if (!FoundryGame.canUseMould(player, mould)) {
                if (player.smithingLvl < mould.level) {
                    mes("You need a Smithing level of ${mould.level} to use this mould.")
                } else {
                    mes("You must purchase this mould from Kovac first.")
                }
            } else {
                VarPlayerIntMapSetter.set(player, part.selectedVarbit, index)
            }
            interfaces.refreshMould(player)
        }
        onIfModalButton(FoundryInterfaces.MOULD_RESET) {
            if (!editable()) return@onIfModalButton
            for (part in FoundryMouldPart.entries) {
                VarPlayerIntMapSetter.set(player, part.selectedVarbit, 0)
            }
            interfaces.refreshMould(player)
        }
        onIfModalButton(FoundryInterfaces.MOULD_SET) {
            if (!editable()) return@onIfModalButton
            val moulds = FoundryGame.selectedMoulds(player)
            if (moulds.size != 3 || moulds.any { !FoundryGame.canUseMould(player, it) }) {
                mes("Choose a forte, blade and tip mould that you can use.")
                interfaces.refreshMould(player)
                return@onIfModalButton
            }
            player.foundryMouldState = 1
            SleepingGiantsService.refreshTutorial(player)
            interfaces.closeMould(player)
        }
    }

    private fun ProtectedAccess.editable(): Boolean {
        if (!FoundryGame.isInside(player.coords) ||
            !FoundryGame.hasCommission(player) ||
            player.foundryPourPending ||
            player.foundryStartQuality > 0 ||
            player.foundryMouldState >= 2
        ) {
            interfaces.closeMould(player)
            return false
        }
        return true
    }

    companion object {
        const val TAB = "varbit.giants_foundry_mould_current_tab"
    }
}
