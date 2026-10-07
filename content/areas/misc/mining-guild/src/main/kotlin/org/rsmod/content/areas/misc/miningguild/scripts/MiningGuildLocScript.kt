package org.rsmod.content.areas.misc.miningguild.scripts

import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MiningGuildLocScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.castlewars_bankchest") { tryOpenBank() }
    }
}
