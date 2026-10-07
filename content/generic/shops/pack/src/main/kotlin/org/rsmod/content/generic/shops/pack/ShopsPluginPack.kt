package org.rsmod.content.generic.shops.pack

import dev.openrune.pack.PluginPack

class ShopsPluginPack : PluginPack() {
    override fun dbTables() = listOf(
        ShopkeeperStockTable.build(),
        ShopkeeperBindingsTable.build(),
        RewardShopsTable.build(),
        RewardPacksTables.packs(),
        RewardPacksTables.gems(),
        ShopkeeperAccessTable.build(),
    )
}
