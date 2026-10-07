package org.rsmod.content.other.consumables.potion

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.staminaExpiresAt by intVarp("varp.consumables_stamina_expires_at")
internal var Player.savedStaminaTicks by intVarp("varp.consumables_stamina_remaining")
private var Player.staminaStateMigrated by boolVarp("varp.consumables_stamina_migrated")

internal object StaminaState {
    private val legacyRemaining = AttributeKey<Int>("consumables.potion.stamina_remaining")

    fun migrate(player: Player) {
        if (!player.staminaStateMigrated) {
            val remaining = player.attr[legacyRemaining]
            if (remaining != null && "varp.consumables_stamina_remaining" !in player.vars) {
                player.savedStaminaTicks = remaining.coerceAtLeast(0)
            }
            player.staminaStateMigrated = true
        }
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
    }

    fun clear(player: Player) {
        player.staminaExpiresAt = 0
        player.savedStaminaTicks = 0
        player.staminaStateMigrated = true
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
        VarPlayerIntMapSetter.set(player, StaminaEffectService.ACTIVE_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, StaminaEffectService.DURATION_VARBIT, 0)
        player.clearTimer(StaminaEffectService.TIMER)
    }
}
