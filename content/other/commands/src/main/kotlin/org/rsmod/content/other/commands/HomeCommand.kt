package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HomeCommand @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("home") {
            desc = "Teleport to Lumbridge while idle and out of combat"
            cheat {
                if (args.isNotEmpty()) {
                    player.mes("Usage: ::home")
                    return@cheat
                }
                // Check before launching: refusing a command must not cancel the current skill.
                val denial = player.homeCommandDenial()
                if (denial != null) {
                    player.mes(denial)
                    return@cheat
                }
                protectedAccess.launch(player) {
                    telejump(Lumbridge, TeleportType.Standard)
                }
            }
        }
    }

    private fun Player.homeCommandDenial(): String? {
        val lastCombat = maxOf(vars["varp.lastcombat"], vars["varp.lastcombat_pvp"])
        if ((lastCombat > 0 && currentMapClock - lastCombat < CombatQuietTicks) ||
            preventLogoutUntil >= currentMapClock) {
            return "You can't use ::home until 10 seconds after the end of combat."
        }
        if (isAccessProtected || isBusy2 || routeRequest != null || forcedRoute ||
            pendingExactMove != null || pendingTeleport || pendingTelejump || hasMovedThisCycle ||
            weakQueueList.isNotEmpty || queueList.isNotEmpty || pendingShutdown || loggingOut) {
            return "You need to finish what you're doing before using ::home."
        }
        return null
    }

    private companion object {
        val Lumbridge = CoordGrid(3222, 3218, 0)
        const val CombatQuietTicks = 17
    }
}
