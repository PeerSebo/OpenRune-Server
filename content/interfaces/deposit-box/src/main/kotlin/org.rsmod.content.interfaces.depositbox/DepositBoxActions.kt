package org.rsmod.content.interfaces.depositbox

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.interfaces.bank.QuantityMode
import org.rsmod.content.interfaces.depositbox.configs.DepositBoxConfig
import org.rsmod.content.interfaces.depositbox.configs.DepositBoxConstants
import org.rsmod.game.entity.Player

private var Player.depositBoxMode by intVarBit("varbit.depositbox_mode")
private var Player.depositBoxQtyInput by intVarp("varp.depositbox_requestedquantity")

internal var Player.hideDepositWornButton by boolVarBit("varbit.depositbox_hidedepositworn")

// The native full-stack setting is inverted relative to its displayed option.
internal val ProtectedAccess.opLocUDepositAll by boolVarBit("varbit.bank_depositbox_oplocu_askquantity")

private fun QuantityMode.toDepositBoxMode(): Int =
    when (this) {
        QuantityMode.Ten -> QuantityMode.All.varValue
        QuantityMode.All -> QuantityMode.Ten.varValue
        else -> varValue
    }

private fun quantityModeOf(depositBoxMode: Int): QuantityMode =
    when (depositBoxMode) {
        QuantityMode.Ten.varValue -> QuantityMode.All
        QuantityMode.All.varValue -> QuantityMode.Ten
        else -> QuantityMode.entries.firstOrNull { it.varValue == depositBoxMode } ?: QuantityMode.One
    }

internal var ProtectedAccess.depositQuantityMode: QuantityMode
    get() = quantityModeOf(player.depositBoxMode)
    set(value) {
        player.depositBoxMode = value.toDepositBoxMode()
    }

internal var ProtectedAccess.depositQuantityInput: Int
    get() = player.depositBoxQtyInput
    set(value) {
        player.depositBoxQtyInput = value
    }

internal fun ProtectedAccess.depositOption1Qty(): Int =
    when (depositQuantityMode) {
        QuantityMode.One -> 1
        QuantityMode.Five -> 5
        QuantityMode.Ten -> 10
        QuantityMode.X -> maxOf(1, depositQuantityInput)
        QuantityMode.All -> Int.MAX_VALUE
    }

internal fun ProtectedAccess.heldDepositCount(slot: Int): Int {
    val obj = inv[slot] ?: return 0
    val objType = ServerCacheManager.getItem(obj.id) ?: return 0
    return inv.count(obj, objType)
}

internal suspend fun ProtectedAccess.requestDepositQuantity(count: Int): Int {
    if (count == 1) {
        return 1
    }
    val amount = when {
        count > 10 -> choice5(
            "1", 1,
            "5", 5,
            "10", 10,
            "X", null,
            "All", Int.MAX_VALUE,
            title = "How many would you like to deposit?",
        )
        count > 5 -> choice4(
            "1", 1,
            "5", 5,
            "X", null,
            "All", Int.MAX_VALUE,
            title = "How many would you like to deposit?",
        )
        else -> choice3(
            "1", 1,
            "X", null,
            "All", Int.MAX_VALUE,
            title = "How many would you like to deposit?",
        )
    }
    return amount ?: countDialog("How many would you like to deposit? 1 - ${count.formatAmount}")
}

internal fun ProtectedAccess.playDepositAnim() {
    anim(DepositBoxConstants.OPEN_SEQ)
}

internal fun ProtectedAccess.resolveDepositSlot(clickedSlot: Int): Int {
    if (!DepositBoxConfig.DEPOSIT_TOP_TO_BOTTOM) {
        return clickedSlot
    }
    val id = inv[clickedSlot]?.id ?: return clickedSlot
    val firstSlot = inv.indexOfFirst { it?.id == id }
    return if (firstSlot != -1) firstSlot else clickedSlot
}
