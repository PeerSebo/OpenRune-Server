package org.rsmod.content.raids.theatreofblood

import kotlin.math.abs

internal enum class TheatreAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal enum class TheatreQuadrant {
    NORTH_EAST,
    SOUTH_EAST,
    SOUTH_WEST,
    NORTH_WEST,
}

internal data class TheatreTile(val x: Int, val z: Int)

internal data class TheatreHit(
    val damageToBoss: Int,
    val healingToBoss: Int,
    val reflectedDamage: Int,
)

internal object TheatreMechanics {
    fun normalHitpoints(base: Int, teamSize: Int): Int {
        require(base > 0)
        require(teamSize in 1..5)
        val numerator = when (teamSize) {
            1, 2, 3 -> 6
            4 -> 7
            else -> 8
        }
        return (base.toLong() * numerator / 8).toInt().coerceAtLeast(1)
    }

    fun atOrBelowPercent(current: Int, maximum: Int, percent: Int): Boolean {
        require(current >= 0 && maximum > 0 && current <= maximum)
        require(percent in 0..100)
        return current.toLong() * 100 <= maximum.toLong() * percent
    }

    fun maidenThresholds(current: Int, maximum: Int, triggeredMask: Int): List<Int> {
        require(triggeredMask in 0..7)
        require(current >= 0 && maximum > 0 && current <= maximum)
        return listOf(70, 50, 30).filterIndexed { index, threshold ->
            triggeredMask and (1 shl index) == 0 &&
                atOrBelowPercent(current, maximum, threshold)
        }
    }

    fun markMaidenThreshold(triggeredMask: Int, threshold: Int): Int {
        require(triggeredMask in 0..7)
        val index = when (threshold) {
            70 -> 0
            50 -> 1
            30 -> 2
            else -> error("Unknown Maiden threshold: $threshold")
        }
        return triggeredMask or (1 shl index)
    }

    fun maidenHealing(nylocasHitpoints: Int): Int {
        require(nylocasHitpoints >= 0)
        return Math.multiplyExact(nylocasHitpoints, 2)
    }

    fun maidenMagicDamage(absorbedHealers: Int, protectFromMagic: Boolean): Int {
        require(absorbedHealers >= 0)
        val damage = (73L + 7L * absorbedHealers) / 2
        return Math.toIntExact(if (protectFromMagic) damage / 2 else damage)
    }

    fun healedHitpoints(current: Int, maximum: Int, healing: Int): Int {
        require(current in 0..maximum && maximum > 0 && healing >= 0)
        return (current.toLong() + healing).coerceAtMost(maximum.toLong()).toInt()
    }

    fun bloatIncomingDamage(damage: Int, moving: Boolean): Int {
        require(damage >= 0)
        return if (moving) damage / 2 else damage
    }

    fun bloatFlyDamage(damage: Int, protectFromMissiles: Boolean): Int {
        require(damage >= 0)
        return if (protectFromMissiles) (damage.toLong() * 3 / 4).toInt() else damage
    }

    fun bloatRuns(current: Int, maximum: Int, runningBelowForty: Boolean): Boolean {
        require(current >= 0 && maximum > 0 && current <= maximum)
        return when {
            current.toLong() * 100 >= maximum.toLong() * 60 -> false
            current.toLong() * 100 >= maximum.toLong() * 40 -> true
            else -> runningBelowForty
        }
    }

    fun bloatCanSleep(currentTick: Int, scheduledTick: Int, lastMovementChange: Int, cooldown: Int = 5): Boolean =
        currentTick >= scheduledTick && currentTick.toLong() - lastMovementChange >= cooldown

    fun nyloHit(
        currentStyle: TheatreAttackStyle,
        attackStyle: TheatreAttackStyle,
        damage: Int,
    ): TheatreHit {
        require(damage >= 0)
        return if (currentStyle == attackStyle) {
            TheatreHit(damageToBoss = damage, healingToBoss = 0, reflectedDamage = 0)
        } else {
            TheatreHit(damageToBoss = 0, healingToBoss = damage, reflectedDamage = damage)
        }
    }

    fun nextNyloStyle(current: TheatreAttackStyle, choice: Int): TheatreAttackStyle {
        require(choice in 0..1)
        return TheatreAttackStyle.entries.filter { it != current }[choice]
    }

    fun sharedBallDamage(totalDamage: Int, participants: Int, minimumDamage: Int): Int {
        require(totalDamage >= 0 && participants in 1..5 && minimumDamage >= 0)
        return ((totalDamage.toLong() + participants - 1) / participants)
            .coerceAtLeast(minimumDamage.toLong())
            .toInt()
    }

    fun connectedMaze(path: List<TheatreTile>, allowed: Set<TheatreTile>): Boolean {
        if (path.isEmpty() || path.toSet().size != path.size || !allowed.containsAll(path)) {
            return false
        }
        return path.zipWithNext().all { (from, to) ->
            abs(from.x.toLong() - to.x) + abs(from.z.toLong() - to.z) == 1L
        }
    }

    fun xarpusQuadrant(center: TheatreTile, tile: TheatreTile): TheatreQuadrant? {
        if (tile == center) return null
        val east = tile.x >= center.x
        val north = tile.z >= center.z
        return when {
            east && north -> TheatreQuadrant.NORTH_EAST
            east -> TheatreQuadrant.SOUTH_EAST
            north -> TheatreQuadrant.NORTH_WEST
            else -> TheatreQuadrant.SOUTH_WEST
        }
    }

    fun xarpusRetaliates(
        staring: TheatreQuadrant,
        center: TheatreTile,
        attacker: TheatreTile,
    ): Boolean = xarpusQuadrant(center, attacker) == staring

    fun verzikFirstPhaseDamage(
        style: TheatreAttackStyle,
        damage: Int,
        dawnbringerSpecial: Boolean = false,
    ): Int {
        require(damage >= 0)
        if (dawnbringerSpecial) return damage
        return damage.coerceAtMost(if (style == TheatreAttackStyle.MELEE) 10 else 3)
    }

    fun verzikBloodPhase(current: Int, maximum: Int): Boolean {
        require(current >= 0 && maximum > 0 && current <= maximum)
        return current.toLong() * 100 < maximum.toLong() * 35
    }

    fun verzikEnraged(current: Int, maximum: Int): Boolean =
        atOrBelowPercent(current, maximum, 20)

    fun rewardShare(points: Int, teamPoints: Long): Double {
        require(points >= 0 && teamPoints >= points.toLong())
        return if (teamPoints == 0L) 0.0 else points.toDouble() / teamPoints
    }

    fun applyRepeatedPenalty(
        points: Int,
        deaths: Int,
        retainedNumerator: Int,
        retainedDenominator: Int,
    ): Int {
        require(points >= 0 && deaths >= 0)
        require(retainedDenominator > 0 && retainedNumerator in 0..retainedDenominator)
        if (retainedNumerator == retainedDenominator) return points
        var remaining = points
        var applied = 0
        while (remaining > 0 && applied < deaths) {
            remaining = (remaining.toLong() * retainedNumerator / retainedDenominator).toInt()
            applied++
        }
        return remaining
    }
}
