package org.rsmod.content.generic.locs.banks

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocCategory1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.content.interfaces.bank.confirmAndExchangeBanknote
import org.rsmod.content.interfaces.bank.exchangeBankCurrency
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankChest : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocCategory1("category.bank_chest") { openChest() }
        onOpLocCategoryU("category.bank_chest") { unnote(it.invSlot, it.objType) }

        // Bank chest categories must be present in the packed SERVER config archive.
        val chest = ServerCacheManager.getObject("loc.blast_bank_chest".asRSCM(RSCMType.LOC))
        val category = "category.bank_chest".asRSCM(RSCMType.CATEGORY)
        check(chest != null && chest.category == category) {
            "Packed SERVER cache lost the bank-chest category: loc.blast_bank_chest decodes " +
                "category=${chest?.category} (expected $category). Back up and rebuild the cache."
        }
    }

    private suspend fun ProtectedAccess.openChest() {
        arriveDelay()
        tryOpenBank()
    }

    private suspend fun ProtectedAccess.unnote(invSlot: Int, objType: ItemServerType) {
        if (exchangeBankCurrency(invSlot, objType)) return
        if (!objType.isCert) {
            mes("Nothing interesting happens.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have any inventory space.")
            return
        }
        startDialogue { confirmAndExchangeBanknote(invSlot, objType) }
    }
}
