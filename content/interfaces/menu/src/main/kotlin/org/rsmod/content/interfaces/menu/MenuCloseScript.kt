package org.rsmod.content.interfaces.menu

import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.script.onIfClose
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MenuCloseScript : PluginScript() {
    override fun ScriptContext.startup() {
        onIfClose("interface.menu") { player.onMenuClose() }
    }

    private fun Player.onMenuClose() {
        // TODO: Verify input-block state when closing menu before a choice resumes.
        ClientScripts.chatDefaultRestoreInput(this)
    }
}
