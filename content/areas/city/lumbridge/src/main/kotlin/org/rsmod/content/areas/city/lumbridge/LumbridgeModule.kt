package org.rsmod.content.areas.city.lumbridge

import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.content.areas.city.lumbridge.npcs.AideTutorClaimSavePreparationHook
import org.rsmod.plugin.module.PluginModule

class LumbridgeModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerSavePreparationHook>(AideTutorClaimSavePreparationHook::class.java)
    }
}
