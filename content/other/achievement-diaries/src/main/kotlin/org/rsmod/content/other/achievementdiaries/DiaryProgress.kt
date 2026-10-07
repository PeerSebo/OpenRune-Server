package org.rsmod.content.other.achievementdiaries

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal object DiaryProgress {
    const val OakStage = "varp.diary_lumbridge_oak_stage"
    const val AmuletStage = "varp.diary_lumbridge_amulet_stage"

    fun isComplete(player: Player, task: DiaryTask): Boolean =
        task.bit >= 0 && player.vars[task.progressVarp] and (1 shl task.bit) != 0

    fun completedCount(player: Player, tier: DiaryTier = DiaryData.easy): Int =
        DiaryData.tasks(tier).count { isComplete(player, it) }

    fun synchronize(player: Player) {
        for (tier in DiaryData.tiers) {
            val count = completedCount(player, tier)
            VarPlayerIntMapSetter.set(player, tier.countVar, count)
            VarPlayerIntMapSetter.set(player, tier.completeVar, if (count == taskCount(tier)) 1 else 0)
            if (count > 0) VarPlayerIntMapSetter.set(player, tier.startedVar, 1)
        }
    }

    val taskCount: Int get() = taskCount(DiaryData.easy)
    fun taskCount(tier: DiaryTier): Int = DiaryData.tasks(tier).count { it.bit >= 0 }

    fun resetStagesOutsideArea(player: Player, inArea: (String, CoordGrid) -> Boolean) {
        for (task in DiaryData.tasks.filter { it.bit >= 0 && it.stageVarp.isNotEmpty() }) {
            if (player.vars[task.stageVarp] > 0 &&
                !task.matches(task.action, task.targets.first(), player.coords, inArea)) {
                VarPlayerIntMapSetter.set(player, task.stageVarp, 0)
            }
        }
    }

    fun record(
        player: Player,
        action: String,
        target: String,
        coords: CoordGrid,
        inArea: (String, CoordGrid) -> Boolean,
        count: Int = 1,
    ) {
        for (task in DiaryData.tasks) {
            if (task.unavailable.isNotEmpty() || count < task.minimumCount) continue
            if (isComplete(player, task) || !task.matches(action, target, coords, inArea)) continue
            val stage = if (task.stageVarp.isEmpty()) 0 else player.vars[task.stageVarp]
            if (task.requiredStage > stage) continue
            if (task.setStage > 0) {
                val finalTask = DiaryData.tasks.single {
                    it.stageVarp == task.stageVarp && it.bit >= 0
                }
                if (isComplete(player, finalTask) || stage >= task.setStage) {
                    continue
                }
                VarPlayerIntMapSetter.set(player, task.stageVarp, task.setStage)
                player.mes("Achievement Diary Stage Task - Current stage: ${task.setStage}.")
                continue
            }
            val tier = DiaryData.forDifficulty(task.difficulty)
            val completedTasks = player.vars[task.progressVarp] or (1 shl task.bit)
            VarPlayerIntMapSetter.set(player, task.progressVarp, completedTasks)
            if (task.requiredStage > 0) VarPlayerIntMapSetter.set(player, task.stageVarp, 0)
            synchronize(player)
            player.mes(
                "<col=ff0000>You have completed ${if (tier.difficulty == 1) "an" else "a"} ${tier.label} task in the ${tier.name} area.</col>"
            )
            if (completedCount(player, tier) == taskCount(tier)) {
                player.mes(
                    "You have completed all the ${tier.label} tasks. " +
                        "Speak to Hatius Cosaintus for your reward."
                )
            }
        }
    }
}
