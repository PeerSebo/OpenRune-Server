package org.rsmod.content.generic.shops

internal object ShopkeeperStalls {
    val inventories get() = ShopkeeperAccessData.stalls

    fun refusalCycles(inventory: String): Int =
        ShopkeeperAccessData.byKey[inventory]?.takeIf { it.stalls.isNotEmpty() }?.refusalCycles ?: 1_000
}
