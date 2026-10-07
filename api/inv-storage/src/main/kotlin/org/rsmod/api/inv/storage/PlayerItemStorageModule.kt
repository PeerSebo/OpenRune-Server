package org.rsmod.api.inv.storage

import org.rsmod.plugin.module.PluginModule

public class PlayerItemStorageModule : PluginModule() {
    override fun bind() {
        newSetBinding<PlayerItemStorageHook>()
    }
}
