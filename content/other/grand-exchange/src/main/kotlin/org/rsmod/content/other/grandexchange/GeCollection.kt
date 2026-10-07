package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.min
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.game.entity.Player

enum class GeCollectionMode {
    Default,
    Item,
    Note,
    Bank,
}

internal class GeCollection(private val exchange: GeExchange) {
    private val coinsId: Int by lazy { GeConfig.COINS.asRSCM(RSCMType.OBJ) }

    fun collect(player: Player, offer: GeOffer, box: Int, mode: GeCollectionMode) {
        if (box == GeConfig.COLLECT_BOX_COINS) {
            collectCoins(player, offer, mode == GeCollectionMode.Bank)
        } else {
            collectItems(player, offer, mode)
        }
    }

    private fun collectCoins(player: Player, offer: GeOffer, toBank: Boolean) {
        val count = offer.collectCoins
        if (count <= 0) {
            return
        }
        val success = if (toBank) depositBank(player, coinsId, count)
            else player.invAdd(player.inv, coinsId, count).success
        if (!success) {
            player.mes(if (toBank) "Your bank is full." else "You don't have enough inventory space.")
            return
        }
        exchange.takeCoins(offer, count)
    }

    private fun collectItems(player: Player, offer: GeOffer, mode: GeCollectionMode) {
        val count = offer.collectItems
        if (count <= 0) {
            return
        }
        val item = ServerCacheManager.getItemOrDefault(offer.item)
        if (mode == GeCollectionMode.Bank) {
            if (!depositBank(player, item.id, count)) {
                player.mes("Your bank is full.")
                return
            }
            exchange.takeItems(offer, count)
            return
        }
        val asNote =
            when (mode) {
                GeCollectionMode.Note -> item.canCert
                GeCollectionMode.Item -> false
                else -> item.canCert && !item.isStackable && count > 1
            }
        val take =
            if (asNote || item.isStackable) {
                count
            } else {
                min(count, player.inv.freeSpace())
            }
        if (take <= 0) {
            player.mes("You don't have enough inventory space.")
            return
        }
        val result = player.invAdd(player.inv, item.id, take, cert = asNote)
        if (!result.success) {
            player.mes("You don't have enough inventory space.")
            return
        }
        exchange.takeItems(offer, take)
    }

    private fun depositBank(player: Player, item: Int, count: Int): Boolean {
        val bank = player.invMap.getOrPut("inv.bank")
        return player.invTransaction(bank) {
            val destination = select(bank)
            insert {
                into = destination
                obj = item
                strictCount = count
                capacity = player.bankCapacity.coerceIn(0, bank.size)
            }
        }.success
    }
}
