package org.rsmod.content.areas.city.draynor

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object ErnestFountainState {
    private var Player.savedValue by boolVarp("varp.ernest_piranhas_dead")
    private var Player.migrated by boolVarp("varp.ernest_piranhas_dead_migrated")
    private val legacyValue = AttributeKey<Boolean>("ernest_piranhas_dead")

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
            if ("varp.ernest_piranhas_dead" !in player.vars) {
                player.attr[legacyValue]?.let { player.savedValue = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacyValue)
    }
}
