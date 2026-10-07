package org.rsmod.content.other.consumables.potion.cox

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

class CoxPrayerEnhanceDeathCleanupHook @Inject constructor(
    private val effect: CoxPrayerEnhanceEffect,
) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        effect.clear(player)
    }
}
