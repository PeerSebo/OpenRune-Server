package org.rsmod.content.skills.agility.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk as sharedForcedWalk
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AgilityCourse
import org.rsmod.content.skills.agility.configs.AgilityFailRates
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

// exactMove delays use 20ms client cycles; one server tick is 30 cycles.
internal const val CLIENT_CYCLES_PER_TICK = 30

internal const val AGILITY_STAT = "stat.agility"

internal fun ProtectedAccess.meetsCourseReq(course: AgilityCourse): Boolean {
    if (player.agilityLvl >= course.levelReq) {
        // Client enum 3507 keys differ from persisted lap IDs; rejected entry must not change the helper.
        vars["varbit.helper_agility_current_course"] = course.helperCourseId
        return true
    }
    mes("You need an Agility level of ${course.levelReq} to use this obstacle.")
    return false
}

internal fun ProtectedAccess.awardObstacle(
    course: AgilityCourse,
    obstacleIndex: Int,
    xp: Double,
    xpMods: XpModifiers,
) {
    val mod = xpMods.get(player, AGILITY_STAT)
    val lapComplete = AgilityCourseProgress.completeObstacle(player, course, obstacleIndex)
    // Combine obstacle and lap XP to avoid duplicate level-up checks in the same tick.
    val lapBonus = if (lapComplete) course.lapBonusXp else 0.0
    val combinedFinalWithoutLap =
        course.finalObstacleXpRequiresLap && obstacleIndex == course.obstacleCount - 1 && !lapComplete
    val obstacleXp = if (combinedFinalWithoutLap) 0.0 else xp
    val totalXp = (obstacleXp + lapBonus) * mod
    if (totalXp > 0.0) {
        statAdvance(AGILITY_STAT, totalXp)
    }
    if (course.restoresRunEnergy) {
        restoreRunEnergyPercent(random.of(1, 2))
    }
    if (!lapComplete) {
        return
    }
    val laps = AgilityCourseProgress.incrementLaps(player, course)
    mes("Your ${course.displayName} lap count is: <col=ff0000>$laps</col>.")
    publish(PlayerActionCompleteEvent(player, PlayerAction.AgilityLap, course.lapCountVarp))
    course.lapReward.onLapComplete(this, course)
}

private fun ProtectedAccess.restoreRunEnergyPercent(percent: Int) {
    val restored =
        (player.runEnergy + percent * RUN_ENERGY_PER_PERCENT).coerceAtMost(constants.run_max_energy)
    if (restored == player.runEnergy) {
        return
    }
    player.runEnergy = restored
    UpdateRun.energy(player, restored)
}

private const val RUN_ENERGY_PER_PERCENT = constants.run_max_energy / 100

// Wait for actual arrival, not estimated distance. Force only nearby blocked mount tiles; abort stalled routes.
internal suspend fun ProtectedAccess.approachMount(mount: CoordGrid): Boolean {
    // Forced mounting must not bridge different planes.
    if (player.coords.level != mount.level) {
        return false
    }
    if (player.coords == mount) {
        return true
    }
    if (player.coords.chebyshevDistance(mount) == 1) {
        forcedWalk(mount, crossTiles = 1)
        return player.coords == mount
    }
    walk(mount)
    player.moveSpeed = MoveSpeed.Walk
    delay(1)
    var waited = 1
    var previous = player.coords
    while (player.coords != mount && waited < APPROACH_TIMEOUT_TICKS) {
        if (player.routeDestination.isEmpty()) {
            break
        }
        delay(1)
        waited++
        if (player.coords == previous) {
            break
        }
        previous = player.coords
    }
    if (player.coords == mount) {
        return true
    }
    if (player.coords.level != mount.level || player.coords.chebyshevDistance(mount) > FORCED_MOUNT_RADIUS) {
        return false
    }
    forcedWalk(mount, player.coords.chebyshevDistance(mount))
    return player.coords == mount
}

// BAS poses animate only during real walking, not exactMove. Restore in finally after interrupted crossings.
internal suspend fun ProtectedAccess.withBalancePose(
    seq: String,
    readySeq: String = when (seq) {
        "seq.human_walk_logbalance" -> "seq.human_walk_logbalance_ready"
        "seq.human_walk_sidestepl" -> "seq.human_ready_sidestepl"
        else -> seq
    },
    block: suspend () -> Unit,
) {
    val anim = seq.asRSCM(RSCMType.SEQ)
    val ready = readySeq.asRSCM(RSCMType.SEQ)
    val monkeybars = seq == "seq.human_monkeybars_walk"
    val sidestep = seq == "seq.human_walk_sidestepl"
    val capturedPose = monkeybars || sidestep || seq == "seq.human_walk_logbalance"
    val turn = if (monkeybars || sidestep) ready else anim
    val sideways = if (monkeybars) ready else anim
    val previous = player.bas
    player.bas =
        BasType(
            readyAnim = ready,
            turnOnSpot = turn,
            walkForward = anim,
            walkBack = sideways,
            walkLeft = sideways,
            walkRight = sideways,
            running = if (capturedPose) -1 else anim,
        )
    player.rebuildAppearance()
    try {
        block()
    } finally {
        player.bas = previous
        player.rebuildAppearance()
    }
}

// Callers must skip XP/progress when mounting fails. Use explicit waypoints when the obstacle bends.
internal suspend fun ProtectedAccess.balanceCross(
    mount: CoordGrid,
    dest: CoordGrid,
    seq: String,
    synth: String?,
    sounds: List<BalanceSound>? = null,
): Boolean = balanceCross(listOf(mount, dest), seq, synth, sounds)

internal data class BalanceSound(val loops: Int, val delayTicks: Int = 1)

internal suspend fun ProtectedAccess.balanceCross(
    waypoints: List<CoordGrid>,
    seq: String,
    synth: String?,
    sounds: List<BalanceSound>? = null,
): Boolean {
    require(waypoints.size >= 2) { "`waypoints` needs a mount and at least one further tile." }
    val mount = waypoints.first()
    val dest = waypoints.last()
    if (!approachMount(mount)) {
        return false
    }
    // Use the sum of waypoint legs, not straight-line distance, for the crossing duration.
    val crossTiles = waypoints.zipWithNext { a, b -> a.chebyshevDistance(b) }.sum().coerceAtLeast(1)
    // Forced walking would regenerate energy each tick; suppress it only after the ordinary mount approach.
    player.blockRunEnergyRestore(crossTiles)
    if (synth != null) {
        val beats = sounds ?: listOf(BalanceSound(maxOf(1, crossTiles - 1), delayTicks = 0))
        for (beat in beats) {
            soundSynth(synth, loops = beat.loops, delay = beat.delayTicks * CLIENT_CYCLES_PER_TICK)
        }
    }
    withBalancePose(seq) {
        // Clear any sequence that would override the walking BAS.
        resetAnim()
        forcedWalk(waypoints, crossTiles)
    }
    return true
}

internal suspend fun ProtectedAccess.forcedWalk(dest: CoordGrid, crossTiles: Int) {
    sharedForcedWalk(dest, crossTiles)
}

internal suspend fun ProtectedAccess.forcedWalk(waypoints: List<CoordGrid>, crossTiles: Int) {
    sharedForcedWalk(waypoints, crossTiles)
}

private const val FORCED_MOUNT_RADIUS = 3

// Cancellation must stop the sequence; normal completion may preserve a final animation beat.
internal suspend fun Player.withAgilityAnimation(
    preserveOnCompletion: Boolean = false,
    block: suspend () -> Unit,
) {
    var completed = false
    try {
        block()
        completed = true
    } finally {
        if (!completed || !preserveOnCompletion) resetAnim()
        resetPendingFaceSquare()
    }
}

// Obstacle placement bypasses teleport restrictions. Reset first so an equal-priority looping sequence cannot win.
internal suspend fun ProtectedAccess.climbTo(
    dest: CoordGrid,
    busyTicks: Int,
    seq: String,
    synth: String,
    face: CoordGrid? = null,
    landSeq: String? = null,
    animDelayClientCycles: Int = 0,
    faceAngle: Int? = null,
): Unit = player.withAgilityAnimation {
    player.blockRunEnergyRestore(busyTicks)
    if (faceAngle != null) faceObstacleAngle(faceAngle) else faceObstacle(face ?: dest)
    resetAnim()
    anim(seq, delay = animDelayClientCycles)
    soundSynth(synth, delay = animDelayClientCycles)
    if (landSeq == null) {
        delay(busyTicks)
        telejump(dest, TeleportType.Exempt)
        // Climbing sequences loop; stop them on landing.
        resetAnim()
        return@withAgilityAnimation
    }
    // The landing animation occupies the final tick of busyTicks.
    delay(busyTicks - 1)
    // Reset before the landing sequence so it replaces the pending leap in this tick's animation slot.
    telejump(dest, TeleportType.Exempt)
    resetAnim()
    anim(landSeq)
    delay(1)
    resetAnim()
}

internal suspend fun ProtectedAccess.leapTo(
    dest: CoordGrid,
    busyTicks: Int,
    seq: String,
    synth: String,
    face: CoordGrid? = null,
    landSeq: String? = null,
): Unit = climbTo(dest, busyTicks, seq, synth, face, landSeq)

internal suspend fun ProtectedAccess.launchTo(
    dest: CoordGrid,
    busyTicks: Int,
    launchSeq: String,
    landSeq: String,
    synth: String,
    face: CoordGrid? = null,
): Unit = player.withAgilityAnimation {
    player.blockRunEnergyRestore(busyTicks)
    faceObstacle(face ?: dest)
    resetAnim()
    anim(launchSeq)
    soundSynth(synth)
    delay(1)
    telejump(dest, TeleportType.Exempt)
    resetAnim()
    anim(landSeq)
    delay(busyTicks - 1)
    resetAnim()
}

internal suspend fun ProtectedAccess.spotHop(
    dest: CoordGrid,
    face: CoordGrid? = null,
    synth: String? = null,
): Unit = player.withAgilityAnimation {
    player.blockRunEnergyRestore(SPOT_HOP_TICKS)
    faceObstacle(face ?: dest)
    resetAnim()
    anim("seq.human_spot_jump")
    if (synth != null) {
        soundSynth(synth)
    }
    delay(1)
    telejump(dest, TeleportType.Exempt)
    resetAnim()
}

private const val SPOT_HOP_TICKS = 2

internal fun ProtectedAccess.moveBeat(
    dest: CoordGrid,
    seq: String? = null,
    synth: String? = null,
    animDelayClientCycles: Int = 0,
    movement: AgilityMovement? = null,
    soundDelayClientCycles: Int = animDelayClientCycles,
) {
    if (movement != null) {
        glideTo(dest, movement)
    } else {
        faceObstacle(dest)
        telejump(dest, TeleportType.Exempt)
    }
    if (seq != null) {
        resetAnim()
        anim(seq, delay = animDelayClientCycles)
    }
    if (synth != null) {
        soundSynth(synth, delay = soundDelayClientCycles)
    }
}

internal fun ProtectedAccess.animBeat(seq: String, synth: String? = null, delayClientCycles: Int = 0) {
    resetAnim()
    anim(seq, delay = delayClientCycles)
    if (synth != null) {
        soundSynth(synth, delay = delayClientCycles)
    }
}

internal fun ProtectedAccess.rollObstacleFail(levelReq: Int, low: Int, high: Int): Boolean {
    val level = player.agilityLvl
    if (level < levelReq) {
        return true
    }
    val successValue = AgilityFailRates.successValue(level, low, high)
    return successValue <= random.of(AgilityFailRates.ROLL_RANGE)
}

internal fun ProtectedAccess.fallAndDamage(dest: CoordGrid, damageLow: Int, damageHigh: Int) {
    telejump(dest, TeleportType.Exempt)
    soundSynth("synth.FALL_LAND")
    queueHit(delay = 1, type = HitType.Typeless, damage = random.of(damageLow, damageHigh))
}

// Reset progress before suspending the fall, so cancellation cannot preserve a failed lap.
internal fun ProtectedAccess.resetLapProgress() {
    AgilityCourseProgress.reset(player)
}

internal suspend fun ProtectedAccess.stumbleAndFall(
    failDest: CoordGrid,
    damageLow: Int,
    damageHigh: Int,
): Unit = player.withAgilityAnimation {
    player.blockRunEnergyRestore(STUMBLE_TICKS + 1)
    resetAnim()
    anim("seq.human_walk_logbalance_stumble")
    soundSynth("synth.STUMBLE_LOOP", loops = STUMBLE_LOOP_COUNT)
    delay(STUMBLE_TICKS)
    resetAnim()
    fallAndDamage(failDest, damageLow, damageHigh)
}

// The stumble lasts 76 client cycles; allow three ticks before dropping.
private const val STUMBLE_TICKS = 3

private const val STUMBLE_LOOP_COUNT = 10

private const val APPROACH_TIMEOUT_TICKS = 6
