package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

@Singleton
public class PlayerStatResetService @Inject constructor(private val hooks: Set<PlayerStatResetHook>) {
    private val allStats by lazy {
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
    }

    public fun resetAll(player: Player) {
        for (hook in hooks) hook.reset(player)
        player.statRestoreAll(allStats)
    }
}
