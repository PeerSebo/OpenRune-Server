package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.game.entity.Player

@OptIn(InternalApi::class)
public fun Player.stat(stat: String): Int {
    return statMap.getCurrentLevel(stat).toInt() and 0xFF
}

@OptIn(InternalApi::class)
public fun Player.statBase(stat: String): Int {
    return statMap.getBaseLevel(stat).toInt() and 0xFF
}

public fun Player.statRestore(stat: String) {
    val currLevel = stat(stat)
    val baseLevel = statBase(stat)
    val delta = baseLevel - currLevel
    when {
        delta == 0 -> return
        delta < 0 -> statSub(stat, delta.absoluteValue, percent = 0)
        else -> statAdd(stat, delta, percent = 0)
    }
}

public fun Player.statRestoreAll(stats: Iterable<String>) {
    for (stat in stats) {
        statRestore(stat)
    }
}

/**
 * Increases the player's current [stat] experience.
 *
 * @param rate The xp multiplier applied to [xp]. Defaults to the player's [Player.xpRate].
 * @param globalRate A global xp multiplier applied alongside [rate]. This is separated for
 *   convenience - for example, if the caller wants to ignore the player's personal xp rate but
 *   still apply global modifiers like Double XP Weekends. Defaults to the player's
 *   [Player.globalXpRate].
 * @return The total amount of experience successfully added to the player's stat.
 */
public fun Player.statAdvance(
    stat: String,
    xp: Double,
    rate: Double = xpRate,
    globalRate: Double = globalXpRate,
): Int = PlayerSkillXP.internalAddXP(this, stat, xp, rate = rate * globalRate)

@OptIn(InternalApi::class)
public fun Player.statAdd(stat: String, constant: Int, percent: Int) {
    require(constant >= 0) { "Constant `$constant` must be positive. Use `statSub` instead." }
    require(percent in 0..100) { "Percent must be an integer from 0-100. (0%-100%)" }

    val base = statBase(stat)
    val current = stat(stat)
    val calculated = current + (constant + (base * percent) / 100)
    val cappedLevel = min(255, calculated)

    statMap.setCurrentLevel(stat, cappedLevel.toByte())

    val statType =
        ServerCacheManager.getStats(stat.asRSCM(RSCMType.STAT)) ?: error("No stat found for $stat")

    updateStat(stat)

    val clearHeroPoints = statType.isType("stat.hitpoints") && hitpoints >= baseHitpointsLvl
    if (clearHeroPoints) {
        clearHeroPoints()
    }

    if (cappedLevel != current) {
        engineQueueChangeStat(statType)
    }
}

public fun Player.statBoost(stat: String, constant: Int, percent: Int) {
    require(constant >= 0) { "Constant `$constant` must be positive. Use `statDrain` instead." }
    require(percent in 0..100) { "Percent must be an integer from 0-100. (0%-100%)" }

    val base = statBase(stat)
    val boost = constant + (base * percent) / 100

    val current = stat(stat)
    val cappedBoost = min(base + boost, current + boost) - current

    if (cappedBoost > 0) {
        statAdd(stat, cappedBoost, 0)
    }
}

@OptIn(InternalApi::class)
public fun Player.statSub(stat: String, constant: Int, percent: Int) {
    require(constant >= 0) { "Constant `$constant` must be positive." }
    require(percent in 0..100) { "Percent must be an integer from 0-100. (0%-100%)" }

    val base = statBase(stat)
    val current = stat(stat)
    val calculated = current - (constant + (base * percent) / 100)
    val cappedLevel = max(0, calculated)

    statMap.setCurrentLevel(stat, cappedLevel.toByte())

    val statType =
        ServerCacheManager.getStats(stat.asRSCM(RSCMType.STAT)) ?: error("No stat found for $stat")

    updateStat(stat)

    if (cappedLevel != current) {
        engineQueueChangeStat(statType)
    }
}

public fun Player.statDrain(stat: String, constant: Int, percent: Int) {
    require(constant >= 0) { "Constant `$constant` must be positive." }
    require(percent in 0..100) { "Percent must be an integer from 0-100. (0%-100%)" }

    val base = statBase(stat)
    val drain = constant + (base * percent) / 100

    val current = stat(stat)
    val cappedDrain = current - max(base - drain, current - drain)

    if (cappedDrain > 0) {
        statSub(stat, cappedDrain, 0)
    }
}

@OptIn(InternalApi::class)
public fun Player.statHeal(internal: String, constant: Int, percent: Int) {
    require(constant >= 0) { "Constant `$constant` must be positive." }
    require(percent in 0..100) { "Percent must be an integer from 0-100. (0%-100%)" }

    val base = statBase(internal)
    val current = stat(internal)
    val calculated = current + (constant + (base * percent) / 100)
    val cappedLevel = calculated.coerceIn(current, base)

    statMap.setCurrentLevel(internal, cappedLevel.toByte())

    val stat =
        ServerCacheManager.getStats(internal.asRSCM(RSCMType.STAT))
            ?: error("No stat found for $internal")

    updateStat(internal)

    val clearHeroPoints = stat.isType("stat.hitpoints") && hitpoints >= baseHitpointsLvl
    if (clearHeroPoints) {
        clearHeroPoints()
    }

    if (cappedLevel != current) {
        engineQueueChangeStat(stat)
    }
}

@OptIn(InternalApi::class) internal fun Player.updateStat(stat: String) = markStatUpdate(stat)

public fun Player.statRandom(
    random: GameRandom,
    stat: String,
    low: Int,
    high: Int,
    invisibleLevels: InvisibleLevels,
): Boolean {
    val invisibleBoost = invisibleLevels.get(this, stat)
    return statRandom(random, stat, low, high, invisibleBoost)
}

public fun Player.statRandom(
    random: GameRandom,
    internal: String,
    low: Int,
    high: Int,
    invisibleBoost: Int,
): Boolean = statRandom(random, internal, low, high, invisibleBoost, flatBonus = 0.0)

/**
 * As [statRandom], with [flatBonus] added to the interpolated success value before the floor.
 *
 * For bonuses OSRS applies as a flat fraction of 256 rather than as a second success curve or a
 * level shift - the Hosidius kitchen's +12.8 (easy Kourend & Kebos diary) and +25.6 (elite) burn
 * reduction being the case this was added for. See
 * [SkillingSuccessRate.successRate] for why the offset is a Double and not a pair of +12/+13
 * integers folded into [low]/[high].
 */
public fun Player.statRandom(
    random: GameRandom,
    internal: String,
    low: Int,
    high: Int,
    invisibleBoost: Int,
    flatBonus: Double,
): Boolean {

    val stat =
        ServerCacheManager.getStats(internal.asRSCM(RSCMType.STAT))
            ?: error("No stat found for $internal")

    val visibleLevel = stat(internal)
    val effectiveLevel = visibleLevel.coerceIn(1, stat.maxLevel) + invisibleBoost
    return statRandomRoll(random, low, high, effectiveLevel, stat.maxLevel, flatBonus)
}

private fun statRandomRoll(
    random: GameRandom,
    low: Int,
    high: Int,
    effectiveLevel: Int,
    maxLevel: Int,
    flatBonus: Double = 0.0,
): Boolean {
    val rate = SkillingSuccessRate.successRate(low, high, effectiveLevel, maxLevel, flatBonus)
    return rate > random.randomDouble()
}
