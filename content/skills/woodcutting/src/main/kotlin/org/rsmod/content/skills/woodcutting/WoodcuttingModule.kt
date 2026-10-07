package org.rsmod.content.skills.woodcutting

import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.plugin.module.PluginModule

class WoodcuttingModule : PluginModule() {
    override fun bind() {
        addSetBinding<InvisibleLevelMod>(WoodcuttingLevelBoosts::class.java)
        addSetBinding<XpMod>(WoodcuttingLumberjackXpModifiers::class.java)
    }
}
