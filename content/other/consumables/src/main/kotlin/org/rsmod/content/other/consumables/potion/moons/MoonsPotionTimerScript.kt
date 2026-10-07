package org.rsmod.content.other.consumables.potion.moons

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MoonsPotionTimerScript @Inject constructor(
    private val moonlight: MoonlightPotionEffect,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { moonlight.clear(player) }
        onPlayerLogout { moonlight.clear(player) }
        onPlayerTimer(MoonlightPotionEffect.TIMER) { moonlight.process(this) }
    }
}
