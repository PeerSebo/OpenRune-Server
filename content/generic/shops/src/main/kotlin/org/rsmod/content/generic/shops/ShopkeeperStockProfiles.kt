package org.rsmod.content.generic.shops

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvStock
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.InvIdCodec
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import dev.openrune.types.dbcol.multiColumnOptional
import org.rsmod.api.shops.ShopStockDefinition

internal object ShopkeeperStockProfiles {
    val shops: List<ShopkeeperBinding> by lazy {
        val rows = DbHelper.table("dbtable.shopkeeper_stock_profiles")
            .sortedBy { it.column("dbcol.shopkeeper_stock_profiles:profile_order", IntCodec) }
        require(rows.isNotEmpty()) { "Shop stock profiles are missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.shopkeeper_stock_profiles:profile_order", IntCodec) } == rows.indices.toList())
        rows.map { row ->
            val items = row.multiColumn("dbcol.shopkeeper_stock_profiles:items", ObjIdCodec)
            val counts = row.multiColumn("dbcol.shopkeeper_stock_profiles:counts", IntCodec)
            val restock = row.multiColumn("dbcol.shopkeeper_stock_profiles:restock_cycles", IntCodec)
            require(items.size == counts.size && items.size == restock.size) {
                "Mismatched stock columns in shop profile row ${row.id}"
            }
            val merchants = row.multiColumn("dbcol.shopkeeper_stock_profiles:merchants", NpcIdCodec)
                .map { RSCM.getReverseMapping(RSCMType.NPC, it) }
            require(merchants.isNotEmpty()) { "Shop profile row ${row.id} has no merchant." }
            ShopkeeperBinding(
                inventory = RSCM.getReverseMapping(
                    RSCMType.INV,
                    row.column("dbcol.shopkeeper_stock_profiles:inventory", InvIdCodec),
                ),
                merchants = merchants,
                key = row.column("dbcol.shopkeeper_stock_profiles:key", StringCodec),
                stock = ShopStockDefinition(
                    name = row.column("dbcol.shopkeeper_stock_profiles:name", StringCodec),
                    displayTemplate = RSCM.getReverseMapping(
                        RSCMType.INV,
                        row.column("dbcol.shopkeeper_stock_profiles:display_template", InvIdCodec),
                    ),
                    sellMultiplier = row.column("dbcol.shopkeeper_stock_profiles:sell_multiplier", IntCodec),
                    buyMultiplier = row.column("dbcol.shopkeeper_stock_profiles:buy_multiplier", IntCodec),
                    delta = row.column("dbcol.shopkeeper_stock_profiles:delta", IntCodec),
                    allStock = row.column("dbcol.shopkeeper_stock_profiles:all_stock", BooleanCodec),
                    stock = items.mapIndexed { index, obj -> InvStock(obj, counts[index], restock[index]) },
                    unsellableItems = row.multiColumnOptional("dbcol.shopkeeper_stock_profiles:unsellable", ObjIdCodec)
                        .filterNotNull().toSet(),
                ),
            )
        }.also { profiles ->
            require(profiles.all { it.key.startsWith("profile.") })
            require(profiles.distinctBy { it.key }.size == profiles.size)
        }
    }
}
