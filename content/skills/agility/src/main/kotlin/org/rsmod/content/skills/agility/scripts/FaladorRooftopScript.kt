package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.FaladorRooftopCourse
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        FaladorRooftopCourse.COURSE.copy(lapReward = marks.lapReward(FaladorRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_falador_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { roughWall() }
        }
        onOpLoc1("loc.rooftops_falador_tightrope_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightropeFirst() }
        }
        onOpLoc1("loc.rooftops_falador_handholds_start") {
            player.withAgilityAnimation(preserveOnCompletion = true) { handHolds() }
        }
        onOpLoc1("loc.rooftops_falador_gap_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFirst() }
        }
        onOpLoc1("loc.rooftops_falador_gap_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapSecond() }
        }
        onOpLoc1("loc.rooftops_falador_tightrope_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightropeSecond() }
        }
        onOpLoc1("loc.rooftops_falador_tightrope_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightropeThird() }
        }
        onOpLoc1("loc.rooftops_falador_gap_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapThird() }
        }
        onOpLoc1("loc.rooftops_falador_ledge_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeJump(8, FaladorRooftopCourse.LEDGE_1_DEST, FaladorRooftopCourse.LEDGE_1_XP) }
        }
        onOpLoc1("loc.rooftops_falador_ledge_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeJump(9, FaladorRooftopCourse.LEDGE_2_DEST, FaladorRooftopCourse.LEDGE_2_XP) }
        }
        onOpLoc1("loc.rooftops_falador_ledge_3a") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeJump(10, FaladorRooftopCourse.LEDGE_3_DEST, FaladorRooftopCourse.LEDGE_3_XP) }
        }
        onOpLoc1("loc.rooftops_falador_ledge_3b") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeJump(10, FaladorRooftopCourse.LEDGE_3_DEST, FaladorRooftopCourse.LEDGE_3_XP) }
        }
        onOpLoc1("loc.rooftops_falador_ledge_4") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeJump(11, FaladorRooftopCourse.LEDGE_4_DEST, FaladorRooftopCourse.LEDGE_4_XP) }
        }
        onOpLoc1("loc.rooftops_falador_edge") {
            player.withAgilityAnimation(preserveOnCompletion = true) { edgeJumpDown() }
        }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the wall...")
        player.blockRunEnergyRestore(FaladorRooftopCourse.WALL_CLIMB_TICKS)
        faceObstacleAngle(constants.em_face_north)
        animBeat("seq.human_reachforladder", delayClientCycles = 15)
        delay(FaladorRooftopCourse.WALL_CLIMB_TICKS)
        telejump(FaladorRooftopCourse.WALL_CLIMB_DEST, TeleportType.Exempt)
        resetAnim()
        awardObstacle(course, 0, FaladorRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeFirst() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            waypoints = FaladorRooftopCourse.TIGHTROPE_1_WAYPOINTS,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(FaladorRooftopCourse.TIGHTROPE_1_SOUND_LOOPS)),
        )) {
            return
        }
        awardObstacle(course, 1, FaladorRooftopCourse.TIGHTROPE_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.handHolds() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (!approachMount(FaladorRooftopCourse.HANDHOLDS_MOUNT)) return
        if (
            rollObstacleFail(
                course.levelReq,
                FaladorRooftopCourse.FAIL_LOW,
                FaladorRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your grip and fall!")
            resetLapProgress()
            stumbleAndFall(
                FaladorRooftopCourse.HANDHOLDS_FAIL_DEST,
                FaladorRooftopCourse.HANDHOLDS_DAMAGE_LOW,
                FaladorRooftopCourse.HANDHOLDS_DAMAGE_HIGH,
            )
            return
        }
        spam("You cross the hand holds.")
        faladorHandholdsCross()
        awardObstacle(course, 2, FaladorRooftopCourse.HANDHOLDS_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapFirst() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(FaladorRooftopCourse.GAP_1_MOUNT)
        spam("You jump the gap.")
        gapHop(FaladorRooftopCourse.GAP_1_DEST, "seq.human_spot_jump")
        awardObstacle(course, 3, FaladorRooftopCourse.GAP_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapSecond() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(FaladorRooftopCourse.GAP_2_MOUNT)
        spam("You jump the gap.")
        gapHop(FaladorRooftopCourse.GAP_2_DEST, "seq.human_spot_jump")
        awardObstacle(course, 4, FaladorRooftopCourse.GAP_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeSecond() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            waypoints = FaladorRooftopCourse.TIGHTROPE_2_WAYPOINTS,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(FaladorRooftopCourse.TIGHTROPE_2_SOUND_LOOPS)),
        )) {
            return
        }
        awardObstacle(course, 5, FaladorRooftopCourse.TIGHTROPE_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeThird() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (!approachMount(FaladorRooftopCourse.TIGHTROPE_3_MOUNT)) {
            return
        }
        spam("You cross the tightrope.")
        player.blockRunEnergyRestore(4)
        moveBeat(
            FaladorRooftopCourse.TIGHTROPE_3_DASH_END,
            "seq.human_walk_logbalance_loop",
            "synth.DEATHSLIDE_FIRST", movement = AgilityMovement(0, 89, 512))
        delay(3)
        resetAnim()
        soundSynth("synth.FALL_LAND")
        forcedWalk(FaladorRooftopCourse.TIGHTROPE_3_DEST, 1)
        awardObstacle(course, 6, FaladorRooftopCourse.TIGHTROPE_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapThird() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(FaladorRooftopCourse.GAP_3_MOUNT)
        spam("You jump the gap.")
        gapHop(FaladorRooftopCourse.GAP_3_DEST, "seq.human_jump_hurdle")
        awardObstacle(course, 7, FaladorRooftopCourse.GAP_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ledgeJump(index: Int, dest: CoordGrid, xp: Double) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        val mount = when (index) {
            8 -> FaladorRooftopCourse.LEDGE_1_MOUNT
            9 -> FaladorRooftopCourse.LEDGE_2_MOUNT
            10 -> FaladorRooftopCourse.LEDGE_3_MOUNT
            11 -> FaladorRooftopCourse.LEDGE_4_MOUNT
            else -> return
        }
        spam(
            when (index) {
                8 -> "You jump down to the ledge."
                9, 11 -> "You jump up the ledge."
                else -> "You jump across the ledge."
            }
        )
        if (!faladorLedgeCross(mount, dest)) return
        awardObstacle(course, index, xp, xpMods)
    }

    private suspend fun ProtectedAccess.gapHop(dest: CoordGrid, seq: String) {
        player.blockRunEnergyRestore(1)
        faceObstacle(dest)
        moveBeat(dest, seq, "synth.JUMP", movement = AgilityMovement(15, 30))
        delay(1)
        resetAnim()
    }

    private suspend fun ProtectedAccess.edgeJumpDown() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump off the edge.")
        player.blockRunEnergyRestore(3)
        faceObstacle(FaladorRooftopCourse.EDGE_HOP)
        moveBeat(FaladorRooftopCourse.EDGE_HOP, "seq.human_jump_hurdle", "synth.JUMP", movement = AgilityMovement(15, 30, 1536))
        delay(1)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(FaladorRooftopCourse.EDGE_DEST, "seq.agility_shortcut_wall_jumpdown2")
        awardObstacle(course, 12, FaladorRooftopCourse.EDGE_XP, xpMods)
        delay(1)
        resetAnim()
    }
}

internal suspend fun ProtectedAccess.faladorHandholdsCross() {
    player.blockRunEnergyRestore(12)
    moveBeat(
        FaladorRooftopCourse.HANDHOLDS_JUMP_ON,
        "seq.agility_shortcut_wall_jump",
        "synth.JUMP_UP",
        animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 1024))
    delay(1)
    moveBeat(
        FaladorRooftopCourse.HANDHOLDS_HANG,
        "seq.agilityarena_handholds_middle",
        "synth.HANDHOLDS_LOOP",
        soundDelayClientCycles = 35,
    )
    delay(1)
    moveBeat(FaladorRooftopCourse.HANDHOLDS_CORNER, movement = AgilityMovement(13, 22, 1024))
    delay(1)
    moveBeat(FaladorRooftopCourse.HANDHOLDS_CATCHUP, movement = AgilityMovement(0, 9, 512))
    delay(1)
    for (rung in FaladorRooftopCourse.HANDHOLDS_RUNGS) {
        moveBeat(rung, "seq.agilityarena_handholds_middle", "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 52, 512), soundDelayClientCycles = 35)
        delay(2)
    }
    moveBeat(
        FaladorRooftopCourse.HANDHOLDS_LAST_RUNG,
        "seq.agilityarena_handholds_last",
        "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 60, 512), soundDelayClientCycles = 35)
    delay(2)
    telejump(FaladorRooftopCourse.HANDHOLDS_DEST, TeleportType.Exempt)
    resetAnim()
}

internal suspend fun ProtectedAccess.faladorLedgeCross(mount: CoordGrid, dest: CoordGrid): Boolean {
    if (!approachMount(mount)) return false
    player.blockRunEnergyRestore(1)
    faceObstacle(dest)
    moveBeat(dest, "seq.human_jump_hurdle", "synth.JUMP", movement = AgilityMovement(15, 30))
    delay(1)
    resetAnim()
    return true
}
