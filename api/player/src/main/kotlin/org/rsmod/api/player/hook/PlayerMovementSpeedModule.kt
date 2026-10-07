package org.rsmod.api.player.hook

import org.rsmod.plugin.module.PluginModule

public class PlayerMovementSpeedModule : PluginModule() {
    override fun bind() {
        newSetBinding<PlayerMovementSpeedHook>()
    }
}
