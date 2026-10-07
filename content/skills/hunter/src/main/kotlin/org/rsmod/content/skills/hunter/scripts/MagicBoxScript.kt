package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.interfaces.bank.selectedTab
import org.rsmod.content.skills.hunter.MagicBoxService
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MagicBoxScript @Inject constructor(
    private val boxes: MagicBoxService,
    private val bankInv: BankInvScript,
    private val areas: AreaChecker,
) : PluginScript() {
    internal data class Banking(val slot: Int, val item: Int)

    private var Player.bankingSlot by nullableIntVarp("varp.hunter_magic_box_banking_slot")
    private var Player.bankingItem by nullableIntVarp("varp.hunter_magic_box_banking_item")

    override fun ScriptContext.startup() {
        onOpHeld1(MagicBoxService.EMPTY) { lay() }
        onEvent<GameLifecycle.LateCycle> { boxes.tick() }
        onPlayerLogout { boxes.cleanup(player); player.clearBanking() }
        for (loc in listOf(MagicBoxService.ARMED_LOC, MagicBoxService.FAILED_LOC, MagicBoxService.FULL_LOC)) {
            onOpLoc1(loc) { retrieve(it.loc) }
        }
        onOpLoc2(MagicBoxService.ARMED_LOC) {
            val trap = boxes.owned(player, it.loc)
            mes(if (trap == null) "This is not your trap." else if (trap.baited) "The magic box is baited and waiting for an imp." else "The magic box is waiting for an imp.")
        }
        onOpLocU(MagicBoxService.ARMED_LOC) {
            if (boxes.owned(player, it.loc) == null) mes("This is not your trap.")
            else boxes.bait(player, it.loc, it.objType.internalName)
        }
        for (box in listOf(MagicBoxService.FULL, MagicBoxService.HALF)) {
            onOpHeld1(box) {
                mes("The imp will take ${if (box == MagicBoxService.FULL) "two items or stacks" else "one item or stack"} to your bank in return for its freedom.")
            }
            onOpHeld2(box) { openBank(it.slot) }
            onOpHeldU(box) { deposit(it.firstSlot, it.first.id, it.secondSlot, it.second.id) }
        }
        onIfClose(INTERFACE) { player.clearBanking() }
        onIfModalButton(INVENTORY) {
            val session = player.banking() ?: return@onIfModalButton
            val clicked = it.obj
            if (it.op == IfButtonOp.Op10) objExamine(inv, it.comsub)
            else if (it.op == IfButtonOp.Op1 && clicked != null) {
                if (deposit(session.slot, session.item, it.comsub, clicked.id)) {
                    val next = (listOf(session.slot) + inv.indices).firstOrNull { slot -> inv[slot]?.let { charges(it.id) > 0 } == true }
                    if (next == null) ifClose()
                    else player.selectBanking(next, checkNotNull(inv[next]).id)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.lay() {
        val trap = boxes.begin(player) ?: return
        resetAnim()
        anim(HunterParams.LAY_TRAP_ANIM)
        soundSynth("synth.HUNTING_LAYMAGICBOXTRAP")
        try {
            delay(HunterParams.LAY_TICKS)
            if (boxes.finish(trap)) {
                collision.firstStepDestination(trap.coords, listOf(Direction.West))?.let { walk(it) }
            }
        } finally { boxes.cancel(trap); resetAnim() }
    }

    private suspend fun ProtectedAccess.retrieve(loc: BoundLocInfo) {
        val trap = boxes.owned(player, loc) ?: run { mes("This is not your trap."); return }
        val origin = coords
        faceLoc(loc)
        resetAnim()
        anim("seq.hunting_setting_trap_small")
        try {
            delay(2)
            if (coords == origin && boxes.owned(player, loc) === trap) boxes.retrieve(player, loc)
        } finally { resetAnim() }
    }

    private fun ProtectedAccess.openBank(slot: Int) {
        val item = inv[slot] ?: return
        if (charges(item.id) == 0 || !mayBank(player)) return
        ifOpenMainModal(INTERFACE)
        player.selectBanking(slot, item.id)
        // Native on-load initializes text; the shared inventory renderer populates the separate 7x4 grid.
        interfaceInvInit(inv, INVENTORY, objRowCount = 7, objColCount = 4, op1 = "Bank")
        ifSetEvents(INVENTORY, inv.indices, IfEvent.Op1, IfEvent.Op10)
    }

    private fun Player.banking(): Banking? {
        val slot = bankingSlot ?: return null
        val item = bankingItem ?: return null
        return Banking(slot, item)
    }

    private fun Player.selectBanking(slot: Int, item: Int) {
        bankingSlot = slot
        bankingItem = item
    }

    private fun Player.clearBanking() {
        bankingSlot = null
        bankingItem = null
    }

    internal fun ProtectedAccess.deposit(boxSlot: Int, expectedBox: Int, itemSlot: Int, expectedItem: Int): Boolean {
        if (boxSlot == itemSlot || boxSlot !in inv.indices || itemSlot !in inv.indices) return false
        val box = inv[boxSlot] ?: return false
        val item = inv[itemSlot] ?: return false
        if (box.id != expectedBox || item.id != expectedItem || charges(box.id) == 0 || !mayBank(player)) return false
        if (item.id == "obj.looting_bag".asRSCM(RSCMType.OBJ) || item.id == "obj.looting_bag_open".asRSCM(RSCMType.OBJ)) {
            mes("The imp refuses to carry your looting bag.")
            return false
        }
        val previousTab = selectedTab
        val deposited = try {
            selectedTab = BankTab.Main
            with(bankInv) { this@deposit.invDeposit(itemSlot, item.count, inv) }
        } finally { selectedTab = previousTab }
        if (!deposited) return false
        val next = if (charges(box.id) == 2) MagicBoxService.HALF else MagicBoxService.EMPTY
        // Keep deposit and box replacement adjacent without suspension.
        val replacement = checkNotNull(ServerCacheManager.getItem(next.asRSCM(RSCMType.OBJ)))
        check(invReplaceSlot(inv, boxSlot, 1, replacement).success) { "Imp box changed during synchronous bank deposit" }
        mes("The imp takes the item to your bank${if (next == MagicBoxService.EMPTY) " and escapes" else ""}.")
        return true
    }

    internal fun mayBank(player: Player): Boolean {
        if (IronmanRestrictions.blockUimBank(player)) return false
        val wild = player.coords.wildernessLevel(areas)
        val reason = when {
            player.vars["varbit.teleblock_cycles"] > 0 -> "The imp refuses to help while you are teleblocked."
            player.vars["varbit.this_is_a_pvp_world"] != 0 -> "The imp refuses to help on this dangerous world."
            wild > 30 -> "The imp refuses to travel from this deep in the Wilderness."
            wild >= 0 && player.isInCombat() -> "The imp refuses to help while you are in combat here."
            player.vars["varbit.raids_client_indungeon"] != 0 || player.vars["varbit.tob_client_partystatus"] >= 2 -> "The imp cannot find its way to the bank from here."
            else -> return true
        }
        player.mes(reason)
        return false
    }

    companion object {
        const val INTERFACE = "interface.bank_deposit_imp"
        const val INVENTORY = "component.bank_deposit_imp:inventory"
        const val EXPLANATION = "component.bank_deposit_imp:explanation"
        fun charges(item: Int): Int = when (item) {
            MagicBoxService.FULL.asRSCM(RSCMType.OBJ) -> 2
            MagicBoxService.HALF.asRSCM(RSCMType.OBJ) -> 1
            else -> 0
        }
    }
}
