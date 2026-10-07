package org.rsmod.content.interfaces.trading

import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerAccountCommit

internal enum class TradeStage { Offer, Confirm, Committing, Closed }

internal class TradeSession(val players: List<Player>, val opened: Int) {
    val origins = players.map { it.coords }
    val offers = players.map { TradeOffer(it.inv.objs.toList()) }
    val accepted = BooleanArray(2)
    var stage = TradeStage.Offer
    var revision = 0L
    var confirmationRevision = -1L
    var confirmOpened = 0
    var changedAt = opened
    var removed = false
    var transitioning = false
    var commit: PlayerAccountCommit? = null
    fun side(player: Player) = players.indexOfFirst { it === player }.also { check(it >= 0) }
    fun other(player: Player) = players[1 - side(player)]
    fun unchanged() = players.indices.all { players[it].inv.objs.toList() == offers[it].original }
    fun changed(now: Int, removal: Boolean) {
        revision++
        changedAt = now
        accepted.fill(false)
        removed = removed || removal
    }
    fun accept(player: Player): Boolean {
        val side = side(player)
        if (accepted[side]) return false
        accepted[side] = true
        return accepted.all { it }
    }
}
