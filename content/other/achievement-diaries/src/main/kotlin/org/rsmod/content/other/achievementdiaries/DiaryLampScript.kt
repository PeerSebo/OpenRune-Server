package org.rsmod.content.other.achievementdiaries

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.manager.XpRewardLamp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiaryLampScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in DiaryData.tiers) onOpHeld1(tier.lamp) { rubLamp(tier) }
    }

    private suspend fun ProtectedAccess.rubLamp(tier: DiaryTier) {
        if (player.vars[tier.rewardVar] == 0) return
        val stat = XpRewardLamp.choose(this) ?: return
        val skillName = XpRewardLamp.displayName(stat)
        if (player.statBase(stat) < tier.lampMinLevel) {
            mes("You need a $skillName level of ${tier.lampMinLevel} to use this lamp.")
            return
        }
        if (invDel(inv, tier.lamp, 1).failure) return
        val granted = statAdvance(stat, tier.lampXp.toDouble(), globalRate = 1.0)
        soundSynth("synth.FOUND_GEM")
        mesbox(
            "<col=0000ff>Your wish has been granted!</col> " +
                "You have been awarded $granted $skillName experience!"
        )
    }
}
