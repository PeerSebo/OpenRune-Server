package org.rsmod.content.other.ironman

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object IronmanTutorState {
    private var Player.savedValue by boolVarp("varp.ironman_tutor_adam_met")
    private var Player.migrated by boolVarp("varp.ironman_tutor_adam_met_migrated")
    private val legacyValue = AttributeKey<Boolean>("ironman_tutor_adam_met")

    fun get(player: Player): Boolean {
        migrate(player)
        return player.savedValue
    }

    fun set(player: Player, value: Boolean) {
        player.savedValue = value
        player.migrated = true
        player.attr.remove(legacyValue)
    }

    fun migrate(player: Player) {
        if (!player.migrated) {
            if ("varp.ironman_tutor_adam_met" !in player.vars) {
                player.attr[legacyValue]?.let { player.savedValue = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacyValue)
    }
}
