package org.rsmod.content.skills.farming.configs

import dev.openrune.types.aconverted.interf.IfButtonOp

internal object FarmingLeprechaunInterface {
    private val slots = listOf(
        "universe", "rake", "dibber", "spade", "secateurs", "wateringcan", "trowel",
        "plantcure", "bucket", "compost", "supercompost", "ultracompost", "bottomless_bucket",
    )
    val mainArguments = (slots + "quantity_selection").map { "component.farming_tools:$it" }
    val sideArguments = slots.map { "component.farming_tools_side:$it" }

    // The selected quantity occupies op1; other quantity options move with it.
    fun quantityMode(selected: Int, op: IfButtonOp): Int? {
        val order = when (selected) {
            1 -> listOf(1, 0, 3, 2)
            2 -> listOf(2, 0, 1, 3)
            3 -> listOf(3, 0, 1, 2)
            else -> listOf(0, 1, 3, 2)
        }
        val index = when (op) {
            IfButtonOp.Op1 -> 0
            IfButtonOp.Op2 -> 1
            IfButtonOp.Op3 -> 2
            IfButtonOp.Op4 -> 3
            else -> return null
        }
        return order[index]
    }
}
