package org.rsmod.content.other.consumables

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.content.other.consumables.food.FoodSpecialState
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.content.other.consumables.potion.moons.MoonlightPotionEffect
import org.rsmod.content.other.consumables.potion.toa.ToaPotionEffect
import org.rsmod.game.entity.Player

class ConsumableDeathCleanupHook @Inject constructor(
    private val heart: HeartEffectService,
    private val moonlight: MoonlightPotionEffect,
    private val toa: ToaPotionEffect,
) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        heart.cancelMaintainedBoost(player)
        ConsumableDelayState.clear(player)
        FoodSpecialState.clear(player)
        moonlight.clear(player)
        toa.clearSessionEffects(player)
    }
}
