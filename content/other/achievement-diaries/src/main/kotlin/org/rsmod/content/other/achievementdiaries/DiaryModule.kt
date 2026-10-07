package org.rsmod.content.other.achievementdiaries

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.plugin.module.PluginModule

class DiaryModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(DiaryNpcKillHook::class.java)
    }
}

class DiaryNpcKillHook @Inject constructor(private val areas: AreaChecker) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        DiaryProgress.record(
            context.hero,
            "NpcKill",
            RSCM.getReverseMapping(RSCMType.NPC, context.npc.type.id),
            context.npc.coords,
            areas::inArea,
        )
    }
}
