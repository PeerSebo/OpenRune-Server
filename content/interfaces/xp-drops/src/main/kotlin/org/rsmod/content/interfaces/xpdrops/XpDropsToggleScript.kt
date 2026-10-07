package org.rsmod.content.interfaces.xpdrops

import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class XpDropsToggleScript
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton("component.orbs:xp_drops") { orbClick(it.op) }
        onPlayerLogin { onLogin(player) }
    }

    private fun ProtectedAccess.orbClick(op: IfButtonOp) {
        when (op) {
            IfButtonOp.Op1 -> toggleXpDrops()
            IfButtonOp.Op2 -> openXpDropsSetup()
            else -> Unit
        }
    }

    private fun ProtectedAccess.toggleXpDrops() {
        val enabled = !player.xpDropsEnabled
        player.xpDropsEnabled = enabled
        if (enabled) {
            ifOpenOverlay("interface.xp_drops", "component.toplevel_osrs_stretch:xp_drops")
        } else {
            ifCloseSub("interface.xp_drops")
        }
    }

    @OptIn(InternalApi::class)
    private fun onLogin(player: Player) {
        if (!player.xpDropsEnabled) {
            return
        }
        protectedAccess.launchLenient(player) {
            ifOpenOverlay("interface.xp_drops", "component.toplevel_osrs_stretch:xp_drops")
        }
    }
}

internal var Player.xpDropsEnabled by boolVarBit("varbit.xpdrops_enabled")
