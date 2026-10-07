package org.rsmod.content.skills.slayer.core

import org.rsmod.api.config.refs.SlayerBossTargets
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.table.slayer.*
import org.rsmod.game.entity.Player

object SlayerBossTasks {
    const val MIN_KILL_COUNT = 3
    const val DEFAULT_MAX_KILL_COUNT = 35
    const val BARROWS_MAX_KILL_COUNT = 36
    const val BOSS_COMPLETION_BONUS_XP = 5_000.0
    private const val BOSS_REPLACE_DENOMINATOR = 16
    private val entriesByBossId = SlayerTaskSublistRow.all().filter { it.subtableId == 1 }.associateBy { it.taskSubtableId }

    data class Selection(val masterTask: SlayerMasterTaskRow, val entry: SlayerTaskSublistRow) {
        val task get() = entry.task
        val bossId get() = entry.taskSubtableId
    }
    fun hasLikeABoss(access: ProtectedAccess): Boolean {
        val rewardBit = SlayerUnlockRow.all().firstOrNull { it.name.equals("Like a Boss", ignoreCase = true) }?.bit ?: return false
        return SlayerTaskManager.hasUnlockedReward(access, rewardBit)
    }
    fun isBossTask(taskId: Int) = taskId == SlayerBossTargets.TaskId
    fun task(bossId: Int): SlayerTaskRow? = entriesByBossId[bossId]?.task
    fun current(player: Player): SlayerTaskRow? = task(player.vars["varbit.slayer_target_bossid"])
    fun isExcludedBoss(task: SlayerTaskRow): Boolean = listOf("corporeal", "yama", "nightmare", "nex").any { task.nameLowercase.contains(it, ignoreCase = true) }

    fun eligibleBossTasks(access: ProtectedAccess, master: SlayerMastersRow): List<Selection> = eligibleBossTasks(access.player, master)
    fun eligibleBossTasks(player: Player, master: SlayerMastersRow): List<Selection> {
        val parent = SlayerTaskManager.tasks[master].orEmpty().firstOrNull { isBossTask(it.task.id) } ?: return emptyList()
        if (!supportsBossAssignment(master)) return emptyList()
        // Each selectable boss must have a kill matcher; otherwise its assignment is impossible.
        return entriesByBossId.values.filter {
            it.taskSubtableId in SlayerBossTargets.supportedBossIds &&
                !isExcludedBoss(it.task) && meetsBossRequirements(player, it.task) &&
                !SlayerTaskManager.isWildernessMaster(master)
        }.map { Selection(parent, it) }
    }
    fun rollBossTask(access: ProtectedAccess, master: SlayerMastersRow): Selection? =
        access.random.pickOrNull(eligibleBossTasks(access, master))
    fun rollBossReplacement(access: ProtectedAccess, master: SlayerMastersRow): Selection? {
        if (!master.assignBosses || !hasLikeABoss(access) || SlayerTaskManager.isWildernessMaster(master) ||
            !access.random.randomBoolean(BOSS_REPLACE_DENOMINATOR)) return null
        return rollBossTask(access, master)
    }
    fun supportsBossAssignment(master: SlayerMastersRow): Boolean = master.assignBosses || SlayerTaskManager.isWildernessMaster(master)
    fun rollVariant(taskId: Int): Int = taskId
    fun maxKillCount(task: SlayerTaskRow): Int = if ("barrows" in task.nameLowercase.lowercase()) BARROWS_MAX_KILL_COUNT else DEFAULT_MAX_KILL_COUNT
    fun meetsBossRequirements(player: Player, task: SlayerTaskRow): Boolean {
        if (task.minComlevel?.let { player.combatLevel < it } == true) return false
        if (!task.minStatRequirementAll.all { (level, stat) -> player.statBase(stat.internalName) >= level }) return false
        return task.minStatRequirementAny.isEmpty() || task.minStatRequirementAny.any { (level, stat) -> player.statBase(stat.internalName) >= level }
    }
}
