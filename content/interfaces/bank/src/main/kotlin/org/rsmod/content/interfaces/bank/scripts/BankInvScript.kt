package org.rsmod.content.interfaces.bank.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.refs.params
import org.rsmod.api.enums.BankEnums.bank_equipment_tab_to_slots_map
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.interact.HeldBanksideEvents
import org.rsmod.api.player.events.interact.HeldDropEvents
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.interfaces.bank.BankLayout.compressBank
import org.rsmod.content.interfaces.bank.BankLayout.compressTabObjs
import org.rsmod.content.interfaces.bank.BankLayout.dragIntoTab
import org.rsmod.content.interfaces.bank.BankLayout.dragMainInv
import org.rsmod.content.interfaces.bank.BankLayout.dragSideInv
import org.rsmod.content.interfaces.bank.BankLayout.dragTab
import org.rsmod.content.interfaces.bank.BankLayout.notifySlotUpdate
import org.rsmod.content.interfaces.bank.BankLayout.removeFillers
import org.rsmod.content.interfaces.bank.BankLayout.removePlaceholders
import org.rsmod.content.interfaces.bank.BankLayout.restoreMainTabSize
import org.rsmod.content.interfaces.bank.BankLayout.selectTab
import org.rsmod.content.interfaces.bank.BankLayout.trimGapsAndReturnTrailingGaps
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.QuantityMode
import org.rsmod.content.interfaces.bank.alwaysPlacehold
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.configs.bank_constants
import org.rsmod.content.interfaces.bank.lastQtyInput
import org.rsmod.content.interfaces.bank.leftClickQtyMode
import org.rsmod.content.interfaces.bank.selectedTab
import org.rsmod.content.interfaces.bank.setBankWornBonuses
import org.rsmod.content.interfaces.bank.setBanksideExtraOps
import org.rsmod.content.interfaces.bank.util.rightShift
import org.rsmod.content.interfaces.bank.withdrawCert
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull
import org.rsmod.game.type.placeholder
import org.rsmod.game.type.uncert
import org.rsmod.game.type.untransform
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.isOk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankInvScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val wornBonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.setDefaultCapacity() }
        onIfOpen("interface.bankmain") { player.restoreMainTabSize() }

        onIfModalButton("component.bankside:items") { sideInvOp(it.comsub, it.op) }
        onIfModalButton("component.bankmain:items") { mainInvOp(it.comsub, it.op) }
        onIfModalButton("component.bankside:wornops") { wornInvOp(it.comsub, it.op) }
        onIfModalButton("component.bankmain:depositinv") { depositInv() }
        onIfModalButton("component.bankmain:depositworn") { depositWorn() }
        onIfModalButton("component.bankmain:tabs") { selectTab(it.comsub, it.op) }
        onIfModalButton("component.bankmain:incinerator_confirm") { incinerate(it.comsub, it.obj) }
        onIfModalDrag("component.bankmain:tabs") { dragTab(it) }
        onIfModalDrag("component.bankside:items") { dragSideInv(it) }
        onIfModalDrag("component.bankmain:items") { dragMainInv(it) }
        onIfModalDrag("component.bankside:wornops") { dragSideInv(it) }
        onIfModalDrag("component.bankmain:items", "component.bankmain:tabs") { dragIntoTab(it) }

        val wornComponents = bank_equipment_tab_to_slots_map.filterValuesNotNull().map { it.key to RSCM.getReverseMapping(RSCMType.COMPONENT, it.value.packed) }
        for ((slot, component) in wornComponents) {
            onIfModalButton(component) { wornOp(slot, it.op) }
        }

        onPlayerQueue("queue.bank_compress") { compressBank() }
    }

    private suspend fun ProtectedAccess.mainInvOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(bank, slot)
            return
        }
        mainInvWithdraw(slot, op)
    }

    private suspend fun ProtectedAccess.mainInvWithdraw(slot: Int, op: IfButtonOp) {
        val obj = bank[slot] ?: return resendSlot(bank, 0)
        val objType = getInvObj(obj)
        if (objType.isPlaceholder) {
            bank[slot] = null
            notifySlotUpdate(slot)
            return
        } else if (objType.isType("obj.bank_filler")) {
            if (op == IfButtonOp.Op7) {
                bank[slot] = null
                soundSynth("synth.PAPER_TURN")
                notifySlotUpdate(slot)
            } else if (op == IfButtonOp.Op8) {
                removeFillers()
            }
            return
        }
        val count =
            when (op) {
                IfButtonOp.Op1 -> resolveLeftClickQty()
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> resolveLastDepositQty()
                IfButtonOp.Op6 -> {
                    val input = countDialog()
                    lastQtyInput = input
                    input
                }
                IfButtonOp.Op7 -> obj.count
                IfButtonOp.Op8 -> obj.count - 1
                IfButtonOp.Op9 -> obj.count
                else -> throw IllegalStateException("Invalid main inv op: $op")
            }
        val transaction =
            invMoveFromSlot(
                from = bank,
                into = inv,
                fromSlot = slot,
                count = count,
                strict = false,
                cert = withdrawCert,
                placehold = alwaysPlacehold || op == IfButtonOp.Op9,
            )
        val result = transaction[0]

        if (result == TransactionResult.NotEnoughSpace) {
            mes("You don't have enough inventory space.")
            return
        }

        if (!result.isOk()) {
            return
        }

        if (withdrawCert && !objType.canCert) {
            mes("This item cannot be withdrawn as a note.")
        }

        if (!result.fullSuccess) {
            mes("You don't have enough inventory space to withdraw that many.")
        }

        if (bank[slot] == null) {
            notifySlotUpdate(slot)
        }

        setBanksideExtraOps()
    }

    private suspend fun ProtectedAccess.sideInvOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }
        val count =
            when (op) {
                IfButtonOp.Op2 -> resolveLeftClickQty()
                IfButtonOp.Op3 -> 1
                IfButtonOp.Op4 -> 5
                IfButtonOp.Op5 -> 10
                IfButtonOp.Op6 -> resolveLastDepositQty()
                IfButtonOp.Op7 -> {
                    val input = countDialog()
                    lastQtyInput = input
                    input
                }
                IfButtonOp.Op8 -> Int.MAX_VALUE
                IfButtonOp.Op1,
                IfButtonOp.Op9 -> {
                    clickBanksideExtraOp(slot, op)
                    return
                }
                else -> throw IllegalStateException("Invalid main inv op: $op")
            }
        invDeposit(slot, count, inv)
    }

    public fun ProtectedAccess.invDeposit(slot: Int, count: Int, inventory: Inventory): Boolean {
        val obj = inventory[slot]
        if (obj == null) {
            resendSlot(inventory, 0)
            return false
        }

        val objType = getInvObj(obj)

        if (objType.param(params.no_bank) != 0) {
            mes("A magical force prevents you from banking this item!")
            return false
        }

        val tab = selectedTab

        val placeholder = placeholder(objType)
        val containedObjSlot = bank.indexOfFirst { it?.id == obj.id || it?.id == placeholder.id }
        val prioritySlot =
            if (containedObjSlot != -1) {
                containedObjSlot
            } else {
                tab.slotRange(this).firstOrNull { bank[it] == null }
            }

        // Depositing into a focused tab compresses other tabs; main-tab deposits do not.
        if (prioritySlot == null && !tab.isMainTab) {
            val others = BankTab.entries - tab
            for (other in others) {
                val slots = other.slotRange(this)
                compressTabObjs(other)
                trimGapsAndReturnTrailingGaps(slots)
            }
        }

        val tabSlots = tab.slotRange(this)
        val insertQuery =
            player.invTransaction(inventory, bank) {
                val fromInv = select(inventory)
                val bankInv = select(bank)
                if (prioritySlot == null && !tab.isMainTab) {
                    rightShift {
                        this.from = bankInv
                        this.startSlot = tabSlots.last + 1
                        this.shiftCount = 1
                    }
                }
                transfer {
                    this.from = fromInv
                    this.into = bankInv
                    this.fromSlot = slot
                    this.intoSlot = prioritySlot ?: tabSlots.first
                    this.intoCapacity = bankCapacity
                    this.count = count
                    this.uncert = true
                    this.strict = false
                }
            }
        val result = insertQuery.results.last()

        // TODO: This message may be incorrect.
        if (result == TransactionResult.NotEnoughSpace && bank.occupiedSpace() >= bankCapacity) {
            mes("You don't have enough space in your bank account.")
            return false
        }

        if (result == TransactionResult.NotEnoughSpace) {
            mes("You already have a full stack of that item in the bank.")
            return false
        }

        if (!result.isOk()) {
            return false
        }

        val expectedSlot = tabSlots.last + 1
        val expectedObj = uncert(obj)
        if (bank[expectedSlot]?.id == expectedObj.id) {
            tab.increaseSize(this)
        }

        if (!result.fullSuccess) {
            mes("You already have a full stack of that item in the bank.")
        }

        setBanksideExtraOps()
        return true
    }

    private suspend fun ProtectedAccess.wornInvOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }

        val obj = inv[slot] ?: return resendSlot(inv, 0)
        if (op == IfButtonOp.Op9) {
            invDeposit(slot, obj.count, inv)
            return
        }

        if (op == IfButtonOp.Op1) {
            val type = getInvObj(obj)
            if (type.wearpos1 != -1) {
                val previous = worn[type.wearpos1]
                opHeld2(slot)
                val replacedPrevious = worn[type.wearpos1] !== previous
                if (replacedPrevious) {
                    setBanksideExtraOps()
                    setBankWornBonuses()
                }
            }
            return
        }
    }

    public fun ProtectedAccess.depositInv() {
        if (inv.isEmpty()) {
            mes("You have nothing to deposit.")
            return
        }
        depositInventory(inv)
    }

    public fun ProtectedAccess.depositWorn() {
        if (worn.isEmpty()) {
            mes("You have nothing to deposit.")
            return
        }
        val startWearposObjs = Wearpos.entries.associateWith { worn[it.slot] }
        depositInventory(worn)

        for ((wearpos, oldObj) in startWearposObjs) {
            if (oldObj == null || oldObj == worn[wearpos.slot]) {
                continue
            }
            WornUnequipOp.notifyWornUnequip(player, wearpos, getInvObj(oldObj), eventBus)
        }
    }

    private fun ProtectedAccess.depositInventory(from: Inventory) {
        val unbankableSlots =
            from.indices
                .filter {
                    val obj = from[it]
                    obj != null && getInvObj(obj).param(params.no_bank) != 0
                }
                .toHashSet()

        val tab = selectedTab
        if (tab.isMainTab) {
            val startLastOccupiedSpace = bank.lastOccupiedSlot()
            val transaction =
                invMoveInv(
                    from = from,
                    into = bank,
                    untransform = true,
                    intoStartSlot = tab.firstSlot(this),
                    intoCapacity = bankCapacity,
                    keepSlots = unbankableSlots,
                )
            val result = transaction[0]

            if (result == TransactionResult.NotEnoughSpace) {
                mes("Your bank cannot hold your items.")
                return
            }

            if (transaction.completed() == 0 && unbankableSlots.isNotEmpty()) {
                mes("Your items cannot be stored in the bank.")
            } else if (unbankableSlots.isNotEmpty()) {
                mes("Some of your items cannot be stored in the bank.")
            }

            val lastOccupiedSlotDiff = bank.lastOccupiedSlot() - startLastOccupiedSpace
            if (lastOccupiedSlotDiff < 0) {
                throw IllegalStateException(
                    "`lastOccupiedSlotDiff` should not be negative: " +
                        "start=$startLastOccupiedSpace, curr=${bank.lastOccupiedSlot()}"
                )
            }

            if (lastOccupiedSlotDiff > 0) {
                tab.increaseSize(this, lastOccupiedSlotDiff)
            }

            setBanksideExtraOps()
            return
        }

        val containedBankObjs = bank.mapNotNull { it?.id }.toHashSet()

        val uniqueInvObjs = mutableSetOf<Int>()
        for (invObj in from) {
            val type = getOrNull(invObj) ?: continue
            val uncert = uncert(type)
            if (uncert.param(params.no_bank) != 0) {
                continue
            }
            uniqueInvObjs += uncert.id
        }

        val containedObjMatches =
            uniqueInvObjs.count { obj ->
                val type = untransform(ServerCacheManager.getItem(obj)!!)
                val containsType = type.id in containedBankObjs
                val containsPlaceholder =
                    type.hasPlaceholder && placeholder(type).id in containedBankObjs
                containsType || containsPlaceholder
            }
        val requiredSlots = uniqueInvObjs.count()
        val emptySlotCount = tab.slotRange(this).count { bank[it] == null }
        val newInsertSlots = requiredSlots - containedObjMatches - emptySlotCount

        check(newInsertSlots >= 0) {
            "`newInsertSlots` should not be negative: $newInsertSlots " +
                "(required=$requiredSlots, contained=$containedObjMatches, empty=$emptySlotCount)"
        }

        if (newInsertSlots > 0) {

            val others = BankTab.entries - tab
            for (other in others) {
                val slots = other.slotRange(this)
                compressTabObjs(other)
                trimGapsAndReturnTrailingGaps(slots)
            }

            val tabSlots = tab.slotRange(this)
            val shiftQuery =
                player.invTransaction(bank) {
                    val bankInv = select(bank)
                    rightShift {
                        this.from = bankInv
                        this.startSlot = tabSlots.last + 1
                        this.shiftCount = newInsertSlots
                    }
                }

            if (shiftQuery.failure) {
                mes("Your bank cannot hold your items.")
                return
            }
        }

        val fromSlots = from.indices.filter { from[it] != null }.distinctBy { from[it]?.id }
        val tabSlots = tab.slotRange(this)

        val bankCapacity = bankCapacity
        val filteredFromSlots = fromSlots - unbankableSlots
        val transferQuery =
            player.invTransaction(from, bank) {
                val fromInv = select(from)
                val bankInv = select(bank)
                for (slot in filteredFromSlots) {
                    transfer {
                        this.from = fromInv
                        this.into = bankInv
                        this.fromSlot = slot
                        this.intoSlot = tabSlots.first
                        this.intoCapacity = bankCapacity
                        this.count = Int.MAX_VALUE
                        this.uncert = true
                        this.untransform = true
                        this.strict = false
                    }
                }
            }
        val noneCompleted = transferQuery.noneCompleted()

        if (noneCompleted && filteredFromSlots.isEmpty()) {
            mes("Your items cannot be stored in the bank.")
            return
        }

        if (noneCompleted) {
            mes("Your bank cannot hold your items.")
            return
        }

        if (unbankableSlots.isNotEmpty()) {
            mes("Some of your items cannot be stored in the bank.")
        }

        if (newInsertSlots > 0) {
            tab.increaseSize(this, newInsertSlots)
        }
    }

    private fun ProtectedAccess.resolveLeftClickQty(): Int =
        when (leftClickQtyMode) {
            QuantityMode.One -> 1
            QuantityMode.Five -> 5
            QuantityMode.Ten -> 10
            QuantityMode.X -> resolveLastDepositQty()
            QuantityMode.All -> Int.MAX_VALUE
        }

    private fun ProtectedAccess.resolveLastDepositQty(): Int = max(1, lastQtyInput)

    private fun ProtectedAccess.incinerate(comsub: Int, objType: ItemServerType?) {
        if (objType == null || objType.isPlaceholder) {
            return
        }
        val slot = comsub - 1
        val obj = bank[slot] ?: return resendSlot(bank, 0)

        if (!obj.isType(objType)) {
            resendSlot(bank, slot)
            return
        }

        bank[slot] = null
        soundSynth("synth.FIREBREATH")
        notifySlotUpdate(slot)

        val dispose = HeldDropEvents.Dispose(player, "inv.bank", slot, obj)
        publish(dispose)
    }

    private fun ProtectedAccess.setBanksideExtraOps() {
        player.setBanksideExtraOps()
    }

    private fun ProtectedAccess.setBankWornBonuses() {
        player.setBankWornBonuses(wornBonuses, weaponSpeeds)
    }

    private suspend fun ProtectedAccess.clickBanksideExtraOp(slot: Int, op: IfButtonOp) {
        val obj = inv[slot] ?: return resendSlot(inv, 0)
        val type = getInvObj(obj)

        val isWearable = type.wearpos1 != -1
        if (isWearable) {
            val wearOpIndex = type.param(params.wear_op_index)
            val hasWearOp = type.hasInvOp(wearOpIndex)
            if (hasWearOp) {
                val previous = worn[type.wearpos1]
                opHeld2(slot)
                val replacedPrevious = worn[type.wearpos1] !== previous
                if (replacedPrevious) {
                    setBanksideExtraOps()
                    setBankWornBonuses()
                }
            }
            return
        }

        val isFood = type.isContentType("content.food")
        val isPotion = type.isContentType("content.potion")

        val isConsumable = isFood || isPotion
        if (isConsumable) {
            opHeld1(slot)
            return
        }

        val event = HeldBanksideEvents.Type(player, slot, type, op)
        publish(event)
    }

    private fun ProtectedAccess.wornOp(wornSlot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(worn, wornSlot)
            return
        }
        val obj = worn[wornSlot] ?: return resendSlot(worn, wornSlot)

        if (op == IfButtonOp.Op2) {
            val type = getInvObj(obj)
            val deposited = invDeposit(wornSlot, obj.count, worn)
            if (deposited) {
                val wearpos = Wearpos[type.wearpos1]
                checkNotNull(wearpos) {
                    "Wearpos should not be null for worn obj: " +
                        "wornSlot=$wornSlot, obj=$obj, type=$type"
                }
                WornUnequipOp.notifyWornUnequip(player, wearpos, type, eventBus)
            }
            return
        }

        if (op == IfButtonOp.Op1) {
            val result = wornUnequip(wornSlot)
            if (result is WornUnequipResult.Fail) {
                result.message?.let(::mes)
                return
            }
            setBanksideExtraOps()
            setBankWornBonuses()
            return
        }
    }

    suspend fun releasePlaceholders(access: ProtectedAccess) {
        val confirmation =
            access.confirmOverlay(
                target = "component.bankmain:popup",
                title = "Release all placeholders?",
                text =
                    "This option will release <col=ffb83f>all</col> the placeholders " +
                        "throughout your <col=ffb83f>whole bank</col>." +
                        "<br><br>Are you sure?",
                cancel = "<col=ff0000>Cancel</col>",
                confirm = "<col=0dc10d>Release them all</col>",
            )
        if (confirmation) {
            access.removePlaceholders()
        }
    }

    suspend fun addBankFillers(access: ProtectedAccess, requestedCount: Int?) {
        val bank = access.bank
        val bankCapacity = access.bankCapacity

        val freeSpace = bankCapacity - bank.occupiedSpace()
        if (freeSpace <= 0) {
            access.mes("Your bank is already full, so there is no reason to add any bank fillers.")
            return
        }

        val count = requestedCount ?: access.countDialog()
        val cappedCount = min(freeSpace, count)
        if (cappedCount == 0) {
            return
        }

        val targetTab = BankTab.Main
        val startSlot = targetTab.slotRange(access).first
        val emptySlots = (startSlot until bankCapacity).filter { bank[it] == null }

        var completed = 0
        for (slot in emptySlots) {
            if (completed >= count) {
                break
            }
            if (bank[slot] == null) {
                // Give the filler vars to avoid any sort of merging through later transactions.
                val filler = InvObj("obj.bank_filler", vars = 1)
                bank[slot] = filler
                completed++
            }
        }

        if (completed == 0) {
            access.mes("Your bank is already full, so there is no reason to add any bank fillers.")
        } else {
            val formatCount = if (completed == 1) "bank filler" else "bank fillers"
            access.mes("You add $completed $formatCount to your bank.")
            targetTab.increaseSize(access, completed)
        }
    }

    private fun Player.setDefaultCapacity() {
        if (bankCapacity == 0) {
            bankCapacity = bank_constants.default_capacity
        }
    }
}
