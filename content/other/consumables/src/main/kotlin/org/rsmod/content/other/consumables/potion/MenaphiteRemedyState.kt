package org.rsmod.content.other.consumables.potion

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.menaphiteRemedyExpiresAt by intVarp("varp.consumables_menaphite_remedy_expires_at")
internal var Player.savedMenaphiteRemedyTicks by intVarp("varp.consumables_menaphite_remedy_remaining")
private var Player.menaphiteRemedyStateMigrated by boolVarp("varp.consumables_menaphite_remedy_migrated")

internal object MenaphiteRemedyState {
    private val legacyRemaining = AttributeKey<Int>("consumables.potion.menaphite_remedy_remaining")

    fun migrate(player: Player) {
        if (!player.menaphiteRemedyStateMigrated) {
            val remaining = player.attr[legacyRemaining]
            if (remaining != null && "varp.consumables_menaphite_remedy_remaining" !in player.vars) {
                player.savedMenaphiteRemedyTicks = remaining.coerceAtLeast(0)
            }
            player.menaphiteRemedyStateMigrated = true
        }
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
    }

    fun clear(player: Player) {
        player.menaphiteRemedyExpiresAt = 0
        player.savedMenaphiteRemedyTicks = 0
        player.menaphiteRemedyStateMigrated = true
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
        player.clearTimer(MenaphiteRemedyEffectService.TIMER)
    }
}
