package org.rsmod.content.other.consumables.heart

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.heartCooldownExpiresAt by intVarp("varp.consumables_heart_cooldown_expires_at")
internal var Player.saturatedHeartExpiresAt by intVarp("varp.consumables_saturated_heart_expires_at")
internal var Player.savedHeartCooldownTicks by intVarp("varp.consumables_heart_cooldown_remaining")
internal var Player.savedSaturatedHeartActive by boolVarp("varp.consumables_saturated_heart_active")
private var Player.heartStateMigrated by boolVarp("varp.consumables_heart_migrated")

internal object HeartState {
    private val legacyRemaining = AttributeKey<Int>("consumables.heart.cooldown_remaining")
    private val legacySaturated = AttributeKey<Boolean>("consumables.heart.saturated_active")

    fun migrate(player: Player) {
        if (!player.heartStateMigrated) {
            val remaining = player.attr[legacyRemaining]
            if (remaining != null && "varp.consumables_heart_cooldown_remaining" !in player.vars) {
                player.savedHeartCooldownTicks = remaining.coerceAtLeast(0)
            }
            val saturated = player.attr[legacySaturated]
            if (saturated != null && "varp.consumables_saturated_heart_active" !in player.vars) {
                player.savedSaturatedHeartActive = saturated
            }
            player.heartStateMigrated = true
        }
        discardLegacy(player)
    }

    fun clear(player: Player) {
        player.heartCooldownExpiresAt = 0
        player.saturatedHeartExpiresAt = 0
        player.savedHeartCooldownTicks = 0
        player.savedSaturatedHeartActive = false
        player.heartStateMigrated = true
        discardLegacy(player)
    }

    private fun discardLegacy(player: Player) {
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
        if (player.attr.has(legacySaturated)) player.attr.remove(legacySaturated)
    }
}
