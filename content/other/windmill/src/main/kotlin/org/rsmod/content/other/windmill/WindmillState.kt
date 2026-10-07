package org.rsmod.content.other.windmill

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object WindmillState {
    private var Player.savedHopperGrain by boolVarp("varp.windmill_hopper_has_grain")
    private var Player.migrated by boolVarp("varp.windmill_hopper_grain_migrated")

    fun hasGrain(player: Player): Boolean {
        migrate(player)
        return player.savedHopperGrain
    }

    fun setHasGrain(player: Player, hasGrain: Boolean) {
        player.savedHopperGrain = hasGrain
        player.migrated = true
        player.attr.remove(legacyKey())
    }

    fun migrate(player: Player) {
        val legacy = legacyKey()
        if (!player.migrated) {
            if ("varp.windmill_hopper_has_grain" !in player.vars) {
                player.attr[legacy]?.let { player.savedHopperGrain = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacy)
    }

    private fun legacyKey() = AttributeKey<Boolean>(persistenceKey = "windmill_hopper_has_grain")
}
