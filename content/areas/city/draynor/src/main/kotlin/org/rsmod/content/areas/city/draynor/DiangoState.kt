package org.rsmod.content.areas.city.draynor

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object DiangoState {
    private var Player.savedBananaHatClaim by boolVarp("varp.diango_claimed_banana_hat")
    private var Player.migrated by boolVarp("varp.diango_banana_hat_migrated")

    fun claimedBananaHat(player: Player): Boolean {
        migrate(player)
        return player.savedBananaHatClaim
    }

    fun setClaimedBananaHat(player: Player, claimed: Boolean) {
        player.savedBananaHatClaim = claimed
        player.migrated = true
        player.attr.remove(legacyKey())
    }

    fun migrate(player: Player) {
        val legacy = legacyKey()
        if (!player.migrated) {
            if ("varp.diango_claimed_banana_hat" !in player.vars) {
                player.attr[legacy]?.let { player.savedBananaHatClaim = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacy)
    }

    private fun legacyKey() = AttributeKey<Boolean>(persistenceKey = "diango_claimed_banana_hat")
}
