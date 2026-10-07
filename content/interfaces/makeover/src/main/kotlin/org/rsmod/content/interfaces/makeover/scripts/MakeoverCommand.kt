package org.rsmod.content.interfaces.makeover.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.interfaces.makeover.MakeoverPage
import org.rsmod.content.interfaces.makeover.openMakeover
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MakeoverCommand @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("makeover") {
            desc = "Change your body type, hair, colours and clothes"
            cheat {
                protectedAccess.launch(player, "You need to finish what you're doing first.") {
                    openMakeover(MakeoverPage.Body)
                }
            }
        }
    }
}
