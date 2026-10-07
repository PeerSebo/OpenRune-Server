package org.rsmod.content.bosses.hespori

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathItemStorageHook
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.plugin.module.PluginModule

class HesporiModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(HesporiGarden::class.java)
        addSetBinding<PlayerDeathCleanupHook>(HesporiReclaim::class.java)
        addSetBinding<PlayerDeathItemStorageHook>(HesporiReclaim::class.java)
        addSetBinding<PlayerDeathRedirectHook>(HesporiReclaim::class.java)
    }
}
