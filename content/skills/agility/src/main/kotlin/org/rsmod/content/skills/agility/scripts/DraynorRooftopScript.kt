package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.DraynorRooftopCourse
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {
    private val course =
        DraynorRooftopCourse.COURSE.copy(lapReward = marks.lapReward(DraynorRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_draynor_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_draynor_tightrope_1") { tightropeFirst() }
        onOpLoc1("loc.rooftops_draynor_tightrope_2") { tightropeSecond() }
        onOpLoc1("loc.rooftops_draynor_wallcrossing") { narrowWall() }
        onOpLoc1("loc.rooftops_draynor_wallscramble") { wallScramble() }
        onOpLoc1("loc.rooftops_draynor_leapdown") { gapJump() }
        onOpLoc1("loc.rooftops_draynor_crate") { crateClimbDown() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You climb the wall...")
        draynorRoughWall()
        awardObstacle(course, 0, DraynorRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeFirst() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You cross the tightrope.")
        if (!balanceCross(
            mount = DraynorRooftopCourse.TIGHTROPE_1_MOUNT,
            dest = DraynorRooftopCourse.TIGHTROPE_1_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(3), BalanceSound(3, delayTicks = 5)),
        )) return
        forcedWalk(DraynorRooftopCourse.TIGHTROPE_1_EXIT, crossTiles = 1)
        awardObstacle(course, 1, DraynorRooftopCourse.TIGHTROPE_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeSecond() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You cross the tightrope.")
        if (!balanceCross(
            mount = DraynorRooftopCourse.TIGHTROPE_2_MOUNT,
            dest = DraynorRooftopCourse.TIGHTROPE_2_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(7)),
        )) return
        forcedWalk(DraynorRooftopCourse.TIGHTROPE_2_EXIT, crossTiles = 1)
        awardObstacle(course, 2, DraynorRooftopCourse.TIGHTROPE_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.narrowWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You balance across the narrow wall.")
        draynorNarrowWall { awardObstacle(course, 3, DraynorRooftopCourse.NARROW_WALL_XP, xpMods) }
    }

    private suspend fun ProtectedAccess.wallScramble() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You jump up the wall.")
        draynorWallScramble()
        awardObstacle(course, 4, DraynorRooftopCourse.WALL_SCRAMBLE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapJump() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You jump the gap.")
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(2)
            faceObstacle(DraynorRooftopCourse.GAP_DEST)
            animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
            delay(1)
            moveBeat(DraynorRooftopCourse.GAP_DEST, "seq.agility_shortcut_wall_jumpdown2")
            delay(1)
            resetAnim()
            awardObstacle(course, 5, DraynorRooftopCourse.GAP_XP, xpMods)
        }
    }

    private suspend fun ProtectedAccess.crateClimbDown() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You climb down the crates.")
        draynorCrateDown { awardObstacle(course, 6, DraynorRooftopCourse.CRATE_XP, xpMods) }
    }
}

internal suspend fun ProtectedAccess.draynorRoughWall() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(DraynorRooftopCourse.WALL_CLIMB_TICKS)
    faceObstacleAngle(constants.em_face_west)
    animBeat("seq.human_reachforladder", delayClientCycles = 15)
    delay(DraynorRooftopCourse.WALL_CLIMB_TICKS)
    telejump(DraynorRooftopCourse.WALL_CLIMB_DEST, TeleportType.Exempt)
    resetAnim()
}

internal suspend fun ProtectedAccess.draynorNarrowWall(onLanding: () -> Unit = {}): Boolean {
    if (!approachMount(DraynorRooftopCourse.NARROW_WALL_APPROACH)) return false
    player.withAgilityAnimation {
        player.blockRunEnergyRestore(5)
        withBalancePose("seq.human_walk_sidestepl", readySeq = "seq.human_ready_sidestepl") {
            animBeat("seq.human_into_sidestepl")
            forcedWalk(DraynorRooftopCourse.NARROW_WALL_MOUNT, crossTiles = 1)
            resetAnim()
            soundSynth("synth.BALANCING_LEDGE", loops = 3)
            forcedWalk(DraynorRooftopCourse.NARROW_WALL_WAYPOINTS, crossTiles = 3)
        }
        animBeat("seq.human_outof_sidestepl")
        onLanding()
        delay(1)
    }
    return true
}

internal suspend fun ProtectedAccess.draynorWallScramble() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(DraynorRooftopCourse.WALL_SCRAMBLE_TICKS)
    faceObstacle(DraynorRooftopCourse.WALL_SCRAMBLE_LAUNCH)
    moveBeat(
        DraynorRooftopCourse.WALL_SCRAMBLE_LAUNCH,
        "seq.agility_shortcut_wall_jump",
        "synth.JUMP_UP",
        animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 0))
    delay(1)
    animBeat("seq.agility_shortcut_wall_jump2")
    delay(1)
    glideTo(DraynorRooftopCourse.WALL_SCRAMBLE_DEST, AgilityMovement(17, 26, 0))
    delay(1)
    resetAnim()
}

internal suspend fun ProtectedAccess.draynorCrateDown(onLanding: () -> Unit) = player.withAgilityAnimation {
    player.blockRunEnergyRestore(DraynorRooftopCourse.CRATE_TICKS)
    faceObstacle(DraynorRooftopCourse.CRATE_LEDGE)
    animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
    delay(1)
    moveBeat(DraynorRooftopCourse.CRATE_LEDGE, "seq.agility_shortcut_wall_jumpdown2")
    delay(1)
    resetAnim()
    delay(1)
    animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
    delay(1)
    moveBeat(DraynorRooftopCourse.CRATE_DEST, "seq.agility_shortcut_wall_jumpdown2")
    onLanding()
    delay(1)
    resetAnim()
}
