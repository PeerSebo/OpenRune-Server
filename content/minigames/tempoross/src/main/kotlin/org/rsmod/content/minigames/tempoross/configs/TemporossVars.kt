package org.rsmod.content.minigames.tempoross.configs

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

var Player.temporossBestPoints by intVarp("varp.tempoross_best_points")
var Player.temporossBestTicks by intVarp("varp.tempoross_best_ticks")
var Player.temporossRewardSearches by intVarp("varp.tempoross_reward_searches")

object TemporossOutfitUnlocks {
    val parts = listOf("hat", "top", "legs", "boots")

    fun has(player: Player, part: String): Boolean {
        require(part in parts)
        return player.vars["varbit.tempoross_spirit_angler_$part"] != 0
    }

    fun unlock(player: Player, part: String) {
        require(part in parts)
        VarPlayerIntMapSetter.set(player, "varbit.tempoross_spirit_angler_$part", 1)
    }
}

private val LegacyBestPoints = AttributeKey<Int>(persistenceKey = "tempoross_best_points")
private val LegacyBestTicks = AttributeKey<Int>(persistenceKey = "tempoross_best_ticks")
private val LegacySearches = AttributeKey<Int>(persistenceKey = "tempoross_reward_searches")
private val LegacyOutfitUnlocks = TemporossOutfitUnlocks.parts.associateWith {
    AttributeKey<Boolean>(persistenceKey = "tempoross_spirit_angler_$it")
}

internal fun Player.migrateTemporossVars() {
    attr[LegacyBestPoints]?.let { temporossBestPoints = maxOf(temporossBestPoints, it, 0) }
    attr[LegacyBestTicks]?.takeIf { it > 0 }?.let {
        temporossBestTicks = if (temporossBestTicks > 0) minOf(temporossBestTicks, it) else it
    }
    attr[LegacySearches]?.let { temporossRewardSearches = maxOf(temporossRewardSearches, it, 0) }
    for ((part, key) in LegacyOutfitUnlocks) {
        if (attr[key] == true) TemporossOutfitUnlocks.unlock(this, part)
        if (attr.has(key)) attr.remove(key)
    }
    if (attr.has(LegacyBestPoints)) attr.remove(LegacyBestPoints)
    if (attr.has(LegacyBestTicks)) attr.remove(LegacyBestTicks)
    if (attr.has(LegacySearches)) attr.remove(LegacySearches)
}
