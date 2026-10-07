package org.rsmod.content.generic.shops

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.StallShopAccess
import org.rsmod.game.entity.Npc

internal fun ProtectedAccess.canTrade(npc: Npc, binding: ShopkeeperBinding): Boolean {
    val stalls = ShopkeeperStalls.inventories[binding.key] ?: return true
    if (StallShopAccess.canTrade(player, npc.coords, stalls,
        ShopkeeperStalls.refusalCycles(binding.key))) return true
    mes("The shopkeeper refuses to trade with you after you stole from their stall.")
    return false
}

internal fun ProtectedAccess.openShopBinding(shops: Shops, npc: Npc, binding: ShopkeeperBinding) {
    if (!canTrade(npc, binding)) return
    val stock = binding.stock
    if (stock != null) {
        stock.open(player, shops, infiniteStock = binding.infiniteStock,
            allowSelling = binding.allowSelling)
        return
    }
    val title = binding.title
    if (title == null) {
        shops.openConfigured(player, npc, binding.inventory,
            infiniteStock = binding.infiniteStock, allowSelling = binding.allowSelling)
    } else {
        shops.open(player, npc, title, binding.inventory,
            infiniteStock = binding.infiniteStock, allowSelling = binding.allowSelling)
    }
}
