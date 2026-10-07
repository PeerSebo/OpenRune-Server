package org.rsmod.api.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.InvStock
import dev.openrune.types.InventoryServerType
import dev.openrune.types.util.UncheckedType
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

public class ShopStockDefinition(
    public val name: String,
    public val displayTemplate: String,
    stock: List<InvStock>,
    public val sellMultiplier: Int,
    public val buyMultiplier: Int,
    public val delta: Int,
    public val allStock: Boolean = false,
    unsellableItems: Set<Int> = emptySet(),
) {
    public val stock: List<InvStock> = stock.toList()
    public val unsellableItems: Set<Int> = unsellableItems.toSet()

    init {
        require(name.isNotBlank()) { "Shop name must not be blank." }
        require(displayTemplate.startsWith("inv.")) { "Shop display template must be an inventory symbol." }
        require(stock.isNotEmpty()) { "Shop must define its initial stock." }
        require(stock.map { it.obj }.distinct().size == stock.size) { "Shop stock must not repeat an item." }
        require(stock.all { it.obj >= 0 && it.count >= 0 && it.restockCycles >= 0 }) { "Invalid shop stock entry." }
        require(sellMultiplier >= 0 && buyMultiplier >= 0 && delta >= 0) { "Invalid shop pricing." }
    }

    // Each definition owns its stock; the native id only selects the client's inventory layout.
    public val inventory: Inventory by lazy {
        val template = checkNotNull(ServerCacheManager.getInventory(displayTemplate.asRSCM(RSCMType.INV))) {
            "Missing shop display template: $displayTemplate"
        }
        createInventory(template)
    }

    public fun open(
        player: Player,
        shops: Shops,
        sideInv: Inventory = player.inv,
        infiniteStock: Boolean = false,
        allowSelling: Boolean = true,
    ) {
        shops.open(
            player = player,
            title = name,
            shopInv = inventory,
            sideInv = sideInv,
            buyPercentage = buyMultiplier / 10.0,
            sellPercentage = sellMultiplier / 10.0,
            changePercentage = delta / 10.0,
            subtext = Shops.DEFAULT_SUBTEXT,
            infiniteStock = infiniteStock,
            allowSelling = allowSelling,
            unsellableItems = unsellableItems,
        )
    }

    @OptIn(UncheckedType::class)
    internal fun createInventory(template: InventoryServerType): Inventory {
        require(stock.size <= template.size) { "$name has ${stock.size} stock entries but $displayTemplate holds ${template.size}." }
        val type = template.copy(
            name = name,
            scope = InvScope.Shared,
            stack = InvStackType.Always,
            stock = stock,
            sellMultiplier = sellMultiplier,
            buyMultiplier = buyMultiplier,
            delta = delta,
            flags = InventoryServerType.pack(
                protect = false,
                allStock = allStock,
                restock = true,
                runWeight = false,
                dummyInv = false,
                placeholders = false,
            ),
        )
        val objects = arrayOfNulls<InvObj>(type.size)
        stock.forEachIndexed { slot, item -> objects[slot] = InvObj(item.obj, item.count) }
        return Inventory(type, objects)
    }
}
