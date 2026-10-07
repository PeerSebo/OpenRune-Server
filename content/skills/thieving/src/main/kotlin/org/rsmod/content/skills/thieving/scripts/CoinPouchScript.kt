package org.rsmod.content.skills.thieving.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.skills.thieving.configs.CoinPouches
import org.rsmod.game.inv.InvObj
import org.rsmod.objtx.Transaction
import org.rsmod.objtx.TransactionInventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CoinPouchScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (pouch in CoinPouches.all) {
            onOpHeld1(pouch) { openAll() }
            onOpHeld2(pouch) { open(pouch) }
        }
    }

    private fun ProtectedAccess.openAll() {
        val pouches = CoinPouches.all.associateWith { invTotal(inv, it) }.filterValues { it > 0 }
        if (pouches.isEmpty()) {
            mes("You have no coin pouches to open.")
            return
        }
        val total = pouches.entries.sumOf { (pouch, count) -> payout(pouch, count) }
        if (exchange(pouches, total)) reportAll(total)
    }

    private fun ProtectedAccess.open(pouch: String) {
        if (invTotal(inv, pouch) == 0) return
        val total = payout(pouch, 1)
        if (exchange(mapOf(pouch to 1), total)) report(1, total)
    }

    private fun ProtectedAccess.payout(pouch: String, count: Int): Long {
        val value = CoinPouches.coinsPerPouch[pouch] ?: return 0
        return (0 until count).sumOf { random.of(value).toLong() }
    }

    private fun ProtectedAccess.exchange(pouches: Map<String, Int>, coins: Long): Boolean {
        if (coins !in 1..Int.MAX_VALUE.toLong()) {
            mes("You cannot hold that many coins.")
            return false
        }
        val result = player.invTransaction(inv) {
            exchangeCoinPouches(
                select(inv), pouches.mapKeys { (obj, _) -> obj.asRSCM(RSCMType.OBJ) },
                "obj.coins".asRSCM(RSCMType.OBJ), coins.toInt(),
            )
        }
        if (!result.success) mes("You don't have room for the coins. Your pouches have not been opened.")
        return result.success
    }

    private fun ProtectedAccess.reportAll(coins: Long) {
        soundSynth(random.pick(CoinPouches.openJingles))
        spam("You open all of the pouches and receive a total of ${coins.formatAmount} coins.")
    }

    private fun ProtectedAccess.report(count: Int, coins: Long) {
        soundSynth(random.pick(CoinPouches.openJingles))
        val plural = if (count == 1) "pouch" else "pouches"
        spam("You open $count coin $plural and find ${coins.formatAmount} coins.")
    }
}

internal fun Transaction<InvObj>.exchangeCoinPouches(
    inventory: TransactionInventory<InvObj>, pouches: Map<Int, Int>, coins: Int, payout: Int,
) {
    require(pouches.isNotEmpty() && pouches.values.all { it > 0 } && payout > 0)
    for ((pouch, count) in pouches) delete { from = inventory; obj = pouch; strictCount = count }
    insert { into = inventory; obj = coins; strictCount = payout }
}
