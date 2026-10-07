package org.rsmod.content.other.consumables.food

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

private var Player.hunterHealDueAt by intVarp("varp.consumables_hunter_heal_due_at")
private var Player.hunterHealAmount by intVarp("varp.consumables_hunter_heal_amount")
private var Player.hunterHealEffect by intVarp("varp.consumables_hunter_heal_effect")

internal object FoodSpecialState {
    fun pending(player: Player): PendingFoodHeal? {
        val dueAt = player.hunterHealDueAt
        if (dueAt == 0) return null
        return PendingFoodHeal(
            dueAt = dueAt,
            amount = player.hunterHealAmount,
            effect = DelayedFoodEffect.entries.single { it.id == player.hunterHealEffect },
        )
    }

    fun schedule(player: Player, pending: PendingFoodHeal) {
        player.hunterHealDueAt = pending.dueAt
        player.hunterHealAmount = pending.amount
        player.hunterHealEffect = pending.effect.id
    }

    fun clear(player: Player) {
        player.hunterHealDueAt = 0
        player.hunterHealAmount = 0
        player.hunterHealEffect = DelayedFoodEffect.NONE.id
        player.clearTimer(FoodSpecialEffectService.DELAYED_HEAL_TIMER)
    }
}

internal data class PendingFoodHeal(
    val dueAt: Int,
    val amount: Int,
    val effect: DelayedFoodEffect,
)

internal enum class DelayedFoodEffect(val id: Int) {
    NONE(0),
    RESTORE_RUN_ENERGY(1),
    CURE_POISON(2),
}
