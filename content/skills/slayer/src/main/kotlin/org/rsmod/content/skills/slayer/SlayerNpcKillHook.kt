package org.rsmod.content.skills.slayer

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.content.skills.slayer.items.BraceletOfSlaughter
import org.rsmod.content.skills.slayer.items.ExpeditiousBracelet
import org.rsmod.content.skills.slayer.superior.SlayerSuperiorManager

class SlayerNpcKillHook @Inject constructor(
    private val npcRepo: NpcRepository,
    private val superiors: SlayerSuperiorManager,
    private val areaChecker: AreaChecker,
    private val random: GameRandom,
) : NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        if (superiors.isSuperior(context.npc)) {
            superiors.onSuperiorDeath(context.npc)
        }

        if (!SlayerTaskManager.countsKillTowardTask(context.hero, context.npc, areaChecker)) return

        val taskId = context.hero.vars["varp.slayer_target"]
        val isTaskMonster = taskId != 0 && SlayerTaskManager.countsAsTaskKill(context.npc, taskId, context.hero.vars["varbit.slayer_target_bossid"])

        // Roll superiors before completion clears the task, so the final kill remains eligible.
        if (isTaskMonster) {
            superiors.trySpawnSuperior(context.hero, context.npc, npcRepo)
        }

        // Bracelet charges require the assigned monster, not just a task-eligible area.
        val extraKills = if (isTaskMonster) ExpeditiousBracelet.rollExtraKill(context.hero, random) else 0
        val skipDecrement =
            if (isTaskMonster) BraceletOfSlaughter.rollSkipTaskDecrement(context.hero, context.npc, random) else false

        SlayerTaskManager.decreaseTask(
            context.hero,
            context.npc,
            extraCountDecrement = extraKills,
            skipCountDecrement = skipDecrement,
        )
    }
}
