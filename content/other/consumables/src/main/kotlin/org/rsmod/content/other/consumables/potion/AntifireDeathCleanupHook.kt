package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

class AntifireDeathCleanupHook @Inject constructor() : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        for (state in AntifireState.entries) state.clear(player)
    }
}
