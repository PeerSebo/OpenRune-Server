package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.InvIdCodec
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.api.shops.ShopStockDefinition

internal data class ShopkeeperBinding(
    val inventory: String,
    val merchants: List<String>,
    val title: String? = null,
    val infiniteStock: Boolean = false,
    val allowSelling: Boolean = true,
    val stock: ShopStockDefinition? = null,
    val key: String = inventory,
)

internal object ShopkeeperCatalog {
    val nativeShops: List<ShopkeeperBinding> by lazy {
        orderedShopRows("shopkeeper_bindings").map { row ->
            val prefix = "dbcol.shopkeeper_bindings:"
            val inventory = row.column(prefix + "inventory", InvIdCodec)
            requireNotNull(ServerCacheManager.getInventory(inventory))
            val key = row.column(prefix + "key", StringCodec)
            require(key.asRSCM(RSCMType.INV) == inventory)
            val merchants = row.multiColumn(prefix + "merchants", NpcIdCodec)
            require(merchants.isNotEmpty())
            ShopkeeperBinding(
                inventory = key,
                merchants = merchants.map {
                    requireNotNull(ServerCacheManager.getNpc(it))
                    RSCM.getReverseMapping(RSCMType.NPC, it)
                },
                title = row.column(prefix + "title", StringCodec).takeIf { it.isNotEmpty() },
                infiniteStock = row.column(prefix + "infinite_stock", BooleanCodec),
                allowSelling = row.column(prefix + "allow_selling", BooleanCodec),
            )
        }
    }

    val shops: List<ShopkeeperBinding> by lazy { nativeShops + ShopkeeperStockProfiles.shops }
}
