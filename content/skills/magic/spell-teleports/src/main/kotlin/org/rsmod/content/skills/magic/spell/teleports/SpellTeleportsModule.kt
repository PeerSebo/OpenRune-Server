package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

class SpellTeleportsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(SpellTeleportScript::class.java)
        addSetBinding<PlayerPostTickHook>(SpellTeleportScript::class.java)
    }
}
