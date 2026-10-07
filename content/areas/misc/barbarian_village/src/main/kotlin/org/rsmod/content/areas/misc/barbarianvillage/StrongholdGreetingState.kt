package org.rsmod.content.areas.misc.barbarianvillage

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object StrongholdGreetingState {
    private var Player.savedValue by boolVarp("varp.sos_greeted_by_gate_of_war")
    private var Player.migrated by boolVarp("varp.sos_greeted_by_gate_of_war_migrated")
    private val legacyValue = AttributeKey<Boolean>("sos_greeted_by_gate_of_war")

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
            if ("varp.sos_greeted_by_gate_of_war" !in player.vars) {
                player.attr[legacyValue]?.let { player.savedValue = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacyValue)
    }
}
