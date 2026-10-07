package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import org.rsmod.api.player.stat.PlayerStatResetHook
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.game.entity.Player

class ConsumableStatResetHook
@Inject
constructor(
    private val prayerRegeneration: PrayerRegenerationEffectService,
    private val divine: DivineEffectService,
    private val heart: HeartEffectService,
) : PlayerStatResetHook {
    override fun reset(player: Player) {
        prayerRegeneration.clear(player)
        divine.cancelMaintenance(player)
        heart.cancelMaintainedBoost(player)
    }
}
