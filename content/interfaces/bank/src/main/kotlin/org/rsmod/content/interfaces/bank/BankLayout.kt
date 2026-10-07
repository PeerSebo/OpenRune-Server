package org.rsmod.content.interfaces.bank

import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.math.abs
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.invtx.invCompress
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.ClientScripts.mesLayerClose
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalDrag
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.configs.bank_comsubs
import org.rsmod.content.interfaces.bank.insertMode
import org.rsmod.content.interfaces.bank.selectedTab
import org.rsmod.content.interfaces.bank.setBanksideExtraOps
import org.rsmod.content.interfaces.bank.util.BankSlots
import org.rsmod.content.interfaces.bank.util.bulkShift
import org.rsmod.content.interfaces.bank.util.leftShift
import org.rsmod.content.interfaces.bank.util.offset
import org.rsmod.content.interfaces.bank.util.shiftInsert
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

internal object BankLayout {
    internal fun ProtectedAccess.dragTab(drag: IfModalDrag) {
        val fromTabIndex = drag.selectedSlot ?: return resendSlot(bank, 0)
        val intoTabIndex = drag.targetSlot ?: return resendSlot(bank, 0)
        val fromTab = BankTab.forIndex(fromTabIndex - bank_comsubs.other_tabs.first)
        val intoTab = BankTab.forIndex(intoTabIndex - bank_comsubs.other_tabs.first)

        if (fromTab == null || fromTab.isMainTab || fromTab.isEmpty(this)) {
            resendSlot(bank, 0)
            return
        }

        if (intoTab == null || intoTab.isMainTab || intoTab.isEmpty(this)) {
            resendSlot(bank, 0)
            return
        }

        val fromSlots = fromTab.slotRange(this)
        val intoSlots = intoTab.slotRange(this)
        val intoSlot = if (fromTab.index > intoTab.index) intoSlots.first else intoSlots.last

        val transaction =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                bulkShift {
                    this.from = bankInv
                    this.fromSlots = fromSlots
                    this.intoSlot = intoSlot
                }
            }

        if (transaction.failure) {
            return
        }

        val fromTabSize = fromTab.occupiedSpace(this)
        if (fromTab.index > intoTab.index) {
            val tabShiftRange = fromTab.index - 1 downTo intoTab.index
            for (index in tabShiftRange) {
                val curr = BankTab.forIndex(index)
                checkNotNull(curr) { "`curr` tab should not be null: $index" }

                val next = BankTab.forIndex(index + 1)
                checkNotNull(next) { "`next` tab should not be null: ${index + 1}" }

                vars[next.sizeVarBit] = vars[curr.sizeVarBit]
            }
        } else {
            val tabShiftRange = fromTab.index until intoTab.index
            for (index in tabShiftRange) {
                val curr = BankTab.forIndex(index)
                checkNotNull(curr) { "`curr` tab should not be null: $index" }

                val next = BankTab.forIndex(index + 1)
                checkNotNull(next) { "`next` tab should not be null: ${index + 1}" }

                vars[curr.sizeVarBit] = vars[next.sizeVarBit]
            }
        }
        vars[intoTab.sizeVarBit] = fromTabSize
    }

    internal fun ProtectedAccess.dragSideInv(drag: IfModalDrag) {
        val fromSlot = drag.selectedSlot ?: return resendSlot(inv, 0)
        val intoSlot = drag.targetSlot ?: return resendSlot(inv, 0)
        invMoveToSlot(inv, inv, fromSlot, intoSlot)
        player.setBanksideExtraOps()
    }

    internal fun ProtectedAccess.dragMainInv(drag: IfModalDrag) {
        val fromSlot = drag.selectedSlot ?: return resendSlot(bank, 0)
        val intoSlot = drag.targetSlot ?: return resendSlot(bank, 0)
        val fromTab = BankTab.forSlot(this, fromSlot) ?: return resendSlot(bank, fromSlot)

        // The client uses extended subcomponents for positions beyond a tab's occupied slots.
        val extendedTabSlots = bank_comsubs.tab_extended_slots_offset.offset(bank.size - 1)
        if (intoSlot in extendedTabSlots) {
            val tabIndex = intoSlot - extendedTabSlots.first
            val intoTab = BankTab.forIndex(tabIndex - 1) ?: BankTab.Main

            if (fromTab == intoTab) {
                resendSlot(bank, fromSlot)
                return
            }

            dragMainInvExtendedSlot(fromTab, fromSlot, intoTab)
            return
        }

        val intoTab = BankTab.forSlot(this, intoSlot) ?: BankTab.Main
        dragMainInv(fromTab, fromSlot, intoTab, intoSlot)
    }

    private fun ProtectedAccess.dragMainInvExtendedSlot(
        fromTab: BankTab,
        fromSlot: Int,
        intoTab: BankTab,
    ) {
        val intoSlots = intoTab.slotRange(this)
        val emptySlot = intoSlots.firstOrNull { bank[it] == null }
        val intoSlot = emptySlot ?: intoSlots.last
        val intoSlotOffset = if (emptySlot == null && fromSlot > intoSlot) 1 else 0
        val targetSlot = intoSlot + intoSlotOffset
        val targetSlotEmpty = bank[targetSlot] == null

        val transaction =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                if (!targetSlotEmpty) {
                    shiftInsert {
                        this.from = bankInv
                        this.fromSlot = fromSlot
                        this.intoSlot = targetSlot
                    }
                } else {
                    swap {
                        this.from = bankInv
                        this.into = bankInv
                        this.fromSlot = fromSlot
                        this.intoSlot = targetSlot
                    }
                    leftShift {
                        this.from = bankInv
                        this.startSlot = fromSlot + 1
                        this.toSlot = fromSlot
                    }
                }
            }

        if (transaction.failure) {
            resendSlot(bank, fromSlot)
            return
        }

        fromTab.decreaseSize(this)
        if (emptySlot == null) {
            intoTab.increaseSize(this)
        }

        notifySlotUpdate(fromTab)
        if (fromTab.isEmpty(this)) {
            compactEmptyTabs(fromTab.index)
        }
    }

    internal fun ProtectedAccess.dragMainInv(
        fromTab: BankTab,
        fromSlot: Int,
        intoTab: BankTab,
        intoSlot: Int,
    ) {
        if (!insertMode) {
            dragMainInvSwap(fromTab, fromSlot, intoTab, intoSlot)
            return
        }

        if (fromTab == intoTab && abs(fromSlot - intoSlot) == 1) {
            dragMainInvSwap(fromTab, fromSlot, intoTab, intoSlot)
            return
        }

        dragMainInvShift(fromTab, fromSlot, intoTab, intoSlot)
    }

    private fun ProtectedAccess.dragMainInvSwap(
        fromTab: BankTab,
        fromSlot: Int,
        intoTab: BankTab,
        intoSlot: Int,
    ) {
        val intoSlots = intoTab.slotRange(this)

        val transaction =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                swap {
                    this.from = bankInv
                    this.into = bankInv
                    this.fromSlot = fromSlot
                    this.intoSlot = intoSlot
                }
            }

        if (transaction.failure) {
            resendSlot(bank, fromSlot)
            return
        }

        val sizeExpansion = intoSlot - intoSlots.last
        if (sizeExpansion > 0) {
            intoTab.increaseSize(this, sizeExpansion)
        }

        notifySlotUpdate(fromSlot)
        if (fromTab.isEmpty(this)) {
            compactEmptyTabs(fromTab.index)
        }
    }

    private fun ProtectedAccess.dragMainInvShift(
        fromTab: BankTab,
        fromSlot: Int,
        intoTab: BankTab,
        intoSlot: Int,
    ) {
        val intoSameTab = fromTab == intoTab
        val intoEmptySlot = bank[intoSlot] == null
        val intoSlotOffset = if (!intoEmptySlot && fromSlot < intoSlot && !intoSameTab) 1 else 0
        val intoMainTabEmptySlot = fromTab.isMainTab && intoTab.isMainTab && intoEmptySlot
        val targetSlot = intoSlot - intoSlotOffset

        val transaction =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                if (!intoEmptySlot) {
                    shiftInsert {
                        this.from = bankInv
                        this.fromSlot = fromSlot
                        this.intoSlot = targetSlot
                    }
                } else {
                    swap {
                        this.from = bankInv
                        this.into = bankInv
                        this.fromSlot = fromSlot
                        this.intoSlot = targetSlot
                    }
                    if (!intoMainTabEmptySlot) {
                        leftShift {
                            this.from = bankInv
                            this.startSlot = fromSlot + 1
                            this.toSlot = fromSlot
                        }
                    }
                }
            }

        if (transaction.failure) {
            resendSlot(bank, fromSlot)
            return
        }

        if (!intoMainTabEmptySlot) {
            fromTab.decreaseSize(this)
        }

        if (!intoEmptySlot) {
            intoTab.increaseSize(this)
        } else {
            val sizeExpansion = intoSlot - intoTab.slotRange(this).last
            if (sizeExpansion > 0) {
                intoTab.increaseSize(this, sizeExpansion)
            }
        }

        notifySlotUpdate(fromTab)
        if (fromTab.isEmpty(this)) {
            compactEmptyTabs(fromTab.index)
        }
    }

    internal fun ProtectedAccess.dragIntoTab(drag: IfModalDrag) {
        val fromSlot = drag.selectedSlot ?: return resendSlot(bank, 0)
        val comsub = drag.targetSlot ?: return resendSlot(bank, 0)

        if (comsub != bank_comsubs.main_tab && comsub !in bank_comsubs.other_tabs) {
            resendSlot(bank, fromSlot)
            return
        }

        val obj = bank[fromSlot]
        if (obj == null || obj.id != drag.selectedObj) {
            resendSlot(bank, fromSlot)
            return
        }

        val fromTab = BankTab.forSlot(this, fromSlot) ?: return resendSlot(bank, 0)
        if (comsub == bank_comsubs.main_tab) {
            dragIntoTab(fromTab, fromSlot, BankTab.Main)
            return
        }

        val tabIndex = comsub - bank_comsubs.other_tabs.first
        val intoTab = BankTab.forIndex(tabIndex) ?: return resendSlot(bank, fromSlot)
        dragIntoTab(fromTab, fromSlot, intoTab)
    }

    internal fun ProtectedAccess.dragIntoTab(fromTab: BankTab, fromSlot: Int, intoTab: BankTab) {
        if (intoTab == fromTab) {
            resendSlot(bank, fromSlot)
            return
        }
        dragMainInvExtendedSlot(fromTab, fromSlot, intoTab)
    }

    internal fun ProtectedAccess.selectTab(comsub: Int, op: IfButtonOp) {
        val tabIndex = comsub - bank_comsubs.other_tabs.first
        val tab = BankTab.forIndex(tabIndex) ?: BankTab.Main
        if (tab == BankTab.Main) {
            if (op == IfButtonOp.Op1) {
                selectedTab = tab
            } else if (op == IfButtonOp.Op7) {
                removePlaceholders(tab)
            }
            return
        }

        if (tab.isEmpty(this)) {
            mes("To create a new tab, drag items from your bank onto this tab.")
            return
        }

        mesLayerClose(player, constants.meslayer_mode_objsearch)
        when (op) {
            IfButtonOp.Op1 -> selectedTab = tab
            IfButtonOp.Op6 -> collapseTab(tab)
            IfButtonOp.Op7 -> removePlaceholders(tab)
            else -> throw NotImplementedError("Bank tab op not implemented: op=$op")
        }
    }

    internal fun ProtectedAccess.removePlaceholders(tab: BankTab) {
        val slots = tab.slotRange(this)
        if (slots.isEmpty()) {
            mes("You don't have any placeholders to release.")
            return
        }
        var removed = 0
        for (slot in slots) {
            val obj = bank[slot] ?: continue
            if (getInvObj(obj).isPlaceholder) {
                bank[slot] = null
                removed++
            }
        }
        if (removed == 0) {
            mes("You don't have any placeholders to release.")
            return
        }
        notifySlotUpdate(slots.first)
    }

    internal fun ProtectedAccess.removePlaceholders() {
        val tabUpdates = mutableSetOf<BankTab>()
        for (slot in bank.indices) {
            val obj = bank[slot] ?: continue
            if (getInvObj(obj).isPlaceholder) {
                bank[slot] = null

                val tab = BankTab.forSlot(this, slot)
                tab?.let(tabUpdates::add)
            }
        }
        if (tabUpdates.isEmpty()) {
            return
        }
        soundSynth("synth.PAPER_TURN")
        for (tab in tabUpdates) {
            notifySlotUpdate(tab)
        }
    }

    internal suspend fun ProtectedAccess.removeFillers() {
        val confirmation =
            confirmOverlay(
                target = "component.bankmain:popup",
                title = "Clear all fillers?",
                text =
                    "This option will clear <col=ffb83f>all</col> the bank fillers throughout " +
                        "your <col=ffb83f>whole bank</col>.<br><br>Are you sure?",
                cancel = "<col=ff0000>Cancel</col>",
                confirm = "<col=0dc10d>Clear them all</col>",
            )
        if (!confirmation) {
            return
        }
        val tabUpdates = mutableSetOf<BankTab>()
        for (slot in bank.indices) {
            val obj = bank[slot] ?: continue
            if (getInvObj(obj).isType("obj.bank_filler")) {
                bank[slot] = null

                val tab = BankTab.forSlot(this, slot)
                tab?.let(tabUpdates::add)
            }
        }
        soundSynth("synth.PAPER_TURN")
        for (tab in tabUpdates) {
            notifySlotUpdate(tab)
        }
    }

    private fun ProtectedAccess.collapseTab(tab: BankTab) {
        require(!tab.isMainTab) { "Main bank tab cannot be collapsed." }

        for (tab in BankTab.tabs) {
            compressTabObjs(tab)
        }

        val collapseObjCount = tab.occupiedSpace(this)
        val collapseTabSlots = tab.slotRange(this)

        val targetTab = BankTab.Main
        val targetTabSlots = targetTab.slotRange(this)
        val targetEmptySlots =
            targetTabSlots.asSequence().filter { bank[it] == null }.take(collapseObjCount).toList()

        val newSlotsCount = collapseObjCount - targetEmptySlots.size
        val swapQuery =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                for (i in targetEmptySlots.indices) {
                    val fromSlot = collapseTabSlots.first + i
                    if (fromSlot > collapseTabSlots.last) {
                        break
                    }
                    swap {
                        this.from = bankInv
                        this.into = bankInv
                        this.fromSlot = fromSlot
                        this.intoSlot = targetEmptySlots[i]
                        this.strict = true
                    }
                }
                if (newSlotsCount > 0) {
                    val shiftStartSlot = collapseTabSlots.first + targetEmptySlots.size
                    val shiftSlots = shiftStartSlot..collapseTabSlots.last
                    bulkShift {
                        this.from = bankInv
                        this.fromSlots = shiftSlots
                        this.intoSlot = targetTabSlots.last
                    }
                }
            }
        check(swapQuery.success) { "Could not collapse tab: tab=$tab, err=${swapQuery.err}" }

        targetTab.increaseSize(this, newSlotsCount)

        val trailingNullCount = trimGapsAndReturnTrailingGaps(tab)
        tab.decreaseSize(this, trailingNullCount)
        if (collapseObjCount > trailingNullCount) {
            tab.decreaseSize(this, collapseObjCount - trailingNullCount)
        }

        compactEmptyTabs(tab.index)
        if (selectedTab == tab) {
            selectedTab = BankTab.Main
        }
    }

    internal fun ProtectedAccess.notifySlotUpdate(slot: Int) {
        val tab = BankTab.forSlot(this, slot)
        checkNotNull(tab) { "`slot` was not associated with a valid bank tab: $slot" }
        notifySlotUpdate(tab)
    }

    internal fun ProtectedAccess.notifySlotUpdate(tab: BankTab) {
        val trailingNullCount = trimGapsAndReturnTrailingGaps(tab)
        if (trailingNullCount == 0) {
            return
        }

        tab.decreaseSize(this, trailingNullCount)

        if (tab.isEmpty(this) && !tab.isMainTab) {
            compactEmptyTabs(tab.index)

            if (selectedTab == tab) {
                selectedTab = BankTab.Main
            }
        }
    }

    internal fun ProtectedAccess.trimGapsAndReturnTrailingGaps(slotRange: IntRange): Int {
        // Note: `shiftLeadingGapsToTail` directly modifies `bank` obj array.
        val result = BankSlots.shiftLeadingGapsToTail(bank, slotRange)

        val trailingNullCount = result.trailingGaps
        if (trailingNullCount == 0) {
            return 0
        }

        val shiftStartSlot = min(bank.size - 1, slotRange.last + 1)
        val shiftToSlot = min(bank.size - 1, slotRange.last + 1 - trailingNullCount)

        val maxShiftSlot = min(bank.size, bank.lastOccupiedSlot() + trailingNullCount)

        val shiftQuery =
            player.invTransaction(bank) {
                val bankInv = select(bank)
                leftShift {
                    this.from = bankInv
                    this.startSlot = shiftStartSlot
                    this.toSlot = shiftToSlot
                    this.maxSlot = maxShiftSlot
                }
            }

        check(shiftQuery.success) {
            "Could not shift bank inventory: " +
                "startSlot=$shiftStartSlot, toSlot=$shiftToSlot, err=${shiftQuery.err}"
        }

        return trailingNullCount
    }

    private fun ProtectedAccess.trimGapsAndReturnTrailingGaps(tab: BankTab): Int {
        val slots = tab.slotRange(this)
        return trimGapsAndReturnTrailingGaps(slots)
    }

    internal fun ProtectedAccess.compressTabObjs(tab: BankTab) {
        if (tab.isEmpty(this)) {
            return
        }
        val tabSlots = tab.slotRange(this)
        if (tabSlots.first != tabSlots.last) {
            val compactQuery = player.invCompress(bank, tabSlots)
            check(compactQuery.success) {
                "Could not compress tab: tab=$tab, err=${compactQuery.err}"
            }
        }
        val trailingNullCount = tabSlots.count { bank[it] == null }
        if (trailingNullCount > 0) {
            tab.decreaseSize(this, trailingNullCount)
        }
    }

    private fun ProtectedAccess.compactEmptyTabs(startTabIndex: Int) {
        for (index in startTabIndex until BankTab.tabs.size) {
            val curr = BankTab.tabs[index]
            val next = BankTab.tabs.getOrNull(index + 1)
            val nextSize = next?.occupiedSpace(this) ?: 0
            vars[curr.sizeVarBit] = nextSize
        }
    }

    internal fun ProtectedAccess.compressBank() {
        for (tab in BankTab.entries) {
            val slots = tab.slotRange(this)
            compressTabObjs(tab)
            trimGapsAndReturnTrailingGaps(slots)
        }
    }

    internal fun Player.restoreMainTabSize() {
        val bank = invMap.getOrPut("inv.bank")
        val mainStart = BankTab.tabs.sumOf { vars[it.sizeVarBit] }
        val mainSize = (bank.lastOccupiedSlot() - mainStart).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(this, BankTab.Main.sizeVarBit, mainSize)
    }
}
