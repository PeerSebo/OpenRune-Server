package org.rsmod.content.other.consumables

import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

object ConsumableDelayState {
    private const val FOOD_UNTIL = "varp.consumables_food_delay_until"
    private const val POTION_UNTIL = "varp.consumables_potion_delay_until"
    private const val COMBO_FOOD_UNTIL = "varp.consumables_combo_food_delay_until"
    private const val COMBAT_UNTIL = "varp.consumables_combat_delay_until"

    fun canConsume(access: ProtectedAccess, type: ConsumableType): Boolean = with(access) {
        when (type) {
            ConsumableType.FOOD ->
                !isActive(FOOD_UNTIL) && !isActive(POTION_UNTIL) && !isActive(COMBO_FOOD_UNTIL)
            ConsumableType.POTION -> !isActive(POTION_UNTIL) && !isActive(COMBO_FOOD_UNTIL)
            ConsumableType.COMBO_FOOD -> !isActive(COMBO_FOOD_UNTIL)
        }
    }

    fun recordConsumption(
        access: ProtectedAccess,
        type: ConsumableType,
        consumeDelay: Int,
        combatDelay: Int,
    ) {
        with(access) {
            if (consumeDelay > 0) {
                val key = key(type)
                val expiresAt = mapClock + consumeDelay
                if (expiresAt > player.vars[key]) {
                    VarPlayerIntMapSetter.set(player, key, expiresAt)
                }
            }
            if (combatDelay > 0) {
                val expiresAt = maxOf(player.vars[COMBAT_UNTIL], mapClock) + combatDelay
                VarPlayerIntMapSetter.set(player, COMBAT_UNTIL, expiresAt)
                actionDelay = maxOf(actionDelay, expiresAt)
                Stun.extendActionLock(player, combatDelay)
            }
        }
    }

    internal fun clear(player: Player) {
        for (key in listOf(FOOD_UNTIL, POTION_UNTIL, COMBO_FOOD_UNTIL, COMBAT_UNTIL)) {
            VarPlayerIntMapSetter.set(player, key, 0)
        }
    }

    private fun ProtectedAccess.isActive(key: String): Boolean = player.vars[key] > mapClock

    private fun key(type: ConsumableType): String = when (type) {
        ConsumableType.FOOD -> FOOD_UNTIL
        ConsumableType.POTION -> POTION_UNTIL
        ConsumableType.COMBO_FOOD -> COMBO_FOOD_UNTIL
    }
}

enum class ConsumableType {
    FOOD,
    POTION,
    COMBO_FOOD,
}
