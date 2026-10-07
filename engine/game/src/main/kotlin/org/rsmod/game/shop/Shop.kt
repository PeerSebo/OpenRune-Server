package org.rsmod.game.shop

import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

public data class Shop(
    public val inv: Inventory,
    public val currency: String,
    public val buyPercentage: Double,
    public val sellPercentage: Double,
    public val changePercentage: Double,
    public val infiniteStock: Boolean = false,
    public val allowSelling: Boolean = true,
    public val unsellableItems: Set<Int> = emptySet(),
    public val purchaseExtras: Map<Int, List<InvObj>> = emptyMap(),
    public val sellableItems: Set<Int> = emptySet(),
) {
    init {
        require(purchaseExtras.all { (item, extras) ->
            extras.all { it.id != item && it.count > 0 } && extras.map { it.id }.distinct().size == extras.size
        }) { "Purchase extras must have positive counts and distinct item types." }
    }
}
