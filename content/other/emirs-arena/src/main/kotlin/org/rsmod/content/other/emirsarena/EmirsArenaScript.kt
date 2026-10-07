package org.rsmod.content.other.emirsarena

import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.other.emirsarena.duel.Duel
import org.rsmod.content.other.emirsarena.duel.DuelManager
import org.rsmod.content.other.emirsarena.duel.DuelRule
import org.rsmod.content.other.emirsarena.duel.DuelRules
import org.rsmod.content.other.emirsarena.duel.DuelStage
import org.rsmod.content.other.emirsarena.ranked.ArenaRanking
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EmirsArenaScript @Inject constructor(
    private val manager: DuelManager,
    private val trades: org.rsmod.content.interfaces.trading.TradeManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onArea(EmirsArena.AREA) { manager.enterArena(player) }
        onAreaExit(EmirsArena.AREA) { manager.exitArena(player) }
        onPlayerLogout { manager.onLogout(player) }
        onPlayerLogin { manager.onLogin(player) }

        for ((component, rule) in DuelRule.BY_COMPONENT) {
            onIfModalButton(EmirsArena.OPTIONS_PREFIX + component) { toggleRule(rule) }
        }
        for (slot in EmirsArena.WORN_OPTION_SLOTS) {
            onIfModalButton(EmirsArena.OPTIONS_PREFIX + "duel_wornoption$slot") { toggleSlot(slot) }
        }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "accept") { acceptOptions() }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "decline") { decline() }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "save") { savePreset() }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "load") { loadPreset() }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "previous") { loadLastDuel() }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "whip") { setRules(DuelRules.WHIP) }
        onIfModalButton(EmirsArena.OPTIONS_PREFIX + "box") { setRules(DuelRules.BOXING) }
        onIfClose(EmirsArena.OPTIONS_INTERFACE) { manager.onScreenClosed(player, DuelStage.Options) }

        onIfModalButton(EmirsArena.STAKE_SIDE_ITEMS) { offerStake(it.comsub, it.op) }
        onIfModalButton(EmirsArena.STAKE_PREFIX + "your_offer") {
            if (!normalTrade(org.rsmod.content.interfaces.trading.TradeScreenAction.Remove, it)) removeStake(it.comsub, it.op)
        }
        onIfModalButton(EmirsArena.STAKE_PREFIX + "accept") {
            if (!normalTrade(org.rsmod.content.interfaces.trading.TradeScreenAction.AcceptOffer, it)) acceptStakes()
        }
        onIfModalButton(EmirsArena.STAKE_PREFIX + "decline") {
            if (!normalTrade(org.rsmod.content.interfaces.trading.TradeScreenAction.Decline, it)) decline()
        }
        onIfClose(EmirsArena.STAKE_INTERFACE) {
            if (trades.owns(player)) trades.closed(player, false)
            else manager.onScreenClosed(player, DuelStage.Stakes)
        }
        onIfModalButton(EmirsArena.STAKE_CONFIRM_PREFIX + "trade2accept") {
            if (!normalTrade(org.rsmod.content.interfaces.trading.TradeScreenAction.AcceptConfirm, it)) acceptStakeConfirm()
        }
        onIfModalButton(EmirsArena.STAKE_CONFIRM_PREFIX + "trade2decline") {
            if (!normalTrade(org.rsmod.content.interfaces.trading.TradeScreenAction.Decline, it)) decline()
        }
        onIfClose(EmirsArena.STAKE_CONFIRM_INTERFACE) {
            if (trades.owns(player)) trades.closed(player, true)
            else manager.onScreenClosed(player, DuelStage.StakeConfirm)
        }

        onIfModalButton(EmirsArena.CONFIRM_PREFIX + "accept") { acceptConfirm() }
        onIfModalButton(EmirsArena.CONFIRM_PREFIX + "decline") { decline() }
        onIfClose(EmirsArena.CONFIRM_INTERFACE) { manager.onScreenClosed(player, DuelStage.Confirm) }

        onPlayerQueue(EmirsArena.START_QUEUE) { enterArenaAndCountDown() }
        onPlayerQueue(EmirsArena.LEAVE_QUEUE) { leaveArena() }
        onOpLoc1(EmirsArena.TRAPDOOR_LOC) { forfeit() }

        onOpLoc1("loc.pvpa_scoreboard") { openScoreboard() }
        for (board in RECRUITMENT_BOARDS) {
            onOpLoc1(board) { recruitmentBoard() }
            onOpLoc2(board) { mes("There are no duels to spectate right now.") }
            onOpLoc3(board) { showRank() }
        }
        onOpLoc1("loc.pvpa_lobby_supplies_chest") { mes(EmirsArena.SUPPLIES_MESSAGE) }
        onOpLoc1("loc.pvpa_stagingarea_supplies_chest") { mes(EmirsArena.SUPPLIES_MESSAGE) }
    }

    private suspend fun ProtectedAccess.normalTrade(
        action: org.rsmod.content.interfaces.trading.TradeScreenAction,
        button: org.rsmod.api.player.ui.IfModalButton,
    ): Boolean {
        if (!trades.owns(player)) return false
        trades.button(this, action, button.comsub, button.op, button.obj?.id)
        return true
    }

    private fun ProtectedAccess.currentDuel(stage: DuelStage): Duel? {
        val duel = manager.duelOf(player) ?: return null
        return duel.takeIf { it.stage == stage }
    }

    private fun ProtectedAccess.toggleRule(rule: DuelRule) {
        val duel = currentDuel(DuelStage.Options) ?: return
        manager.changeRules(duel, player, duel.rules.toggle(rule))
    }

    private fun ProtectedAccess.toggleSlot(slot: Int) {
        val duel = currentDuel(DuelStage.Options) ?: return
        manager.changeRules(duel, player, duel.rules.toggleSlot(slot))
    }

    private fun ProtectedAccess.setRules(rules: DuelRules) {
        val duel = currentDuel(DuelStage.Options) ?: return
        manager.changeRules(duel, player, rules)
    }

    private fun ProtectedAccess.savePreset() {
        val duel = currentDuel(DuelStage.Options) ?: return
        EmirsArenaState.savePreset(player, duel.rules.packed)
        mes("Your duel settings have been saved as a preset.")
    }

    private fun ProtectedAccess.loadPreset() {
        val saved = EmirsArenaState.savedPreset(player)
        if (saved == null) {
            mes("You haven't saved a duel preset yet.")
            return
        }
        setRules(DuelRules(saved))
    }

    private fun ProtectedAccess.loadLastDuel() {
        val last = EmirsArenaState.lastRules(player)
        if (last == null) {
            mes("You haven't fought a duel yet.")
            return
        }
        setRules(DuelRules(last))
    }

    private fun ProtectedAccess.acceptOptions() {
        val duel = currentDuel(DuelStage.Options) ?: return
        manager.acceptOptions(duel, player)
    }

    private suspend fun ProtectedAccess.offerStake(slot: Int, op: IfButtonOp) {
        val duel = currentDuel(DuelStage.Stakes) ?: return
        manager.offerStake(this, duel, slot, op)
    }

    private suspend fun ProtectedAccess.removeStake(slot: Int, op: IfButtonOp) {
        val duel = currentDuel(DuelStage.Stakes) ?: return
        manager.removeStake(this, duel, slot, op)
    }

    private fun ProtectedAccess.acceptStakes() {
        val duel = currentDuel(DuelStage.Stakes) ?: return
        manager.acceptStakes(duel, player)
    }

    private fun ProtectedAccess.acceptStakeConfirm() {
        val duel = currentDuel(DuelStage.StakeConfirm) ?: return
        manager.acceptStakeConfirm(duel, player)
    }

    private fun ProtectedAccess.acceptConfirm() {
        val duel = currentDuel(DuelStage.Confirm) ?: return
        manager.acceptConfirm(duel, player)
    }

    private fun ProtectedAccess.decline() {
        val duel = manager.duelOf(player) ?: return
        manager.decline(duel, player)
    }

    private suspend fun ProtectedAccess.enterArenaAndCountDown() {
        val duel = manager.duelOf(player) ?: return
        val arena = duel.arena ?: return
        if (duel.stage != DuelStage.Countdown) {
            return
        }
        val (west, east) = arena.startPositions(duel.rules.has(DuelRule.NoMovement))
        val mine = if (duel.side(player) == 0) west else east
        val theirs = if (duel.side(player) == 0) east else west

        ifClose()
        telejump(mine, TeleportType.Exempt)
        faceSquare(theirs)
        player.midiJingle(EmirsArena.DUEL_START_JINGLE)
        delay(2)
        for (count in COUNTDOWN_FROM downTo 1) {
            if (duel.stage != DuelStage.Countdown) {
                return
            }
            say("$count")
            delay(COUNTDOWN_INTERVAL)
        }
        if (duel.stage != DuelStage.Countdown) {
            return
        }
        say("FIGHT!")
        manager.beginFight(duel)
    }

    private fun ProtectedAccess.leaveArena() {
        ifClose()
        val spot =
            mapFindSquareLineOfWalk(EmirsArena.LOBBY, minRadius = 0, maxRadius = EmirsArena.LOBBY_RADIUS)
        telejump(spot ?: EmirsArena.LOBBY, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.forfeit() {
        val duel = manager.duelOf(player)
        if (duel == null || duel.stage != DuelStage.Fighting) {
            mes("You can only use the trapdoor to forfeit a duel.")
            return
        }
        if (!manager.canForfeit(duel)) {
            mes("Forfeiting has been disabled for this duel.")
            return
        }
        val leave =
            choice2("Yes, forfeit the duel.", true, "No, keep fighting.", false, title = "Forfeit the duel?")
        if (!leave) {
            return
        }
        if (duel.stage == DuelStage.Fighting) {
            manager.forfeit(duel, player)
        }
    }

    private fun ProtectedAccess.openScoreboard() {
        val lines = manager.scoreboardLines
        val text =
            if (lines.isEmpty()) {
                "No duels have been fought here yet."
            } else {
                lines.joinToString("<br>")
            }
        ifOpenMainModal(EmirsArena.SCOREBOARD_INTERFACE)
        ifSetText(EmirsArena.SCOREBOARD_TEXT, text)
    }

    private suspend fun ProtectedAccess.recruitmentBoard() {
        val choices =
            listOf(
                "Sign up for a ranked 1v1 duel",
                "Withdraw from ranked matchmaking",
                "How does ranked duelling work?",
                "Cancel",
            )
        when (menu("Arena recruitment board", hotkeys = true, choices = choices)) {
            0 -> mes(manager.signUpForRanked(player))
            1 -> mes(manager.withdrawFromRanked(player))
            2 ->
                mesbox(
                    "Sign up on a recruitment board and the arena staff will pair you with the " +
                        "next fighter who signs up. Ranked duels are fought to the death with " +
                        "everything allowed; the winner gains rank points and both fighters earn " +
                        "reward points to spend with Mubariz."
                )
            else -> Unit
        }
    }

    private suspend fun ProtectedAccess.showRank() {
        val rank = ArenaRanking.effectiveRank(player.vars[EmirsArena.RANK_POINTS_VARBIT])
        val tier = ArenaRanking.tier(rank)
        val rewards = player.vars[EmirsArena.REWARD_POINTS_VARBIT]
        val rankedWins = EmirsArenaState.wins(player, ranked = true)
        val rankedLosses = EmirsArenaState.losses(player, ranked = true)
        val legacyWins = EmirsArenaState.wins(player, ranked = false)
        val legacyLosses = EmirsArenaState.losses(player, ranked = false)
        val queued = if (manager.isQueuedForRanked(player)) "<br>You are waiting for a ranked opponent." else ""
        mesbox(
            "Rank: ${tier.label} ($rank rank points)<br>" +
                "Reward points: $rewards<br>" +
                "Ranked duels: $rankedWins won, $rankedLosses lost<br>" +
                "Legacy duels: $legacyWins won, $legacyLosses lost$queued"
        )
    }

    private companion object {
        private const val COUNTDOWN_FROM = 3
        private const val COUNTDOWN_INTERVAL = 2

        private val RECRUITMENT_BOARDS =
            listOf("loc.pvpa_recruitment_board", "loc.pvpa_recruitment_board02")
    }
}
