package org.rsmod.content.skills.smithing

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object SmithingState {
    private var Player.selectedQuantity by intVarp("varp.smithing_quantity")
    private var Player.skipConfirmation by boolVarp("varp.crystal_singing_skip_confirmation")
    private var Player.migrated by boolVarp("varp.crystal_singing_confirmation_migrated")

    fun quantity(player: Player): Int = player.selectedQuantity.takeUnless { it == 0 } ?: 1

    fun setQuantity(player: Player, quantity: Int) {
        player.selectedQuantity = quantity
    }

    fun clearQuantity(player: Player) {
        player.selectedQuantity = 0
    }

    fun skipCrystalConfirmation(player: Player): Boolean {
        migrate(player)
        return player.skipConfirmation
    }

    fun setSkipCrystalConfirmation(player: Player, skip: Boolean) {
        player.skipConfirmation = skip
        player.migrated = true
        player.attr.remove(legacyKey())
    }

    fun migrate(player: Player) {
        val legacy = legacyKey()
        if (!player.migrated) {
            if ("varp.crystal_singing_skip_confirmation" !in player.vars) {
                player.attr[legacy]?.let { player.skipConfirmation = it }
            }
            player.migrated = true
        }
        player.attr.remove(legacy)
    }

    private fun legacyKey() =
        AttributeKey<Boolean>(persistenceKey = "crystal_singing_dont_ask_again")
}

class SmithingStateScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            SmithingState.clearQuantity(player)
            SmithingState.migrate(player)
        }
        onPlayerLogout { SmithingState.clearQuantity(player) }
    }
}
