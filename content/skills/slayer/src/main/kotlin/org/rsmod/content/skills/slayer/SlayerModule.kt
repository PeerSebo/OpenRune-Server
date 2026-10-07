package org.rsmod.content.skills.slayer

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.content.skills.slayer.SlayerNpcKillHook
import org.rsmod.content.skills.slayer.superior.SlayerSuperiorAttackHook
import org.rsmod.content.skills.slayer.superior.SlayerSuperiorEvents
import org.rsmod.plugin.module.PluginModule

class SlayerModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(SlayerNpcKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(SlayerSuperiorAttackHook::class.java)
        addSetBinding<PlayerPostTickHook>(SlayerSuperiorEvents::class.java)
    }
}
