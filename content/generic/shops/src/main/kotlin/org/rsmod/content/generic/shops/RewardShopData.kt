package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvStock
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.InvIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.api.shops.ShopStockDefinition
import org.rsmod.api.shops.currency.ShopCurrencyRows

internal data class RewardShopItem(val item: String, val price: Int, val count: Int)

internal data class RewardShopSpec(
    val key: String,
    val title: String,
    val currency: String,
    val allowSelling: Boolean,
    val displayTemplate: String,
    val sellMultiplier: Int,
    val buyMultiplier: Int,
    val delta: Int,
    val restockCycles: Int,
    val items: List<RewardShopItem>,
) {
    fun createStock() = ShopStockDefinition(
        name = title,
        displayTemplate = displayTemplate,
        stock = items.map { InvStock(it.item.asRSCM(), it.count, restockCycles) },
        sellMultiplier = sellMultiplier,
        buyMultiplier = buyMultiplier,
        delta = delta,
    )
}

internal object RewardShopData {
    val shops: List<RewardShopSpec> by lazy {
        val currencies = ShopCurrencyRows.all().map { it.key }.toSet()
        orderedShopRows("reward_merchant_shops").map { row ->
            val prefix = "dbcol.reward_merchant_shops:"
            val items = row.multiColumn(prefix + "items", ObjIdCodec)
            val prices = row.multiColumn(prefix + "prices", IntCodec)
            val counts = row.multiColumn(prefix + "counts", IntCodec)
            require(items.isNotEmpty() && items.size == prices.size && items.size == counts.size)
            require(items.distinct().size == items.size && prices.all { it > 0 } && counts.all { it > 0 })
            val currency = row.column(prefix + "currency", StringCodec)
            require(currency in currencies) { "Unknown reward currency $currency in row ${row.id}." }
            currency.asRSCM(RSCMType.CURRENCY)
            val inventory = row.column(prefix + "display_template", InvIdCodec)
            requireNotNull(ServerCacheManager.getInventory(inventory))
            RewardShopSpec(
                key = row.column(prefix + "key", StringCodec),
                title = row.column(prefix + "title", StringCodec),
                currency = currency,
                allowSelling = row.column(prefix + "allow_selling", BooleanCodec),
                displayTemplate = RSCM.getReverseMapping(RSCMType.INV, inventory),
                sellMultiplier = row.column(prefix + "sell_multiplier", IntCodec),
                buyMultiplier = row.column(prefix + "buy_multiplier", IntCodec),
                delta = row.column(prefix + "delta", IntCodec),
                restockCycles = row.column(prefix + "restock_cycles", IntCodec),
                items = items.mapIndexed { index, item ->
                    requireNotNull(ServerCacheManager.getItem(item))
                    RewardShopItem(RSCM.getReverseMapping(RSCMType.OBJ, item), prices[index], counts[index])
                },
            ).also {
                require(it.key.isNotBlank() && it.title.isNotBlank())
                require(it.sellMultiplier > 0 && it.buyMultiplier >= 0 && it.delta >= 0 && it.restockCycles > 0)
                require(it.allowSelling || it.buyMultiplier == 0)
            }
        }.also { shops ->
            require(shops.distinctBy { it.key }.size == shops.size)
            for (variants in shops.groupBy { it.currency }.values) {
                for ((item, entries) in variants.flatMap { it.items }.groupBy { it.item }) {
                    require(entries.map { it.price }.distinct().size == 1) {
                        "Conflicting reward prices for $item."
                    }
                }
            }
        }
    }

    val byKey by lazy { shops.associateBy { it.key } }
}
