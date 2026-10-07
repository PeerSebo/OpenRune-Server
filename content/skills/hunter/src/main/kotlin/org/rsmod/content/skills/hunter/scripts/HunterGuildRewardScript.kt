package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.skills.hunter.HunterGuildRewards
import org.rsmod.content.skills.hunter.configs.HunterSackTier
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HunterGuildRewardScript @Inject constructor(private val rewards: HunterGuildRewards) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in HunterSackTier.entries) onOpHeld1(tier.obj) { rewards.open(player, tier) }
        for (obj in HunterGuildRewards.EXCHANGEABLE) onOpNpcU("npc.hg_verity", obj) { rewards.exchange(player, obj) }
    }
}
