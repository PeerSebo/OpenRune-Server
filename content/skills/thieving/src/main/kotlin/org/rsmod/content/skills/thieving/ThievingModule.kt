package org.rsmod.content.skills.thieving

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.content.skills.thieving.scripts.BlackjackStateScript
import org.rsmod.plugin.module.PluginModule

class ThievingModule : PluginModule() {
    override fun bind() {
        addSetBinding<InvisibleLevelMod>(ThievingLevelBoosts::class.java)
        addSetBinding<PlayerDeathCleanupHook>(BlackjackStateScript::class.java)
    }
}
