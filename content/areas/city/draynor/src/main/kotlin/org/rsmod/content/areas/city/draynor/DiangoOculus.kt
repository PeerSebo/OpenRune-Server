package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onDialogInput
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpHeld1
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoOculus @Inject constructor(private val events: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.oculus") {
            mes("The scrying orb is temporarily unavailable.")
        }
        onDialogInput {
            if (player.ui.containsModal(Interface)) {
                when (count) {
                    0 -> player.ifCloseSub(Interface, events)
                    1 -> {
                        player.resetCounter = (player.resetCounter + 1) and 0xffff
                        Camera.syncOculus(player, player.resetCounter)
                    }
                }
            }
        }
        onIfClose(Interface) {
            Camera.freeCamera(player, false)
            player.runClientScript("clientscript.[clientscript,set_renderself]", 1)
            player.anim("seq.qip_watchtower_stop_reading_scroll")
            player.resetCounter = 0
        }
    }

    private companion object {
        const val Interface = "interface.oculus"
        var Player.resetCounter by intVarp("varp.diango_oculus_reset")
    }
}
