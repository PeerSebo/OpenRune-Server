package org.rsmod.content.raids.theatreofblood

internal enum class VerzikSpecial {
    NYLOCAS,
    WEBS,
    POWER_BLAST,
    GREEN_BALL,
}

internal object TheatreCombatPatterns {
    fun nextRoom(currentRoom: Int): Int? {
        require(currentRoom in 0..5)
        return (currentRoom + 1).takeIf { it < 6 }
    }

    fun supplyChestAfter(currentRoom: Int): Boolean {
        require(currentRoom in 0..5)
        return currentRoom == 1 || currentRoom == 3
    }

    fun nyloWaveCap(nextWave: Int): Int {
        require(nextWave in 1..31)
        return if (nextWave < 20) 12 else 24
    }

    fun nyloWaveReady(nextWave: Int, active: Int, currentTick: Int, scheduledTick: Int): Boolean {
        require(active >= 0)
        return currentTick >= scheduledTick && active < nyloWaveCap(nextWave)
    }

    fun nyloBossReady(lastWave: Int, active: Int, survivingPillars: Int): Boolean {
        require(lastWave in 0..31 && active >= 0 && survivingPillars in 0..4)
        return lastWave == 31 && active == 0 && survivingPillars > 0
    }

    fun nyloWrongStyleMask(mask: Int, memberIndex: Int): Int {
        require(mask in 0..31 && memberIndex in 0..4)
        return mask or (1 shl memberIndex)
    }

    fun nyloMemberLocked(mask: Int, memberIndex: Int): Boolean {
        require(mask in 0..31 && memberIndex in 0..4)
        return mask and (1 shl memberIndex) != 0
    }

    fun nyloSmallDamage(
        style: TheatreAttackStyle,
        attackStyle: TheatreAttackStyle,
        damage: Int,
        memberLocked: Boolean,
    ): Int {
        require(damage >= 0)
        return if (!memberLocked && style == attackStyle) damage else 0
    }

    fun nyloSmallImpactDamage(launchDamage: Int, memberLocked: Boolean): Int {
        require(launchDamage >= 0)
        return if (memberLocked) 0 else launchDamage
    }

    fun sotetsegMazeThresholds(current: Int, maximum: Int, triggeredMask: Int): List<Int> {
        require(current in 0..maximum && maximum > 0 && triggeredMask in 0..3)
        return listOf(666, 333).filterIndexed { index, threshold ->
            triggeredMask and (1 shl index) == 0 &&
                current.toLong() * 1000 <= maximum.toLong() * threshold
        }
    }

    fun xarpusStareReady(current: Int, healthAfterRecovery: Int): Boolean =
        TheatreMechanics.atOrBelowPercent(current, healthAfterRecovery, 25)

    fun nextVerzikPhaseOnDeath(currentPhase: Int): Int {
        require(currentPhase in 0..2)
        return currentPhase + 1
    }

    fun verzikSpecial(autoAttacksSinceSpecial: Int, specialIndex: Int): VerzikSpecial? {
        require(autoAttacksSinceSpecial >= 0 && specialIndex >= 0)
        if (autoAttacksSinceSpecial < 4) return null
        return VerzikSpecial.entries[specialIndex % VerzikSpecial.entries.size]
    }

    fun verzikAttackTicks(enraged: Boolean): Int = if (enraged) 5 else 7

    fun verzikGreenBallDamage(hitpointsLevel: Int): Int {
        require(hitpointsLevel > 0)
        return (hitpointsLevel.toLong() * 3 / 4).toInt()
    }

    fun verzikTornadoDamage(currentHitpoints: Int): Int {
        require(currentHitpoints >= 0)
        return currentHitpoints / 2
    }

    fun verzikTornadoHealing(damage: Int): Int {
        require(damage >= 0)
        return Math.multiplyExact(damage, 3)
    }
}
