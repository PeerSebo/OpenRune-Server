package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal fun ProtectedAccess.aideTutorClaimOnCooldown(): Boolean =
    AideTutorClaimState.remaining(player, mapClock) > 0

internal fun ProtectedAccess.markAideTutorClaimed() {
    AideTutorClaimState.claimed(player, mapClock)
}

internal class AideTutorClaimPersistenceScript
@Inject
constructor(private val worldClock: MapClock) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            AideTutorClaimState.onLogin(player, worldClock.cycle)
        }

        onPlayerLogout {
            AideTutorClaimState.onLogout(player)
        }
    }
}
