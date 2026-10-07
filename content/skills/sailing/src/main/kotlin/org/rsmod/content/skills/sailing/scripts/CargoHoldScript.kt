package org.rsmod.content.skills.sailing.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.math.max
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.IfModalDrag
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.skills.sailing.CargoHoldSession
import org.rsmod.content.skills.sailing.CargoHoldSessions
import org.rsmod.content.skills.sailing.CargoQuantityMode
import org.rsmod.content.skills.sailing.SailingCargoHold
import org.rsmod.content.skills.sailing.cargoHold
import org.rsmod.content.skills.sailing.cargoHoldDrawStacked
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.isOk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CargoHoldScript : PluginScript() {
    override fun ScriptContext.startup() {

        onIfOpen(INTERFACE_SIDE) { player.onCargoHoldOpen() }
        onIfClose(INTERFACE_MAIN) { player.onCargoHoldClose() }

        onIfModalButton(COM_HOLD_ITEMS) { holdItemOp(it.comsub, it.op) }
        onIfModalButton(COM_SIDE_ITEMS) { sideItemOp(it.comsub, it.op) }

        onIfModalButton(COM_DEPOSITALL_INVENTORY) { depositAll { true } }
        onIfModalButton(COM_DEPOSITALL_CARGO) { depositAll(SailingCargoHold::isCourierCargo) }
        onIfModalButton(COM_DEPOSITALL_SALVAGE) { depositAll(SailingCargoHold::isShipwreckSalvage) }

        onIfModalButton(COM_TOGGLE_STACKS) { toggleStacks() }

        onIfModalButton(COM_QTY_1) { setQuantityMode(CargoQuantityMode.One) }
        onIfModalButton(COM_QTY_5) { setQuantityMode(CargoQuantityMode.Five) }
        onIfModalButton(COM_QTY_10) { setQuantityMode(CargoQuantityMode.Ten) }
        onIfModalButton(COM_QTY_X) { setQuantityMode(CargoQuantityMode.X) }
        onIfModalButton(COM_QTY_ALL) { setQuantityMode(CargoQuantityMode.All) }

        onIfModalDrag(COM_HOLD_ITEMS) { dragHoldItems(it) }
        onIfModalDrag(COM_SIDE_ITEMS) { dragSideItems(it) }
    }

    private fun Player.onCargoHoldOpen() {
        // Reject openings without a cargo session; guessing could expose another boat's hold.
        val session = CargoHoldSessions.current(this) ?: return
        val hold = cargoHold(session.boatSlot)
        setCargoHoldIfEvents(hold)
        refreshCargoHoldText(session.boatSlot, hold)
        startInvTransmit(hold)
    }

    private fun Player.onCargoHoldClose() {
        val session = CargoHoldSessions.current(this) ?: return
        stopInvTransmit(cargoHold(session.boatSlot))
        CargoHoldSessions.close(this)
    }

    private fun Player.setCargoHoldIfEvents(hold: Inventory) {
        ifSetEvents(
            COM_HOLD_ITEMS,
            hold.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op10,
            IfEvent.Depth2,
            IfEvent.DragTarget,
        )
        ifSetEvents(
            COM_SIDE_ITEMS,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op10,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )
    }

    private fun Player.refreshCargoHoldText(boatSlot: Int, hold: Inventory) {
        ifSetText(COM_CAPACITY, SailingCargoHold.capacity(this, boatSlot).toString())
        ifSetText(COM_OCCUPIEDSLOTS, hold.occupiedSpace().toString())
    }

    private suspend fun ProtectedAccess.holdItemOp(slot: Int, op: IfButtonOp) {
        val session = session() ?: return
        val hold = player.cargoHold(session.boatSlot)

        if (op == IfButtonOp.Op10) {
            objExamine(hold, slot)
            return
        }

        if (hold[slot] == null) {
            resendSlot(hold, 0)
            return
        }

        val count = resolveCount(session, op) ?: return
        val transaction =
            invMoveFromSlot(from = hold, into = inv, fromSlot = slot, count = count, strict = false)
        val result = transaction[0]

        if (result == TransactionResult.NotEnoughSpace) {
            mes(MES_NO_INVENTORY_SPACE)
            return
        }

        if (!result.isOk()) {
            return
        }

        if (!result.fullSuccess) {
            mes(MES_NO_INVENTORY_SPACE_FOR_THAT_MANY)
        }

        player.refreshCargoHoldText(session.boatSlot, hold)
    }

    private suspend fun ProtectedAccess.sideItemOp(slot: Int, op: IfButtonOp) {
        val session = session() ?: return

        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }

        if (inv[slot] == null) {
            resendSlot(inv, 0)
            return
        }

        val count = resolveCount(session, op) ?: return
        deposit(session.boatSlot, slot, count)
    }

    private fun ProtectedAccess.deposit(boatSlot: Int, slot: Int, count: Int): Boolean {
        val obj = inv[slot]
        if (obj == null) {
            resendSlot(inv, 0)
            return false
        }

        if (!SailingCargoHold.canStore(getInvObj(obj))) {
            mes(MES_CANNOT_STORE)
            return false
        }

        val capacity = SailingCargoHold.capacity(player, boatSlot)
        if (capacity <= 0) {
            mes(MES_NO_CARGO_HOLD)
            return false
        }

        val hold = player.cargoHold(boatSlot)
        val transaction =
            player.invTransaction(inv, hold) {
                val from = select(inv)
                val into = select(hold)
                transfer {
                    this.from = from
                    this.into = into
                    this.fromSlot = slot
                    this.intoSlot = 0
                    this.intoCapacity = capacity
                    this.count = count
                    this.uncert = true
                    this.strict = false
                }
            }
        val result = transaction.results.lastOrNull()

        if (result == TransactionResult.NotEnoughSpace) {
            mes(MES_HOLD_FULL)
            return false
        }

        if (!result.isOk()) {
            return false
        }

        if (!result.fullSuccess) {
            mes(MES_HOLD_FULL)
        }

        player.refreshCargoHoldText(boatSlot, hold)
        return true
    }

    private fun ProtectedAccess.depositAll(filter: (ItemServerType) -> Boolean) {
        val session = session() ?: return

        val capacity = SailingCargoHold.capacity(player, session.boatSlot)
        if (capacity <= 0) {
            mes(MES_NO_CARGO_HOLD)
            return
        }

        val keepSlots =
            inv.indices.filterTo(HashSet()) { slot ->
                val type = getOrNull(inv[slot])
                type == null || !SailingCargoHold.canStore(type) || !filter(type)
            }

        if (keepSlots.size == inv.size) {
            mes(MES_NOTHING_TO_DEPOSIT)
            return
        }

        val hold = player.cargoHold(session.boatSlot)
        val transaction =
            invMoveInv(from = inv, into = hold, intoCapacity = capacity, keepSlots = keepSlots)
        val result = transaction[0]

        if (result == TransactionResult.NotEnoughSpace) {
            mes(MES_HOLD_FULL)
            return
        }

        if (!result.isOk()) {
            return
        }

        if (!result.fullSuccess) {
            mes(MES_HOLD_FULL)
        }

        player.refreshCargoHoldText(session.boatSlot, hold)
    }

    private fun ProtectedAccess.toggleStacks() {
        player.cargoHoldDrawStacked = !player.cargoHoldDrawStacked
    }

    private fun ProtectedAccess.setQuantityMode(mode: CargoQuantityMode) {
        val session = session() ?: return
        session.quantityMode = mode
    }

    private fun ProtectedAccess.dragHoldItems(drag: IfModalDrag) {
        val session = session() ?: return
        val hold = player.cargoHold(session.boatSlot)
        val fromSlot = drag.selectedSlot ?: return resendSlot(hold, 0)
        val intoSlot = drag.targetSlot ?: return resendSlot(hold, 0)
        invMoveToSlot(hold, hold, fromSlot, intoSlot)
    }

    private fun ProtectedAccess.dragSideItems(drag: IfModalDrag) {
        val fromSlot = drag.selectedSlot ?: return resendSlot(inv, 0)
        val intoSlot = drag.targetSlot ?: return resendSlot(inv, 0)
        invMoveToSlot(inv, inv, fromSlot, intoSlot)
    }

    private fun ProtectedAccess.session(): CargoHoldSession? = CargoHoldSessions.current(player)

    private suspend fun ProtectedAccess.resolveCount(
        session: CargoHoldSession,
        op: IfButtonOp,
    ): Int? =
        when (op) {
            IfButtonOp.Op1 ->
                when (session.quantityMode) {
                    CargoQuantityMode.One -> 1
                    CargoQuantityMode.Five -> 5
                    CargoQuantityMode.Ten -> 10
                    CargoQuantityMode.X -> max(1, session.lastXInput)
                    CargoQuantityMode.All -> Int.MAX_VALUE
                }
            IfButtonOp.Op2 -> 1
            IfButtonOp.Op3 -> 5
            IfButtonOp.Op4 -> 10
            IfButtonOp.Op5 -> max(1, session.lastXInput)
            IfButtonOp.Op6 -> {
                val input = countDialog()
                session.lastXInput = input
                input
            }
            IfButtonOp.Op7 -> Int.MAX_VALUE
            else -> null
        }

    private companion object {
        const val INTERFACE_MAIN = "interface.sailing_boat_cargohold"
        const val INTERFACE_SIDE = "interface.sailing_boat_cargohold_side"

        const val COM_HOLD_ITEMS = "component.sailing_boat_cargohold:items"
        const val COM_SIDE_ITEMS = "component.sailing_boat_cargohold_side:items"
        const val COM_CAPACITY = "component.sailing_boat_cargohold:capacity"
        const val COM_OCCUPIEDSLOTS = "component.sailing_boat_cargohold:occupiedslots"
        const val COM_DEPOSITALL_CARGO = "component.sailing_boat_cargohold:depositall_cargo"
        const val COM_DEPOSITALL_SALVAGE = "component.sailing_boat_cargohold:depositall_salvage"
        const val COM_DEPOSITALL_INVENTORY = "component.sailing_boat_cargohold:depositall_inventory"
        const val COM_TOGGLE_STACKS = "component.sailing_boat_cargohold:toggle_stacks"
        const val COM_QTY_1 = "component.sailing_boat_cargohold:1"
        const val COM_QTY_5 = "component.sailing_boat_cargohold:5"
        const val COM_QTY_10 = "component.sailing_boat_cargohold:10"
        const val COM_QTY_X = "component.sailing_boat_cargohold:x"
        const val COM_QTY_ALL = "component.sailing_boat_cargohold:all"

        const val MES_NO_INVENTORY_SPACE = "You don't have enough inventory space."
        const val MES_NO_INVENTORY_SPACE_FOR_THAT_MANY =
            "You don't have enough inventory space to withdraw that many."
        const val MES_CANNOT_STORE = "You can't store that in your cargo hold."
        const val MES_NO_CARGO_HOLD = "This boat doesn't have a cargo hold."
        const val MES_HOLD_FULL = "Your cargo hold is full."
        const val MES_NOTHING_TO_DEPOSIT = "You have nothing to deposit."
    }
}
