package org.rsmod.content.skills.farming

import org.rsmod.api.farming.FarmingPreResolveHook
import org.rsmod.plugin.module.PluginModule

class FarmingModule : PluginModule() {
    override fun bind() {
        addSetBinding<FarmingPreResolveHook>(FarmingCompanionProtectionHook::class.java)
    }
}
