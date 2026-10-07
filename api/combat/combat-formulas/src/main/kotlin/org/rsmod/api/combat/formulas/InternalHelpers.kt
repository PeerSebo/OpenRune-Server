package org.rsmod.api.combat.formulas

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.config.refs.SlayerBossTargets
import org.rsmod.game.entity.Player

/** Fixed-point probability scale: 100% is 10,000. */
internal const val HIT_CHANCE_SCALE: Int = 10_000

internal fun scale(base: Int, multiplier: Int, divisor: Int): Int = (base * multiplier) / divisor

/**
 * Superior variants count as their base Slayer task. Unverified: whether Konar's location
 * restriction also gates the combat boost; it is not applied here.
 */
internal fun NpcServerType.isSlayerTask(player: Player): Boolean {
    val taskId = player.vars["varp.slayer_target"]
    if (taskId <= 0) return false
    if (player.vars["varp.slayer_count"] <= 0) return false
    if (taskId == SlayerBossTargets.TaskId) return SlayerBossTargets.matches(this, player.vars["varbit.slayer_target_bossid"])
    if (SlayerBossTargets.additionalCategory(this, taskId)) return true

    val category = paramOrNull(BaseParams.slayer_task_id)
    if (category != null && category > 0 && category == taskId) {
        return true
    }

    return superiorNpcIdsByTaskId[taskId]?.contains(id) == true
}

// Cache loading must finish before resolving superior variants by their base monster's Slayer task.
private val superiorNpcIdsByTaskId: Map<Int, Set<Int>> by lazy { buildSuperiorNpcIdsByTaskId() }

private fun buildSuperiorNpcIdsByTaskId(): Map<Int, Set<Int>> {
    val map = mutableMapOf<Int, MutableSet<Int>>()
    for ((_, type) in ServerCacheManager.getNpcs()) {
        val taskId = type.paramOrNull(BaseParams.slayer_task_id) ?: continue
        val superiorId = type.paramOrNull<NpcServerType>(BaseParams.slayer_superior)?.id ?: continue
        map.getOrPut(taskId) { mutableSetOf() }.add(superiorId)
    }
    return map
}
