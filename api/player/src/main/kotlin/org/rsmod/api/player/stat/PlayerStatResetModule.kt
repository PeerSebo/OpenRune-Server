package org.rsmod.api.player.stat

import org.rsmod.plugin.module.PluginModule

public class PlayerStatResetModule : PluginModule() {
    override fun bind() {
        newSetBinding<PlayerStatResetHook>()
    }
}
