package org.rsmod.api.death.plugin

import jakarta.inject.Inject
import org.rsmod.api.death.grave.GraveManager
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Use a soft timer so the countdown does not interrupt modals; decrement three units every
 * three ticks.
 */
public class GraveScript @Inject constructor(private val graves: GraveManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { graves.onLogin(player) }
        onPlayerLogout { graves.onLogout(player) }
        onPlayerSoftTimer(GraveManager.COUNTDOWN_TIMER) { graves.countdownTick(player) }
    }
}
