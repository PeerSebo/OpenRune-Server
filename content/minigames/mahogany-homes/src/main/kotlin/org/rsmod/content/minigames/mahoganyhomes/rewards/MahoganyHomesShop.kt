package org.rsmod.content.minigames.mahoganyhomes.rewards

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesData
import org.rsmod.game.entity.Player

// TODO: use native construction_contract_shop (673); current rewards use the generic shop UI.
public object MahoganyHomesShop {
    private const val SHOP_INV = "inv.mahogany_homes_shop"
    private const val TITLE = "Mahogany Homes Rewards"

    public fun open(shops: Shops, player: Player) {
        shops.open(
            player = player,
            title = TITLE,
            shopInv = SHOP_INV,
            buyPercentage = 100.0,
            sellPercentage = 0.0,
            changePercentage = 0.0,
            currency = CarpenterPoints.CURRENCY,
        )
    }

    public fun registerCostOverride(shopOps: ShopOperationMap) {
        MahoganyHomesData.rewardPrices
        shopOps.costOf(CarpenterPoints.CURRENCY, ::costOf)
    }

    private fun costOf(type: ItemServerType): Int {
        val name = RSCM.getReverseMapping(RSCMType.OBJ, type.id)
        return MahoganyHomesData.rewardPrices[name] ?: type.cost.coerceAtLeast(1)
    }
}
