package org.rsmod.content.skills.smithing

import org.rsmod.api.inv.storage.PlayerItemStorageHook
import org.rsmod.content.skills.smithing.coalbag.CoalBagStorageHook
import org.rsmod.plugin.module.PluginModule

public class SmithingModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerItemStorageHook>(CoalBagStorageHook::class.java)
    }
}
