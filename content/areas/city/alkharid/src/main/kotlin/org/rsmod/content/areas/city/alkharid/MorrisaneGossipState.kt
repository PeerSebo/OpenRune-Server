package org.rsmod.content.areas.city.alkharid

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

internal object MorrisaneGossipState {
    private var Player.savedHeardGossip by boolVarp("varp.alkharid_heard_morrisane_gossip")
    private var Player.migrated by boolVarp("varp.alkharid_morrisane_gossip_migrated")

    fun heardGossip(player: Player): Boolean {
        migrate(player)
        return player.savedHeardGossip
    }

    fun setHeardGossip(player: Player, heard: Boolean) {
        player.savedHeardGossip = heard
        player.migrated = true
        player.attr.remove(legacyKey())
    }

    fun migrate(player: Player) {
        val legacy = legacyKey()
        if (!player.migrated) {
            if ("varp.alkharid_heard_morrisane_gossip" !in player.vars) {
                player.attr[legacy]?.let { player.savedHeardGossip = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacy)
    }

    private fun legacyKey() =
        AttributeKey<Boolean>(persistenceKey = "alkharid_heard_morrisane_gossip")
}
