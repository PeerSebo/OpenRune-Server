package org.rsmod.content.other.consumables.potion.overload

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

class OverloadDeathCleanupHook @Inject constructor(private val effects: OverloadEffectService) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        OverloadType.entries.forEach { effects.clear(player, it) }
    }
}
