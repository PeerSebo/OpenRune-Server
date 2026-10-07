package org.rsmod.content.other.login

import jakarta.inject.Inject
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onPlayerInit
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LoginXpRatesScript
@Inject
constructor(private val realm: Realm, private val config: ServerConfig) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerInit {
            player.xpRate = config.gameplay.xpRate
            player.globalXpRate = realm.config.globalXpRate
        }
    }
}
