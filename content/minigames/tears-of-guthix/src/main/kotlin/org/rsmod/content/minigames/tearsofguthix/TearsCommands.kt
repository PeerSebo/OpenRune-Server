package org.rsmod.content.minigames.tearsofguthix

import dev.or2.central.account.Rights
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TearsCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("togreset") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset your Tears of Guthix weekly and XP eligibility; ::togreset"
            cheat {
                when {
                    args.isNotEmpty() -> player.mes("Usage: ::togreset")
                    TearsState.active(player) -> player.mes("Finish your current Tears of Guthix visit first.")
                    else -> {
                        TearsState.resetEligibility(player)
                        player.mes("Your Tears of Guthix wait and XP requirements have been reset.")
                    }
                }
            }
        }
    }
}
