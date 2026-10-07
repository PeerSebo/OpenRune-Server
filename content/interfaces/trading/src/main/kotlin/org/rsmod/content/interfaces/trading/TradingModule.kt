package org.rsmod.content.interfaces.trading

import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

class TradingModule : PluginModule() {
    override fun bind() {
        bindInstance<TradeManager>()
        bindBaseInstance<TradePersistence>(AtomicTradePersistence::class.java)
        addSetBinding<PlayerPostTickHook>(TradeManager::class.java)
    }
}
