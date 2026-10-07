package org.rsmod.content.interfaces.trading

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.ironman.isSoloIronman
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.GameMessage
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.*
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Singleton
@OptIn(org.rsmod.annotations.InternalApi::class)
class TradeManager @Inject constructor(
    private val saves: TradePersistence,
    private val events: EventBus,
    private val prices: MarketPrices,
) : PlayerPostTickHook {
    override val runsDuringAccountCommit: Boolean get() = true
    private data class Request(val target: Player, val expires: Int, val sent: Int)
    private val requests = IdentityHashMap<Player, Request>()
    private val sessions = IdentityHashMap<Player, TradeSession>()
    internal fun session(player: Player): TradeSession? = sessions[player]
    fun owns(player: Player): Boolean = sessions.containsKey(player)
    fun examineOther(player: Player, slot: Int, objId: Int?) {
        val session = sessions[player] ?: return
        val item = session.offers[1 - session.side(player)].display().getOrNull(slot) ?: return
        if (item.id == objId) player.mes(ServerCacheManager.getItem(item.id)?.examine ?: "")
    }

    fun request(player: Player, other: Player) {
        if (player === other || IronmanRestrictions.blockTrade(player, other)) return
        if (!available(player) || !available(other) || sessions.containsKey(player) || sessions.containsKey(other) ||
            player.level != other.level || player.coords.chebyshevDistance(other.coords) > 1) {
            player.mes("Other player is busy at the moment.", ChatType.Trade)
            return
        }
        if (other.tradeSocial?.accepts(player.displayName) != true || player.tradeSocial?.accepts(other.displayName) != true) return
        val now = player.currentMapClock
        val incoming = requests[other]
        if (incoming?.target === player && incoming.expires >= now) {
            requests.remove(player); requests.remove(other)
            open(player, other)
            return
        }
        val previous = requests[player]
        if (previous != null && now - previous.sent < 5) return
        requests[player] = Request(other, now + 100, now)
        player.mes("Sending trade offer...", ChatType.Trade)
        GameMessage.requestMes(other, "${player.displayName} wishes to trade with you.", player.displayName, ChatType.TradeReq)
    }

    private fun available(player: Player): Boolean = !player.loggingOut && !player.pendingLogout && !player.manualLogout && !player.forceDisconnect &&
        !player.pendingShutdown && !player.clientDisconnected.get() && !player.hidden &&
        player.pendingAccountCommit == null && player.vars["varbit.cutscene_status"] == 0 &&
        player.vars["varp.lastcombat"] + 10 < player.currentMapClock && !player.isAccessProtected

    private fun open(first: Player, second: Player) {
        val session = TradeSession(listOf(first, second), first.currentMapClock)
        for (player in session.players) {
            player.clearPendingAction(events)
            player.resetFaceEntity()
            sessions[player] = session
        }
        try {
            for (player in session.players) {
                player.ifOpenMainSidePair("interface.trademain", "interface.tradeside", -1, -1, events)
                ClientScripts.interfaceInvInit(player, player.inv, "component.tradeside:side_layer", 4, 7,
                    op1 = "Offer<col=ff9040>", op2 = "Offer-5<col=ff9040>", op3 = "Offer-10<col=ff9040>",
                    op4 = "Offer-All<col=ff9040>", op5 = "Offer-X<col=ff9040>")
                player.ifSetEvents("component.tradeside:side_layer", 0 until player.inv.size, *ITEM_OPS)
                player.ifSetEvents("component.trademain:your_offer", 0 until player.inv.size, *ITEM_OPS)
                player.ifSetEvents("component.trademain:other_offer", 0 until player.inv.size, IfEvent.Op10)
                flags(player, false)
                player.ifSetText("component.trademain:title", "Trading With: ${session.other(player).displayName}")
                player.ifSetText("component.trademain:status", "")
            }
            refresh(session)
        } catch (failure: Exception) {
            cancel(session)
            throw failure
        }
    }

    suspend fun button(access: ProtectedAccess, action: TradeScreenAction, slot: Int, op: IfButtonOp, objId: Int?) {
        val player = access.player
        val session = sessions[player] ?: return
        if (session.stage == TradeStage.Committing || session.stage == TradeStage.Closed) return
        val now = player.currentMapClock
        if (now <= session.opened || !valid(session)) { if (!valid(session)) cancel(session); return }
        when (action) {
            TradeScreenAction.Offer, TradeScreenAction.Remove -> {
                if (session.stage != TradeStage.Offer) return
                val offer = session.offers[session.side(player)]
                val item = if (action == TradeScreenAction.Offer) offer.original.getOrNull(slot)
                    else offer.display().getOrNull(slot)
                if (item == null || item.id != objId) return
                if (op == IfButtonOp.Op10) { access.mes(ServerCacheManager.getItem(item.id)?.examine ?: ""); return }
                if (!TradeItems.allowed(item.id)) { access.mes("You can't trade that item."); return }
                val quantity = when (op) {
                    IfButtonOp.Op1 -> 1
                    IfButtonOp.Op2 -> 5
                    IfButtonOp.Op3 -> 10
                    IfButtonOp.Op4 -> Int.MAX_VALUE
                    IfButtonOp.Op5 -> access.countDialog("Enter amount:")
                    else -> return
                }
                if (sessions[player] !== session || session.stage != TradeStage.Offer || !valid(session) || quantity <= 0) return
                if (action == TradeScreenAction.Offer) {
                    if (!offer.addMatching(slot, quantity)) return
                } else if (!offer.removeDisplay(slot, quantity)) return
                session.changed(player.currentMapClock, action == TradeScreenAction.Remove)
                for (member in session.players) {
                    member.ifSetText("component.trademain:status", "")
                    flags(member, session.removed)
                }
                refresh(session)
            }
            TradeScreenAction.AcceptOffer -> {
                if (session.stage != TradeStage.Offer || now <= session.changedAt) return
                if (plan(session) == null) { player.mes("There isn't enough inventory space to complete this trade."); return }
                val both = session.accept(player)
                status(session, player, "component.trademain:status")
                if (both) confirm(session)
            }
            TradeScreenAction.AcceptConfirm -> {
                if (session.stage != TradeStage.Confirm || now <= session.confirmOpened || session.revision != session.confirmationRevision) return
                val both = session.accept(player)
                status(session, player, "component.tradeconfirm:title")
                if (both) commit(session)
            }
            TradeScreenAction.Decline -> cancel(session)
        }
    }

    private fun valid(session: TradeSession): Boolean = session.unchanged() && session.players.indices.all { index ->
        val player = session.players[index]
        !player.loggingOut && !player.pendingLogout && !player.manualLogout && !player.forceDisconnect && !player.clientDisconnected.get() && !player.pendingShutdown &&
            !player.hidden && !player.isSoloIronman && player.tradeSocial?.accepts(session.other(player).displayName) == true &&
            player.pendingAccountCommit == null && player.coords == session.origins[index] &&
            player.vars["varbit.cutscene_status"] == 0 && player.vars["varp.lastcombat"] + 10 < player.currentMapClock
    }

    private fun plan(session: TradeSession) = TradePlan.build(session.offers[0], session.offers[1],
        TradeItems::allowed, { ServerCacheManager.getItem(it)?.isStackable == true })

    private fun confirm(session: TradeSession) {
        session.stage = TradeStage.Confirm
        session.confirmationRevision = session.revision
        session.confirmOpened = session.players.first().currentMapClock
        session.accepted.fill(false)
        session.transitioning = true
        try {
            for (player in session.players) {
                player.ifCloseSub("interface.tradeside", events)
                player.ifOpenMainModal("interface.tradeconfirm", events)
                player.ifSetText("component.tradeconfirm:title", "Are you sure you want to make this trade?")
                player.ifSetText("component.tradeconfirm:tradeopponent", "Trading with:<br>${session.other(player).displayName}")
                player.ifSetText("component.tradeconfirm:you_will_give", "You are about to give:<br>(Value: ${value(session.offers[session.side(player)])} coins)")
                player.ifSetText("component.tradeconfirm:you_will_receive", "In return you will receive:<br>(Value: ${value(session.offers[1 - session.side(player)])} coins)")
            }
        } finally { session.transitioning = false }
    }

    private fun commit(session: TradeSession) {
        val plan = plan(session) ?: run { cancel(session); return }
        val audit = mapOf("kind" to "player_trade", "names" to session.players.map { it.displayName },
            "offers" to session.offers.map { offer -> offer.items().map { item ->
                mapOf("obj" to dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.OBJ, item.id),
                    "count" to item.count, "vars" to item.vars)
            } })
        session.stage = TradeStage.Committing
        for ((player, items) in session.players.zip(listOf(plan.first, plan.second))) write(player, items)
        val group = saves.save(session.players[0], session.players[1], audit)
        if (group == null) {
            for ((player, offer) in session.players.zip(session.offers)) write(player, offer.original)
            session.stage = TradeStage.Confirm
            session.accepted.fill(false)
            cancel(session, "The trade could not be saved. Your items have not been exchanged.")
            return
        }
        session.commit = group
        for (player in session.players) player.ifSetText("component.tradeconfirm:title", "Completing trade...")
    }

    private fun write(player: Player, items: List<InvObj?>) {
        check(player.inv.size == items.size)
        for (slot in items.indices) player.inv[slot] = items[slot]
    }

    private fun status(session: TradeSession, player: Player, component: String) {
        player.ifSetText(component, "Waiting for other player...")
        session.other(player).ifSetText(component, "Other player has accepted.")
    }

    private fun value(offer: TradeOffer): String = "<col=ffffff>" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
        .format(offer.items().fold(java.math.BigInteger.ZERO) { total, item -> total +
            java.math.BigInteger.valueOf((ServerCacheManager.getItem(item.id)?.let { prices[it] } ?: 0).toLong()) *
            java.math.BigInteger.valueOf(item.count.toLong()) }) + "</col>"

    private fun refresh(session: TradeSession) {
        for (player in session.players) {
            val side = session.side(player)
            val own = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.tradeoffer".asRSCM())), arrayOfNulls(player.inv.size))
            val other = Inventory(own.type, arrayOfNulls(player.inv.size))
            session.offers[side].display().forEachIndexed { slot, item -> own[slot] = item }
            session.offers[1 - side].display().forEachIndexed { slot, item -> other[slot] = item }
            UpdateInventory.updateInvFull(player, own)
            UpdateInventory.updateInvFullMirror(player, other)
            val remaining = Inventory(player.inv.type, session.offers[side].remaining().toTypedArray())
            UpdateInventory.updateInvFull(player, remaining)
            val name = session.other(player).displayName
            player.ifSetText("component.trademain:free_space_text", "$name has ${session.offers[1 - side].remaining().count { it == null }} free inventory slots.")
            player.ifSetText("component.trademain:your_offer_header", "You offer:<br>(Value: ${value(session.offers[side])} coins)")
            player.ifSetText("component.trademain:other_offer_header", "$name offers:<br>(Value: ${value(session.offers[1 - side])} coins)")
        }
    }

    fun closed(player: Player, confirmation: Boolean) {
        val session = sessions[player] ?: return
        if (session.transitioning || session.stage == TradeStage.Committing) return
        if ((session.stage == TradeStage.Confirm) == confirmation) cancel(session)
    }

    fun cancel(player: Player) { sessions[player]?.let { cancel(it) }; requests.remove(player) }
    private fun cancel(session: TradeSession, message: String = "Other player declined trade.") {
        if (session.stage == TradeStage.Committing || session.stage == TradeStage.Closed) return
        close(session, message)
    }
    private fun close(session: TradeSession, message: String) {
        session.stage = TradeStage.Closed
        for (player in session.players) sessions.remove(player)
        for (player in session.players) {
            player.ifCloseModals(events)
            flags(player, false)
            UpdateInventory.updateInvStopTransmit(player, "inv.tradeoffer".asRSCM())
            UpdateInventory.updateInvFull(player, player.inv)
            player.mes(message, ChatType.Trade)
        }
    }

    override fun onPostTick(player: Player) {
        requests.entries.removeIf { (owner, request) -> request.expires < player.currentMapClock ||
            owner.loggingOut || request.target.loggingOut || owner.clientDisconnected.get() || request.target.clientDisconnected.get() }
        val session = sessions[player] ?: return
        if (session.stage == TradeStage.Committing) {
            if (session.commit?.succeeded == true) {
                session.commit!!.release()
                close(session, "Accepted trade.")
            }
        } else if (!valid(session)) cancel(session)
    }

    private fun flags(player: Player, removed: Boolean) {
        VarPlayerIntMapSetter.set(player, "varbit.trade_this_player_removed", if (removed) 1 else 0)
        VarPlayerIntMapSetter.set(player, "varbit.trade_other_player_removed", if (removed) 1 else 0)
    }

    private companion object {
        val ITEM_OPS = arrayOf(IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10)
    }
}
