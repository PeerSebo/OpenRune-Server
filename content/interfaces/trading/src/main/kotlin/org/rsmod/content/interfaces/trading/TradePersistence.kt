package org.rsmod.content.interfaces.trading

import jakarta.inject.Inject
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerAccountCommit

fun interface TradePersistence {
    fun save(first: Player, second: Player, audit: Map<String, Any?>): PlayerAccountCommit?
}

class AtomicTradePersistence @Inject constructor(private val saves: AccountSavingService) : TradePersistence {
    override fun save(first: Player, second: Player, audit: Map<String, Any?>): PlayerAccountCommit? =
        saves.queueAtomic(first, second, audit)
}
