package org.rsmod.content.minigames.blastfurnace.scripts

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceMachine
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarsHot
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCofferBalance
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCofferHasCoins
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CofferScript
@Inject
constructor(
    private val areaChecker: AreaChecker,
    private val eventBus: EventBus,
    private val machine: BlastFurnaceMachine,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blast_furnace_automata_coffer_empty") { useCoffer() }
        onOpLoc1("loc.blast_furnace_automata_coffer_full") { useCoffer() }
        onArea(BlastFurnaceParams.AREA) {
            softTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER, 1)
            softQueue(BlastFurnaceParams.SMELT_QUEUE, 1)
            player.openFurnaceHud()
        }
        onAreaExit(BlastFurnaceParams.AREA) {
            clearSoftTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER)
            player.closeFurnaceHud()
        }
        onPlayerSoftTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER) {
            drainCoffer(player)
        }
        onPlayerLogin { player.syncCofferState() }
    }

    internal fun drainCoffer(player: Player) {
        if (!areaChecker.inArea(BlastFurnaceParams.AREA, player.coords)) {
            player.clearSoftTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER)
            return
        }
        val balance = player.blastFurnaceCofferBalance
        if (balance <= 0) {
            player.blastFurnaceCofferHasCoins = false
            player.clearSoftTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER)
            return
        }
        val drained = minOf(balance, BlastFurnaceParams.COFFER_DRAIN_PER_TICK)
        player.blastFurnaceCofferBalance = balance - drained
        player.blastFurnaceCofferHasCoins = balance - drained > 0
    }

    private fun Player.syncCofferState() {
        blastFurnaceCofferHasCoins = blastFurnaceCofferBalance > 0
        if (blastFurnaceBarsHot == SmeltingProcess.DISPENSER_POURING) {
            blastFurnaceBarsHot = SmeltingProcess.DISPENSER_HOT
        }
        if (blastFurnaceBarsHot == SmeltingProcess.DISPENSER_HOT) {
            softTimer(BlastFurnaceParams.HOT_TIMER, BlastFurnaceParams.AUTO_COOL_TICKS)
        }
        if (areaChecker.inArea(BlastFurnaceParams.AREA, coords)) {
            softTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER, 1)
            softQueue(BlastFurnaceParams.SMELT_QUEUE, 1)
            openFurnaceHud()
        }
    }

    private fun Player.openFurnaceHud() {
        VarPlayerIntMapSetter.set(this, "varbit.blast_furnace_broken_pipe_reading", 0)
        VarPlayerIntMapSetter.set(this, "varbit.blast_furnace_fuel_low_reading", 0)
        VarPlayerIntMapSetter.set(this, "varbit.blast_furnace_temperature_reading", 50)
        machine.restoreMachinery()
        machine.animateMachinery()
        if (!ui.containsOverlay("interface.blast_furnace_hud")) {
            ifOpenOverlay("interface.blast_furnace_hud", "component.toplevel_osrs_stretch:overlay_hud", eventBus)
        }
    }

    private fun Player.closeFurnaceHud() {
        ifCloseOverlay("interface.blast_furnace_hud", eventBus)
    }

    private suspend fun ProtectedAccess.useCoffer() {
        startDialogue { cofferMenu() }
    }

    private suspend fun Dialogue.cofferMenu() {
        val balance = player.blastFurnaceCofferBalance
        access.mes("The coffer contains ${balance.formatAmount} coins.")
        val choice =
            choice3(
                "Deposit coins.",
                1,
                "Withdraw coins.",
                2,
                "Never mind.",
                3,
                title = "Select an Option",
            )
        when (choice) {
            1 -> deposit()
            2 -> withdraw()
        }
    }

    private suspend fun Dialogue.deposit() {
        if (access.inv.count("obj.coins") <= 0) {
            access.mes("You don't have any coins to deposit.")
            return
        }
        val requested = access.countDialog("How many coins do you wish to deposit?")
        val liveBalance = player.blastFurnaceCofferBalance
        val room = (Int.MAX_VALUE - liveBalance).coerceAtLeast(0)
        val amount = minOf(requested, access.inv.count("obj.coins"), room)
        if (amount <= 0) {
            return
        }
        if (access.invDel(access.inv, "obj.coins", amount).failure) {
            return
        }
        player.blastFurnaceCofferBalance = liveBalance + amount
        player.blastFurnaceCofferHasCoins = true
        access.soundSynth("synth.COINS")
        if (areaChecker.inArea(BlastFurnaceParams.AREA, player.coords)) {
            access.softTimer(BlastFurnaceParams.COFFER_DRAIN_TIMER, 1)
        }
        objbox(
            "obj.coins_100",
            "The coffer now contains ${player.blastFurnaceCofferBalance.formatAmount} coins.",
        )
    }

    private suspend fun Dialogue.withdraw() {
        if (player.blastFurnaceCofferBalance <= 0) {
            access.mes("The coffer is empty.")
            return
        }
        val requested = access.countDialog("How many coins do you wish to withdraw?")
        val liveBalance = player.blastFurnaceCofferBalance
        val amount = minOf(requested, liveBalance)
        if (amount <= 0) {
            return
        }
        if (access.invAdd(access.inv, "obj.coins", amount).failure) {
            access.mes(Constants.dm_invspace)
            return
        }
        player.blastFurnaceCofferBalance = liveBalance - amount
        player.blastFurnaceCofferHasCoins = player.blastFurnaceCofferBalance > 0
        access.mes("You withdraw ${amount.formatAmount} coins.")
    }
}
