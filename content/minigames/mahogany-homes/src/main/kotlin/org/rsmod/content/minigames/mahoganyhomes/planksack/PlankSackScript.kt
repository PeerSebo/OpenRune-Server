package org.rsmod.content.minigames.mahoganyhomes.planksack

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlankSackScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1("obj.plank_sack") { fillSack() }
        onOpHeld3("obj.plank_sack") { checkSack() }
        onOpHeld4("obj.plank_sack") { emptySack() }
    }

    private fun ProtectedAccess.fillSack() {
        if (PlankSack.freeSpace(player) <= 0) {
            mes("Your plank sack is full.")
            return
        }

        var totalDeposited = 0
        for (type in PlankType.entries) {
            val free = PlankSack.freeSpace(player)
            if (free <= 0) break

            val held = inv.physicalCount(type.objInternal)
            if (held <= 0) continue

            val toDeposit = minOf(held, free)
            if (invDel(inv, type.objInternal, toDeposit, ignoreVirtualStorage = true).success) {
                PlankSack.depositUpTo(player, type, toDeposit)
                totalDeposited += toDeposit
            }
        }

        if (totalDeposited <= 0) {
            mes("You don't have any planks to fill your plank sack with.")
            return
        }
        mes("You fill the plank sack with $totalDeposited planks.")
    }

    private fun ProtectedAccess.emptySack() {
        val total = PlankSack.totalStored(player)
        if (total <= 0) {
            mes("Your plank sack is empty.")
            return
        }

        var totalEmptied = 0
        for (type in PlankType.entries) {
            val stored = PlankSack.stored(player, type)
            if (stored <= 0) continue

            val added =
                invAdd(inv, type.objInternal, stored, strict = false, ignoreVirtualStorage = true).completed()
            if (added <= 0) continue

            PlankSack.withdrawUpTo(player, type, added)
            totalEmptied += added
        }

        if (totalEmptied <= 0) {
            mes("You don't have any free space in your inventory.")
            return
        }
        mes("You empty $totalEmptied planks from the plank sack.")
    }

    private fun ProtectedAccess.checkSack() {
        val total = PlankSack.totalStored(player)
        if (total <= 0) {
            mes("Your plank sack is empty.")
            return
        }

        val breakdown =
            PlankType.entries
                .mapNotNull { type ->
                    val stored = PlankSack.stored(player, type)
                    if (stored > 0) "$stored ${type.name.lowercase()}" else null
                }
                .joinToString(", ")
        mes("Your plank sack contains $total of ${PlankSack.CAPACITY} planks: $breakdown.")
    }
}
