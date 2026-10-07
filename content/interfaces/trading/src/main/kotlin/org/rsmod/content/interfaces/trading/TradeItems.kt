package org.rsmod.content.interfaces.trading

import dev.openrune.ServerCacheManager

internal object TradeItems {
    fun allowed(id: Int): Boolean {
        val item = ServerCacheManager.getItem(id) ?: return false
        if (!item.tradeable || item.isPlaceholder || item.isDummyItem) return false
        if (item.stockmarket) return true
        if (item.isCert && ServerCacheManager.getItem(item.certlink)?.stockmarket == true) return true
        return item.internalName in NON_MARKET_EXCEPTIONS
    }

    private val NON_MARKET_EXCEPTIONS = setOf(
        "obj.coins", "obj.platinum", "obj.arravcertificate_lft", "obj.arravcertificate_rht",
    )
}
