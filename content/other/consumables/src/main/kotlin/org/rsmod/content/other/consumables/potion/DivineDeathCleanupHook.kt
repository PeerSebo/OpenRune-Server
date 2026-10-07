package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

class DivineDeathCleanupHook @Inject constructor(private val effects: DivineEffectService) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        effects.onDeath(player)
    }
}
