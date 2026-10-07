package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.aideTutorClaimReadyAt by intVarp("varp.lumbridge_tutor_claim_ready_at")
internal var Player.aideTutorClaimRemainingTicks by intVarp("varp.lumbridge_tutor_claim_remaining_ticks")
private var Player.aideTutorClaimMigrated by boolVarp("varp.lumbridge_tutor_claim_migrated")

internal const val AideTutorClaimCooldownTicks: Int = 3000

internal object AideTutorClaimState {
    private val legacyRemaining = AttributeKey<Int>("aide_tutor_claim_remaining_ticks")

    fun migrate(player: Player) {
        if (!player.aideTutorClaimMigrated) {
            if ("varp.lumbridge_tutor_claim_remaining_ticks" !in player.vars) {
                player.attr[legacyRemaining]?.let {
                    player.aideTutorClaimRemainingTicks = it.coerceAtLeast(0)
                }
            }
            player.aideTutorClaimMigrated = true
        }
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
    }

    fun claimed(player: Player, clock: Int) {
        migrate(player)
        player.aideTutorClaimReadyAt = clock + AideTutorClaimCooldownTicks
        player.aideTutorClaimRemainingTicks = AideTutorClaimCooldownTicks
    }

    fun onLogin(player: Player, clock: Int) {
        migrate(player)
        val remaining = player.aideTutorClaimRemainingTicks.coerceAtLeast(0)
        player.aideTutorClaimRemainingTicks = remaining
        player.aideTutorClaimReadyAt = if (remaining > 0) clock + remaining else 0
    }

    fun snapshot(player: Player) {
        migrate(player)
        if ("varp.lumbridge_tutor_claim_ready_at" !in player.vars) return
        player.aideTutorClaimRemainingTicks = remaining(player, player.currentMapClock)
    }

    fun onLogout(player: Player) {
        migrate(player)
        if (player.aideTutorClaimReadyAt == 0) return
        snapshot(player)
        player.aideTutorClaimReadyAt = 0
    }

    fun remaining(player: Player, clock: Int): Int =
        if (player.aideTutorClaimReadyAt == 0) 0
        else (player.aideTutorClaimReadyAt - clock).coerceAtLeast(0)
}
