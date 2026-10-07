package org.rsmod.content.skills.farming.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpContentNpc1
import org.rsmod.api.script.onOpContentNpc3
import org.rsmod.api.script.onOpContentNpc4
import org.rsmod.api.script.onOpContentNpcU
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunInterface
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunProduce
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.BOTTOMLESS_BUCKET_EMPTY
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.BOTTOMLESS_BUCKET_FILLED
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.BOTTOMLESS_BUCKET_QUANTITY_VARBIT
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.BOTTOMLESS_BUCKET_TYPE_VARBIT
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.COUNT_SLOTS
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.MAGIC_SECATEURS_VARBIT
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.SECATEURS
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.SELECTED_QUANTITY_VARBIT
import org.rsmod.content.skills.farming.configs.FarmingLeprechaunSlots.WATERING_CAN_VARBIT
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.content.skills.farming.configs.LeprechaunCountSlot
import org.rsmod.game.entity.Player
import org.rsmod.game.type.cert
import org.rsmod.game.type.uncert
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FarmingToolLeprechaunScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpContentNpc1(CONTENT) { talkTo() }
        onOpContentNpc3(CONTENT) { openStorage() }
        onOpContentNpc4(CONTENT) { depositAllTools() }
        onOpContentNpcU(CONTENT) { useItemOn(it.objType) }

        for (slot in COUNT_SLOTS) {
            if (slot === SECATEURS) continue
            onIfModalButton(mainComponent(slot.component)) { withdraw(slot, it.op) }
            onIfModalButton(sideComponent(slot.component)) { deposit(slot, it.op) }
        }
        onIfModalButton(mainComponent("secateurs")) { withdrawSecateurs(it.op) }
        onIfModalButton(sideComponent("secateurs")) { depositSecateurs(it.op) }
        onIfModalButton(mainComponent("wateringcan")) { withdrawWateringCan() }
        onIfModalButton(sideComponent("wateringcan")) { depositWateringCan() }
        onIfModalButton(mainComponent("bottomless_bucket")) { withdrawBottomlessBucket() }
        onIfModalButton(sideComponent("bottomless_bucket")) { depositBottomlessBucket() }
        onIfModalButton(mainComponent("deposit_all")) { depositAllTools() }
        onIfModalButton(mainComponent("quantity_1")) { selectedQuantity = 0 }
        onIfModalButton(mainComponent("quantity_5")) { selectedQuantity = 1 }
        onIfModalButton(mainComponent("quantity_x")) { selectedQuantity = 3 }
        onIfModalButton(mainComponent("quantity_all")) { selectedQuantity = 2 }
    }

    private suspend fun ProtectedAccess.talkTo() {
        val open =
            choice2(
                FarmingMessages.LEPRECHAUN_CHECK_STORAGE,
                true,
                FarmingMessages.LEPRECHAUN_NEVERMIND,
                false,
            )
        if (open) openStorage()
    }

    private fun ProtectedAccess.openStorage() {
        if (player.vars[BOTTOMLESS_BUCKET_TYPE_VARBIT] == 0 &&
            player.vars[BOTTOMLESS_BUCKET_QUANTITY_VARBIT] > 0
        ) {
            VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_TYPE_VARBIT, 1)
            VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_QUANTITY_VARBIT, 0)
        }
        ifOpenMainSidePair(MAIN_INTERFACE, SIDE_INTERFACE)
        runClientScript(
            "clientscript.[clientscript,farming_tools_main_init]".asRSCM(RSCMType.CLIENTSCRIPT),
            *FarmingLeprechaunInterface.mainArguments.map { it.asRSCM(RSCMType.COMPONENT) as Any }
                .toTypedArray(),
        )
        runClientScript(
            "clientscript.[clientscript,farming_tools_side_init]".asRSCM(RSCMType.CLIENTSCRIPT),
            *FarmingLeprechaunInterface.sideArguments.map { it.asRSCM(RSCMType.COMPONENT) as Any }
                .toTypedArray(),
        )
        for (slot in COUNT_SLOTS) {
            ifSetEvents(mainComponent(slot.component), -1..0,
                IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op9)
            ifSetEvents(sideComponent(slot.component), -1..0,
                IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4)
        }
        for (component in listOf("wateringcan", "bottomless_bucket")) {
            ifSetEvents(mainComponent(component), -1..0, IfEvent.Op1)
            ifSetEvents(sideComponent(component), -1..0, IfEvent.Op1)
        }
        ifSetEvents(mainComponent("deposit_all"), -1..-1, IfEvent.Op1)
        ifSetEvents(mainComponent("quantity_1"), -1..-1, IfEvent.Op1)
        ifSetEvents(mainComponent("quantity_5"), -1..-1, IfEvent.Op1)
        ifSetEvents(mainComponent("quantity_x"), -1..-1, IfEvent.Op1)
        ifSetEvents(mainComponent("quantity_all"), -1..-1, IfEvent.Op1)
    }

    private fun ProtectedAccess.depositAllTools() {
        var moved = false
        for (slot in COUNT_SLOTS) {
            if (depositQuiet(slot)) moved = true
        }
        if (depositSecateursQuiet()) moved = true
        if (depositWateringCanQuiet()) moved = true
        if (depositBottomlessBucketQuiet()) moved = true
        mes(
            if (moved) FarmingMessages.LEPRECHAUN_DEPOSITED_ALL
            else FarmingMessages.LEPRECHAUN_NOTHING_TO_DEPOSIT
        )
    }

    private suspend fun ProtectedAccess.useItemOn(objType: ItemServerType) {
        val name = objType.internalName
        if (name in FarmingLeprechaunProduce.NOTEABLE) {
            mes(
                if (noteItemQuiet(name)) FarmingMessages.LEPRECHAUN_EXCHANGED
                else FarmingMessages.LEPRECHAUN_NOTHING_TO_EXCHANGE
            )
            return
        }
        val baseName = uncert(objType).internalName
        val slot = COUNT_SLOTS.firstOrNull { it.item == baseName && it !== SECATEURS }
        when {
            slot != null -> deposit(slot)
            baseName == FarmingParams.SECATEURS || name == FarmingParams.MAGIC_SECATEURS ->
                depositSecateurs()
            name in FarmingParams.WATERING_CANS -> depositWateringCan()
            name == BOTTOMLESS_BUCKET_EMPTY || name == BOTTOMLESS_BUCKET_FILLED ->
                depositBottomlessBucket()
            else -> mes(Constants.dm_default)
        }
    }

    private suspend fun ProtectedAccess.withdraw(slot: LeprechaunCountSlot, op: IfButtonOp = IfButtonOp.Op1) {
        val stored = totalStored(player, slot)
        if (stored <= 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_STORED)
            return
        }
        val amount = if (op == IfButtonOp.Op9) stored else resolveQuantity(stored, op)
        if (amount <= 0) return
        val result = invAdd(inv, slot.item, count = amount, strict = false, cert = op == IfButtonOp.Op9)
        // Non-strict success can mean zero moved; use completed() before debiting storage.
        val completed = result.completed()
        if (completed <= 0) {
            mes(FarmingMessages.LEPRECHAUN_INV_FULL)
            return
        }
        setStored(player, slot, stored - completed)
        if (completed < amount) {
            mes(FarmingMessages.leprechaunPartialWithdraw(completed))
        }
    }

    private suspend fun ProtectedAccess.deposit(slot: LeprechaunCountSlot, op: IfButtonOp = IfButtonOp.Op1) {
        val held = heldIncludingNoted(slot.item)
        if (held <= 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_TO_STORE)
            return
        }
        val stored = totalStored(player, slot)
        val room = slot.capacity - stored
        if (room <= 0) {
            mes(FarmingMessages.LEPRECHAUN_STORAGE_FULL)
            return
        }
        val amount = resolveQuantity(minOf(held, room), op)
        if (amount <= 0) return
        val completed = delIncludingNoted(slot.item, amount)
        if (completed <= 0) return
        setStored(player, slot, stored + completed)
    }

    private fun ProtectedAccess.depositQuiet(slot: LeprechaunCountSlot): Boolean {
        val held = heldIncludingNoted(slot.item)
        if (held <= 0) return false
        val stored = totalStored(player, slot)
        val room = slot.capacity - stored
        if (room <= 0) return false
        val amount = minOf(held, room)
        val completed = delIncludingNoted(slot.item, amount)
        if (completed <= 0) return false
        setStored(player, slot, stored + completed)
        return true
    }

    private fun ProtectedAccess.heldIncludingNoted(item: String): Int {
        val notedType = cert(item)
        val notedName = notedType.internalName
        val notedHeld = if (notedName != item) invTotal(inv, notedName) else 0
        return invTotal(inv, item) + notedHeld
    }

    private fun ProtectedAccess.delIncludingNoted(item: String, amount: Int): Int {
        var remaining = amount
        var removed = 0
        val unnotedResult = invDel(inv, item, count = remaining, strict = false)
        removed += unnotedResult.completed()
        remaining -= unnotedResult.completed()
        if (remaining <= 0) return removed
        val notedType = cert(item)
        val notedName = notedType.internalName
        if (notedName == item) return removed
        val notedResult = invDel(inv, notedName, count = remaining, strict = false)
        removed += notedResult.completed()
        return removed
    }

    private suspend fun ProtectedAccess.withdrawSecateurs(op: IfButtonOp = IfButtonOp.Op1) {
        val hasMagic = player.vars[MAGIC_SECATEURS_VARBIT] != 0
        val regularStored = totalStored(player, SECATEURS)
        val available = regularStored + if (hasMagic) 1 else 0
        if (available <= 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_STORED)
            return
        }
        var amount = if (op == IfButtonOp.Op9 && !hasMagic) available else resolveQuantity(available, op)
        if (amount <= 0) return
        // Non-strict success can mean zero moved; debit only completed() units.
        var withdrawn = 0
        if (hasMagic) {
            val result = invAdd(inv, FarmingParams.MAGIC_SECATEURS, count = 1, strict = false)
            val completed = result.completed()
            if (completed <= 0) {
                mes(FarmingMessages.LEPRECHAUN_INV_FULL)
                return
            }
            VarPlayerIntMapSetter.set(player, MAGIC_SECATEURS_VARBIT, 0)
            withdrawn++
            amount--
        }
        if (amount > 0) {
            val result = invAdd(inv, SECATEURS.item, count = amount, strict = false, cert = op == IfButtonOp.Op9)
            val completed = result.completed()
            if (completed <= 0) {
                mes(
                    if (withdrawn > 0) FarmingMessages.leprechaunPartialWithdraw(withdrawn)
                    else FarmingMessages.LEPRECHAUN_INV_FULL
                )
                return
            }
            setStored(player, SECATEURS, regularStored - completed)
            withdrawn += completed
            if (completed < amount) {
                mes(FarmingMessages.leprechaunPartialWithdraw(withdrawn))
            }
        }
    }

    private suspend fun ProtectedAccess.depositSecateurs(op: IfButtonOp = IfButtonOp.Op1) {
        if (FarmingLeprechaunInterface.quantityMode(selectedQuantity, op) == null) return
        val heldMagic = invTotal(inv, FarmingParams.MAGIC_SECATEURS) > 0
        val heldRegular = heldIncludingNoted(SECATEURS.item)
        if (!heldMagic && heldRegular <= 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_TO_STORE)
            return
        }
        val regularStored = totalStored(player, SECATEURS)
        val room = SECATEURS.capacity - regularStored
        val canStoreMagic = heldMagic && player.vars[MAGIC_SECATEURS_VARBIT] == 0
        val available = minOf(heldRegular, room) + if (canStoreMagic) 1 else 0
        if (available <= 0) {
            mes(FarmingMessages.LEPRECHAUN_STORAGE_FULL)
            return
        }
        // Prompt before transferring either secateurs variant; Store-1 moves one pair total.
        var remaining = resolveQuantity(available, op)
        if (remaining <= 0) return
        if (canStoreMagic) {
            val result = invDel(inv, FarmingParams.MAGIC_SECATEURS, count = 1)
            if (result.failure) return
            VarPlayerIntMapSetter.set(player, MAGIC_SECATEURS_VARBIT, 1)
            remaining--
        }
        if (remaining > 0) {
            val completed = delIncludingNoted(SECATEURS.item, remaining)
            setStored(player, SECATEURS, regularStored + completed)
        }
    }

    private fun ProtectedAccess.depositSecateursQuiet(): Boolean {
        var moved = false
        val heldMagic = invTotal(inv, FarmingParams.MAGIC_SECATEURS) > 0
        if (heldMagic && player.vars[MAGIC_SECATEURS_VARBIT] == 0) {
            val result = invDel(inv, FarmingParams.MAGIC_SECATEURS, count = 1, strict = false)
            if (result.success) {
                VarPlayerIntMapSetter.set(player, MAGIC_SECATEURS_VARBIT, 1)
                moved = true
            }
        }
        val heldRegular = invTotal(inv, SECATEURS.item)
        val regularStored = totalStored(player, SECATEURS)
        val room = SECATEURS.capacity - regularStored
        if (heldRegular > 0 && room > 0) {
            val amount = minOf(heldRegular, room)
            val result = invDel(inv, SECATEURS.item, count = amount, strict = false)
            if (result.success) {
                setStored(player, SECATEURS, regularStored + amount)
                moved = true
            }
        }
        return moved
    }

    private fun ProtectedAccess.withdrawWateringCan() {
        val raw = player.vars[WATERING_CAN_VARBIT]
        if (raw <= 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_STORED)
            return
        }
        val dose = raw - 1
        val item = FarmingParams.WATERING_CANS.getOrNull(dose) ?: return
        val result = invAdd(inv, item, count = 1, strict = false)
        if (result.completed() <= 0) {
            mes(FarmingMessages.LEPRECHAUN_INV_FULL)
            return
        }
        VarPlayerIntMapSetter.set(player, WATERING_CAN_VARBIT, 0)
    }

    private fun ProtectedAccess.depositWateringCan() {
        if (player.vars[WATERING_CAN_VARBIT] != 0) {
            mes(FarmingMessages.LEPRECHAUN_ONE_CAN_ONLY)
            return
        }
        val dose = FarmingParams.WATERING_CANS.indexOfFirst { invTotal(inv, it) > 0 }
        if (dose < 0) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_TO_STORE)
            return
        }
        val result = invDel(inv, FarmingParams.WATERING_CANS[dose], count = 1, strict = false)
        if (!result.success) return
        VarPlayerIntMapSetter.set(player, WATERING_CAN_VARBIT, dose + 1)
    }

    private fun ProtectedAccess.depositWateringCanQuiet(): Boolean {
        if (player.vars[WATERING_CAN_VARBIT] != 0) return false
        val dose = FarmingParams.WATERING_CANS.indexOfFirst { invTotal(inv, it) > 0 }
        if (dose < 0) return false
        val result = invDel(inv, FarmingParams.WATERING_CANS[dose], count = 1, strict = false)
        if (!result.success) return false
        VarPlayerIntMapSetter.set(player, WATERING_CAN_VARBIT, dose + 1)
        return true
    }

    private fun ProtectedAccess.withdrawBottomlessBucket() {
        if (!hasStoredBottomlessBucket()) {
            mes(FarmingMessages.LEPRECHAUN_NOTHING_STORED)
            return
        }
        val result = invAdd(inv, BOTTOMLESS_BUCKET_EMPTY, count = 1, strict = false)
        if (result.completed() <= 0) {
            mes(FarmingMessages.LEPRECHAUN_INV_FULL)
            return
        }
        VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_QUANTITY_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_TYPE_VARBIT, 0)
    }

    // Preserve a filled bottomless bucket's charges; ownership alone cannot reconstruct them.
    private fun ProtectedAccess.depositBottomlessBucket() {
        if (hasStoredBottomlessBucket()) {
            mes(FarmingMessages.LEPRECHAUN_STORAGE_FULL)
            return
        }
        if (invTotal(inv, BOTTOMLESS_BUCKET_EMPTY) > 0) {
            val result = invDel(inv, BOTTOMLESS_BUCKET_EMPTY, count = 1, strict = false)
            if (!result.success) return
            VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_TYPE_VARBIT, 1)
            VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_QUANTITY_VARBIT, 0)
            return
        }
        if (invTotal(inv, BOTTOMLESS_BUCKET_FILLED) > 0) {
            mes(FarmingMessages.LEPRECHAUN_CANNOT_STORE_THAT)
            return
        }
        mes(FarmingMessages.LEPRECHAUN_NOTHING_TO_STORE)
    }

    private fun ProtectedAccess.depositBottomlessBucketQuiet(): Boolean {
        if (hasStoredBottomlessBucket()) return false
        if (invTotal(inv, BOTTOMLESS_BUCKET_EMPTY) <= 0) return false
        val result = invDel(inv, BOTTOMLESS_BUCKET_EMPTY, count = 1, strict = false)
        if (!result.success) return false
        VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_TYPE_VARBIT, 1)
        VarPlayerIntMapSetter.set(player, BOTTOMLESS_BUCKET_QUANTITY_VARBIT, 0)
        return true
    }

    private fun ProtectedAccess.hasStoredBottomlessBucket(): Boolean =
        player.vars[BOTTOMLESS_BUCKET_TYPE_VARBIT] > 0 ||
            player.vars[BOTTOMLESS_BUCKET_QUANTITY_VARBIT] > 0

    private fun ProtectedAccess.noteItemQuiet(name: String): Boolean {
        val held = invTotal(inv, name)
        if (held <= 0) return false
        val certName = cert(name).internalName
        if (certName == name) return false
        val result = invReplace(inv, replace = name, count = held, replacement = certName)
        return result.success
    }

    private var ProtectedAccess.selectedQuantity: Int
        get() = player.vars[SELECTED_QUANTITY_VARBIT]
        set(value) = VarPlayerIntMapSetter.set(player, SELECTED_QUANTITY_VARBIT, value)

    private suspend fun ProtectedAccess.resolveQuantity(available: Int, op: IfButtonOp = IfButtonOp.Op1): Int {
        val mode = FarmingLeprechaunInterface.quantityMode(selectedQuantity, op) ?: return 0
        val requested =
            when (mode) {
                1 -> 5
                2 -> available
                3 -> countDialog()
                else -> 1
            }
        return minOf(requested, available)
    }

    private fun totalStored(player: Player, slot: LeprechaunCountSlot): Int {
        var total = 0
        var shift = 0
        for ((index, field) in slot.fields.withIndex()) {
            total = total or (player.vars[field] shl shift)
            shift += slot.fieldWidths[index]
        }
        return total
    }

    private fun setStored(player: Player, slot: LeprechaunCountSlot, newTotal: Int) {
        var remaining = newTotal.coerceIn(0, slot.capacity)
        for ((index, field) in slot.fields.withIndex()) {
            val width = slot.fieldWidths[index]
            val mask = (1 shl width) - 1
            VarPlayerIntMapSetter.set(player, field, remaining and mask)
            remaining = remaining shr width
        }
    }

    private fun mainComponent(component: String): String = "component.farming_tools:$component"

    private fun sideComponent(component: String): String = "component.farming_tools_side:$component"

    private companion object {
        const val CONTENT = "content.farming_tool_leprechaun"

        const val MAIN_INTERFACE = "interface.farming_tools"
        const val SIDE_INTERFACE = "interface.farming_tools_side"
    }
}
