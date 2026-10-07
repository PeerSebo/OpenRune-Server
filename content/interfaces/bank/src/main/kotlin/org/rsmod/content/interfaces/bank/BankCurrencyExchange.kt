package org.rsmod.content.interfaces.bank

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.inv.isType

suspend fun ProtectedAccess.exchangeBankCurrency(invSlot: Int, objType: ItemServerType): Boolean {
    val coins = "obj.coins".asRSCM()
    val platinum = "obj.platinum".asRSCM()
    val fromCoins = when (objType.id) {
        coins -> true
        platinum -> false
        else -> return false
    }
    if (!inv[invSlot].isType(objType)) return true

    val origin = player.coords
    val sourceName = if (fromCoins) "coins" else "platinum tokens"
    val action = if (fromCoins) "receive" else "exchange for coins"
    val requested = numberDialog("How many platinum tokens would you like to $action? (1 token = 1,000 coins)")
    // Input can be cancelled or forged, and inventories can change while the prompt is open.
    if (requested <= 0 || player.coords != origin) return true
    val source = inv[invSlot]
    if (!source.isType(objType)) return true

    val destination = if (fromCoins) platinum else coins
    val destinationCount = inv.filterNotNull { it.id == destination }.sumOf { it.count.toLong() }
    val capacity = Int.MAX_VALUE.toLong() - destinationCount
    val available = if (fromCoins) source.count.toLong() / COINS_PER_TOKEN else source.count.toLong()
    val tokenCapacity = if (fromCoins) capacity else capacity / COINS_PER_TOKEN
    val tokens = minOf(requested.toLong(), available, tokenCapacity)
    if (tokens <= 0) {
        mes(if (fromCoins && available == 0L) "You need at least 1,000 coins to exchange."
            else "You cannot hold any more ${if (fromCoins) "platinum tokens" else "coins"}.")
        return true
    }

    val spent = (if (fromCoins) tokens * COINS_PER_TOKEN else tokens).toInt()
    val received = (if (fromCoins) tokens else tokens * COINS_PER_TOKEN).toInt()
    // Delete and insert atomically; deleting first allows a full currency stack to free its own slot.
    val exchange = player.invTransaction(inv) {
        val inventory = select(inv)
        delete { from = inventory; obj = objType.id; strictCount = spent; strictSlot = invSlot }
        insert { into = inventory; obj = destination; strictCount = received }
    }
    if (exchange.failure) {
        mes("You don't have enough inventory space for the exchange.")
        return true
    }
    val receivedName = if (fromCoins) {
        if (received == 1) "platinum token" else "platinum tokens"
    } else "coins"
    val spentName = if (!fromCoins && spent == 1) "platinum token" else sourceName
    mes("The bank exchanges ${spent.formatAmount} $spentName for ${received.formatAmount} $receivedName.")
    return true
}

private const val COINS_PER_TOKEN = 1_000L
