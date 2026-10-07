package org.rsmod.content.skills.construction

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal object HouseOptionsState {
    private var Player.savedTeleportInside by intVarp("varp.poh_teleport_inside")
    private var Player.migrated by boolVarp("varp.poh_teleport_inside_migrated")

    fun teleportInside(player: Player): Int {
        migrate(player)
        return player.savedTeleportInside
    }

    fun setTeleportInside(player: Player, value: Int) {
        player.savedTeleportInside = value
        player.migrated = true
        player.attr.remove(legacyKey())
    }

    fun migrate(player: Player) {
        val legacy = legacyKey()
        if (!player.migrated) {
            if ("varp.poh_teleport_inside" !in player.vars) {
                player.attr[legacy]?.let { player.savedTeleportInside = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacy)
    }

    private fun legacyKey() = AttributeKey<Int>(persistenceKey = "poh_tele_inside")
}
