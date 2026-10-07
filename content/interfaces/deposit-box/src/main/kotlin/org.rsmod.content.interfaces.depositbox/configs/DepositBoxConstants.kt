package org.rsmod.content.interfaces.depositbox.configs

object DepositBoxConstants {

    const val OPEN_SEQ = "seq.human_leverdown"

    const val INTERFACE_MAIN = "interface.bank_depositbox"

    const val COMP_ITEMS = "component.bank_depositbox:inventory"

    const val COMP_DEPOSIT_INV = "component.bank_depositbox:deposit_inv"
    const val COMP_DEPOSIT_WORN = "component.bank_depositbox:deposit_worn"

    const val COMP_DEPOSITWORN_TOGGLE = "component.bank_depositbox:depositworn_toggle"

    const val COMP_QUANTITY_1 = "component.bank_depositbox:1"
    const val COMP_QUANTITY_5 = "component.bank_depositbox:5"
    const val COMP_QUANTITY_10 = "component.bank_depositbox:10"
    const val COMP_QUANTITY_X = "component.bank_depositbox:x"
    const val COMP_QUANTITY_ALL = "component.bank_depositbox:all"

    // TODO: Inventory slot-lock settings button is not implemented.
    const val COM_LOCK_MENU = "component.bank_depositbox:lock_menu"

    val WORN_WEAPOS_SLOTS = intArrayOf(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)

    fun wornComponent(wearpos: Int): String = "component.bank_depositbox:slot$wearpos"

    const val INVENTORY_INTERFACE = "interface.inventory"
    const val INVENTORY_DISABLED = "interface.inventory_noops"
    const val INVENTORY_MAIN_TARGET = "component.toplevel_osrs_stretch:side3"
}
