package org.rsmod.content.skills.sailing.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.aconverted.interf.IfSubType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.PortTaskRow
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.skills.sailing.PortTaskManager
import org.rsmod.content.skills.sailing.SailingQuestMilestone
import org.rsmod.content.skills.sailing.SailingQuestProgressEvent
import org.rsmod.content.skills.sailing.SailingQuestSupport
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.portTaskOpenedSlot
import org.rsmod.content.skills.sailing.table.PortTaskRewards
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal enum class CargoTakeMode {
    Current,

    Last,

    Any,
}

internal data class CargoTransferTask(
    val slots: List<Int>,
    val deposit: Boolean,
    val dockRowId: Int,
)

// Validate selections against the immutable offers actually shown to this player.
internal data class PortTaskBoardView(val offers: List<PortTaskRow>, val openedAt: CoordGrid) {
    fun offerAtChild(child: Int): PortTaskRow? =
        if (child >= 0 && child % ChildrenPerCard == 0) offers.getOrNull(child / ChildrenPerCard)
        else null

    companion object {
        // Script 8917 creates six children per card; the next selectable child advances by six.
        const val ChildrenPerCard = 6
    }
}

class PortTaskBoardScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (port in NOTICE_BOARD_PORTS) {
            onOpLoc1(noticeBoardLoc(port)) { inspectBoard(it.loc) }
        }
        onIfModalButton(COMPONENT_BOARD_CONTAINER) {
            if (it.op == IfButtonOp.Op1) selectOffer(it.comsub)
        }
        onIfModalButton(COMPONENT_BOARD_CLOSE) { ifClose() }
        onIfClose(INTERFACE_BOARD) { player.attr.remove(OPEN_BOARD) }

        onOpLoc1(LOC_LEDGER_WITHDRAW) { takeCargo(it.loc, CargoTakeMode.Current) }
        onOpLoc2(LOC_LEDGER_WITHDRAW) { takeCargo(it.loc, CargoTakeMode.Last) }
        onOpLoc3(LOC_LEDGER_WITHDRAW) { takeCargo(it.loc, CargoTakeMode.Any) }
        onOpLoc1(LOC_LEDGER_DEPOSIT) { depositCargo(it.loc) }

        onPlayerQueueWithArgs<CargoTransferTask>(SailingConstants.QUEUE_CARGO_TRANSFER) {
            finishTransfer(it.args)
        }
    }

    private suspend fun ProtectedAccess.inspectBoard(loc: BoundLocInfo) {
        val dock = boardDocks[loc.id]
        if (dock == null) {
            mes(constants.dm_default)
            return
        }

        arriveDelay()

        PortTaskManager.syncSlotCount(player)

        val offers = PortTaskManager.offeredTasks(player, dock.row)
        if (offers.isEmpty()) {
            mes(MES_NOTHING_ON_BOARD)
            return
        }

        ifOpenMainModal(INTERFACE_BOARD)
        val view = PortTaskBoardView(offers.toList(), player.coords)
        player.attr[OPEN_BOARD] = view
        // Clear old child permissions before enabling this board's cards.
        ifSetEvents(COMPONENT_BOARD_CONTAINER, -1 until PortTaskManager.OFFERS_PER_BOARD * PortTaskBoardView.ChildrenPerCard)
        for (index in offers.indices) {
            val child = index * PortTaskBoardView.ChildrenPerCard
            ifSetEvents(COMPONENT_BOARD_CONTAINER, child..child, IfEvent.Op1)
        }
        ifSetEvents(COMPONENT_BOARD_CLOSE, -1..-1, IfEvent.Op1)
        // Script 8912 builds the frame/cards from eight DBROW integers, with -1 for unused offers.
        val rows = Array<Any>(PortTaskManager.OFFERS_PER_BOARD) { offers.getOrNull(it)?.rowId ?: -1 }
        runClientScript(BOARD_INIT_SCRIPT, *rows)
    }

    @OptIn(InternalApi::class)
    private suspend fun ProtectedAccess.selectOffer(child: Int) {
        val view = player.attr[OPEN_BOARD] ?: return
        if (player.ui.closeModal) return
        if (!player.ui.containsModal(INTERFACE_BOARD) || player.coords != view.openedAt) return
        val row = view.offerAtChild(child) ?: return
        if (PortTaskManager.hasTask(player, row)) return

        ifOpenSub(INTERFACE_INFO, COMPONENT_BOARD_POPUP, IfSubType.Modal)
        runClientScript(INFO_INIT_SCRIPT, COMPONENT_BOARD_POPUP.asRSCM(RSCMType.COMPONENT), row.rowId)
        try {
            // Scripts 8902/8903 send Back=0, Accept=1 through RESUME_P_COUNTDIALOG.
            val input = coroutine.pause(ResumePCountDialogInput::class)
            if (input.count != 1 || player.activeCoroutine !== coroutine || player.isDelayed) return
            // CloseModal may precede this response in the same input batch.
            if (player.ui.closeModal) return
            if (player.attr[OPEN_BOARD] !== view || player.coords != view.openedAt) return
            if (!player.ui.containsModal(INTERFACE_BOARD) || !player.ui.containsModal(INTERFACE_INFO)) return
            acceptOffer(row)
        } finally {
            ifCloseSub(INTERFACE_INFO)
        }
    }

    private fun ProtectedAccess.acceptOffer(row: PortTaskRow) {
        if (player.baseSailingLvl < row.levelRequired) {
            ifClose()
            mes(mesLevelRequirement(row.levelRequired))
            return
        }

        val slot = PortTaskManager.accept(player, row)
        if (slot == null) {
            ifClose()
            mes(mesNoFreeSlot(PortTaskManager.unlockedSlots(player)))
            return
        }

        player.portTaskOpenedSlot = slot
        ifClose()
        mes("You accept the task: ${row.name}.")
        if (!PortTaskRewards.isBounty(row)) {
            val pickup = PortTaskManager.pickupPort(row)
            mes("Collect the cargo at ${pickup.niceName}'s ledger table.")
            mes("Deliver it to ${row.endingPort.niceName}.")
        }
    }

    private suspend fun ProtectedAccess.takeCargo(loc: BoundLocInfo, mode: CargoTakeMode) {
        SailingQuestSupport.restoreLostCargoPickup(player)
        val dock = ledgerDocks[loc.id]
        if (dock == null) {
            mes(constants.dm_default)
            return
        }

        arriveDelay()

        if (!handsFree()) {
            mes(MES_HANDS_NOT_FREE)
            return
        }

        val waiting = PortTaskManager.slotsAwaitingPickupAt(player, dock.row)
        val slots =
            when (mode) {
                CargoTakeMode.Current -> waiting.take(1)
                CargoTakeMode.Any -> waiting
                CargoTakeMode.Last -> {
                    val lastId = PortTaskManager.lastCargoTaken(player)
                    waiting.filter { PortTaskManager.slotTaskId(player, it) == lastId }
                }
            }

        if (slots.isEmpty()) {
            mes(MES_NO_CARGO_HERE)
            return
        }

        // Loc ops run after queue processing; initial delays need no +1.
        weakQueue(
            SailingConstants.QUEUE_CARGO_TRANSFER,
            LEDGER_TRANSFER_TICKS,
            CargoTransferTask(slots = slots, deposit = false, dockRowId = dock.row.rowId),
        )
    }

    private suspend fun ProtectedAccess.depositCargo(loc: BoundLocInfo) {
        val dock = ledgerDocks[loc.id]
        if (dock == null) {
            mes(constants.dm_default)
            return
        }

        arriveDelay()

        SailingQuestSupport.restoreLostCargoPickup(player)
        val tutorial = SailingQuestSupport.tutorialTask
        if (dock.row.rowId == PortTaskManager.pickupPort(tutorial).rowId &&
            SailingQuestSupport.canCollectTutorialCargo(player)) {
            val tutorialSlot = PortTaskManager.slotOf(player, tutorial.taskId)
            if (tutorialSlot != null && PortTaskManager.cargoTaken(player, tutorialSlot) == 0) {
                collectCargo(tutorialSlot, tutorial, dock.row.rowId)
                return
            }
        }

        val slot = PortTaskManager.slotAwaitingDeliveryAt(player, dock.row)
        if (slot == null) {
            mes(MES_NOTHING_TO_DELIVER)
            return
        }

        weakQueue(
            SailingConstants.QUEUE_CARGO_TRANSFER,
            LEDGER_TRANSFER_TICKS,
            CargoTransferTask(slots = listOf(slot), deposit = true, dockRowId = dock.row.rowId),
        )
    }

    private fun ProtectedAccess.finishTransfer(task: CargoTransferTask) {
        for (slot in task.slots) {
            val row = PortTaskManager.slotTask(player, slot) ?: continue
            if (task.deposit) {
                if (!deliverCargo(slot, row, task.dockRowId)) return
            } else {
                if (!collectCargo(slot, row, task.dockRowId)) return
            }
        }
    }

    private fun ProtectedAccess.collectCargo(slot: Int, row: PortTaskRow, dockRowId: Int): Boolean {
        if (SailingQuestSupport.isTutorialTask(row) &&
            (!SailingQuestSupport.canCollectTutorialCargo(player) || SailingQuestSupport.ownsTutorialCargo(player))) return true
        if (PortTaskManager.cargoTaken(player, slot) != 0) return true
        if (PortTaskManager.pickupPort(row).rowId != dockRowId) return true
        if (!handsFree()) {
            mes(MES_HANDS_NOT_FREE)
            return false
        }

        val added = ArrayList<Pair<String, Int>>(row.cargo.size)
        for (entry in row.cargo) {
            val obj = entry.t0.internalName
            val count = entry.t1
            if (invAdd(inv, obj, count).failure) {
                for ((rollbackObj, rollbackCount) in added) {
                    invDel(inv, rollbackObj, rollbackCount)
                }
                mes(MES_NOT_ENOUGH_SPACE)
                return false
            }
            added += obj to count
        }

        PortTaskManager.setCargoTaken(player, slot, row.cargo.size)
        PortTaskManager.setLastCargoTaken(player, row.taskId)
        mes("You collect the cargo for '${row.name}'.")
        if (SailingQuestSupport.isTutorialTask(row)) {
            publish(SailingQuestProgressEvent(this, SailingQuestMilestone.CargoCollected))
        }
        return true
    }

    private fun ProtectedAccess.deliverCargo(slot: Int, row: PortTaskRow, dockRowId: Int): Boolean {
        val tutorial = SailingQuestSupport.isTutorialTask(row)
        if (tutorial && player.vars["varbit.sailing_intro"] != 42) return true
        if (row.endingPort.rowId != dockRowId) return true
        val taken = PortTaskManager.cargoTaken(player, slot)
        if (taken <= PortTaskManager.cargoDelivered(player, slot)) return true

        val removed = ArrayList<Pair<String, Int>>(row.cargo.size)
        for (entry in row.cargo) {
            val obj = entry.t0.internalName
            val count = entry.t1
            if (invDel(inv, obj, count).failure) {
                for ((rollbackObj, rollbackCount) in removed) {
                    invAdd(inv, rollbackObj, rollbackCount)
                }
                mes(MES_CARGO_MISSING)
                return false
            }
            removed += obj to count
        }

        PortTaskManager.setCargoDelivered(player, slot, row.cargo.size)
        if (tutorial) {
            publish(SailingQuestProgressEvent(this, SailingQuestMilestone.CargoDelivered))
            statAdvance(SailingConstants.SAILING_STAT, PortTaskRewards.xpFor(row).toDouble())
            invAddOrDrop(objRepo, "obj.sailing_paint_shark", 1)
            CollectionLog.grant(player, "obj.sailing_paint_shark")
            mes("<col=800080>You've received some paint!</col>")
            mes("You deliver the crate of ship parts.")
        } else {
            payOut(row)
        }
        PortTaskManager.recordCompletion(player, slot)
        return true
    }

    private fun ProtectedAccess.payOut(row: PortTaskRow) {
        val xp = PortTaskRewards.xpFor(row)
        val coins = PortTaskRewards.coinsFor(row)

        if (xp > 0) {
            statAdvance(SailingConstants.SAILING_STAT, xp.toDouble())
        }
        if (coins > 0) {
            invAddOrDrop(objRepo, OBJ_COINS, coins)
        }
        mes("You deliver the cargo and are paid $coins coins.")
    }

    private fun ProtectedAccess.handsFree(): Boolean =
        player.worn[Wearpos.RightHand.slot] == null && player.worn[Wearpos.LeftHand.slot] == null

    private fun mesLevelRequirement(level: Int): String =
        "You need a Sailing level of $level to take that task."

    private fun mesNoFreeSlot(slots: Int): String =
        "You can only carry $slots port task${if (slots == 1) "" else "s"} at a time."

    private val boardDocks: Map<Int, SailingDocks.SailingDock> by lazy {
        NOTICE_BOARD_PORTS.mapNotNull { port ->
                val dock = SailingDocks.byDbRow(dockDbRow(port)) ?: return@mapNotNull null
                noticeBoardLoc(port).asRSCM(RSCMType.LOC) to dock
            }
            .toMap()
    }

    // Resolve the event's parent loc ID even when dispatch matched its rendered child.
    private val ledgerDocks: Map<Int, SailingDocks.SailingDock> by lazy {
        LEDGER_TABLE_PORTS.mapNotNull { port ->
                val dock = SailingDocks.byDbRow(dockDbRow(port)) ?: return@mapNotNull null
                ledgerTableLoc(port).asRSCM(RSCMType.LOC) to dock
            }
            .toMap()
    }

    private companion object {
        const val INTERFACE_BOARD: String = "interface.port_task_board"

        const val INTERFACE_INFO: String = "interface.port_task_info"
        const val COMPONENT_BOARD_POPUP: String = "component.port_task_board:popup"
        val BOARD_INIT_SCRIPT: Int get() = "clientscript.sailing_port_task_board_init".asRSCM()
        val INFO_INIT_SCRIPT: Int get() = "clientscript.sailing_port_task_info_init".asRSCM()
        val OPEN_BOARD = AttributeKey<PortTaskBoardView>()

        const val COMPONENT_BOARD_CONTAINER: String = "component.port_task_board:container"

        const val COMPONENT_BOARD_CLOSE: String = "component.port_task_board:close"

        const val LOC_LEDGER_WITHDRAW: String = "loc.dock_loading_bay_ledger_table_withdraw"

        const val LOC_LEDGER_DEPOSIT: String = "loc.dock_loading_bay_ledger_table_deposit"

        const val OBJ_COINS: String = "obj.coins"

        const val LEDGER_TRANSFER_TICKS: Int = 2

        const val MES_NOTHING_ON_BOARD: String =
            "There are no tasks on this board that you can take right now."
        const val MES_HANDS_NOT_FREE: String = "You need both hands free to carry cargo."
        const val MES_NO_CARGO_HERE: String = "There is no cargo waiting for you at this port."
        const val MES_NOTHING_TO_DELIVER: String = "You have nothing to deliver here."
        const val MES_NOT_ENOUGH_SPACE: String =
            "You don't have enough inventory space to carry that cargo."
        const val MES_CARGO_MISSING: String = "You don't have all of the cargo for that task."

        val NOTICE_BOARD_PORTS: List<String> =
            listOf(
                "port_sarim",
                "pandemonium",
                "catherby",
                "port_khazard",
                "ruins_of_unkah",
                "rellekka",
                "port_piscarilius",
                "civitas_illa_fortis",
                "lands_end",
                "musa_point",
                "ardougne",
                "brimhaven",
                "corsair_cove",
                "the_summer_shore",
                "aldarin",
                "void_knights_outpost",
                "port_roberts",
                "red_rock",
                "etceteria",
                "port_tyras",
                "deepfin_point",
                "prifddinas",
                "lunar_isle",
            )

        val LEDGER_TABLE_PORTS: List<String> =
            listOf(
                "port_sarim",
                "musa_point",
                "pandemonium",
                "catherby",
                "entrana",
                "ardougne",
                "brimhaven",
                "port_khazard",
                "corsair_cove",
                "ruins_of_unkah",
                "port_piscarilius",
                "civitas_illa_fortis",
                "rellekka",
                "lands_end",
                "hosidius",
                "cairn_isle",
                "sunset_coast",
                "the_summer_shore",
                "aldarin",
                "void_knights_outpost",
                "port_roberts",
                "red_rock",
                "etceteria",
                "neitiznot",
                "jatizso",
                "port_tyras",
                "deepfin_point",
                "prifddinas",
                "piscatoris",
                "lunar_isle",
            )

        fun noticeBoardLoc(port: String): String = "loc.port_task_board_$port"

        fun ledgerTableLoc(port: String): String = "loc.dock_loading_bay_ledger_table_$port"

        // Pandemonium loc suffixes omit the article present in the dock dbrow.
        fun dockDbRow(port: String): String =
            when (port) {
                "pandemonium" -> "dbrow.sailing_dock_the_pandemonium"
                else -> "dbrow.sailing_dock_$port"
            }
    }
}
