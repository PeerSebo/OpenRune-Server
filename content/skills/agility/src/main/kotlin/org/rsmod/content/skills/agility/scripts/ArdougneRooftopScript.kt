package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.ArdougneRooftopCourse
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ArdougneRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        ArdougneRooftopCourse.COURSE.copy(lapReward = marks.lapReward(ArdougneRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_ardy_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { woodenBeams() }
        }
        onOpLoc1("loc.rooftops_ardy_jump") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFirst(it.loc) }
        }
        onOpLoc1("loc.rooftops_ardy_plank") {
            player.withAgilityAnimation(preserveOnCompletion = true) { plank() }
        }
        onOpLoc1("loc.rooftops_ardy_jump_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapSecond(it.loc) }
        }
        onOpLoc1("loc.rooftops_ardy_jump_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapThird(it.loc) }
        }
        onOpLoc1("loc.rooftops_ardy_wallcrossing") {
            player.withAgilityAnimation(preserveOnCompletion = true) { steepRoof() }
        }
        onOpLoc1("loc.rooftops_ardy_jump_4") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFinal() }
        }
        onOpLoc1("loc.rooftops_ardy_leapdown") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFinal() }
        }
    }

    private suspend fun ProtectedAccess.woodenBeams() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the beams...")
        ardougneWoodenBeams()
        awardObstacle(course, 0, ArdougneRooftopCourse.WOODEN_BEAMS_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapFirst(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (
            rollObstacleFail(
                course.levelReq,
                ArdougneRooftopCourse.FAIL_LOW,
                ArdougneRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                ArdougneRooftopCourse.GAP_1_FAIL_DEST,
                ArdougneRooftopCourse.FAIL_DAMAGE_LOW,
                ArdougneRooftopCourse.FAIL_DAMAGE_HIGH,
            )
            return
        }
        spam("You leap across the gap.")
        leapTo(
            dest = ArdougneRooftopCourse.GAP_1_DEST,
            busyTicks = ArdougneRooftopCourse.GAP_1_TICKS,
            seq = "seq.human_longjump",
            synth = "synth.JUMP2",
            face = loc.coords,
        )
        awardObstacle(course, 1, ArdougneRooftopCourse.GAP_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.plank() {
        arriveDelay()
        spam("You walk across the plank.")
        if (!balanceCross(
            mount = ArdougneRooftopCourse.PLANK_MOUNT,
            dest = ArdougneRooftopCourse.PLANK_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.LOG_BALANCE",
        )) {
            return
        }
        awardObstacle(course, 2, ArdougneRooftopCourse.PLANK_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapSecond(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (
            rollObstacleFail(
                course.levelReq,
                ArdougneRooftopCourse.FAIL_LOW,
                ArdougneRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                ArdougneRooftopCourse.GAP_2_FAIL_DEST,
                ArdougneRooftopCourse.FAIL_DAMAGE_LOW,
                ArdougneRooftopCourse.FAIL_DAMAGE_HIGH,
            )
            return
        }
        spam("You leap across the gap.")
        leapTo(
            dest = ArdougneRooftopCourse.GAP_2_DEST,
            busyTicks = ArdougneRooftopCourse.GAP_2_TICKS,
            seq = "seq.human_longjump",
            synth = "synth.JUMP2",
            face = loc.coords,
        )
        awardObstacle(course, 3, ArdougneRooftopCourse.GAP_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapThird(loc: BoundLocInfo) {
        arriveDelay()
        spam("You leap across the gap.")
        leapTo(
            dest = ArdougneRooftopCourse.GAP_3_DEST,
            busyTicks = ArdougneRooftopCourse.GAP_3_TICKS,
            seq = "seq.human_longjump",
            synth = "synth.JUMP2",
            face = loc.coords,
        )
        awardObstacle(course, 4, ArdougneRooftopCourse.GAP_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.steepRoof() {
        arriveDelay()
        spam("You balance across the steep roof.")
        if (!balanceCross(
            waypoints = ArdougneRooftopCourse.STEEP_ROOF_WAYPOINTS,
            seq = "seq.human_walk_logbalance",
            synth = "synth.BALANCING_LEDGE",
        )) {
            return
        }
        awardObstacle(course, 5, ArdougneRooftopCourse.STEEP_ROOF_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapFinal() {
        arriveDelay()
        approachMount(ArdougneRooftopCourse.GAP_FINAL_MOUNT)
        spam("You jump the gap.")
        leapTo(
            dest = ArdougneRooftopCourse.GAP_FINAL_LEDGE,
            busyTicks = ArdougneRooftopCourse.GAP_FINAL_LEAP_TICKS,
            seq = "seq.agility_shortcut_wall_jumpdown",
            synth = "synth.JUMP_AND_FALL",
            face = ArdougneRooftopCourse.GAP_FINAL_LEDGE,
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
        )
        delay(ArdougneRooftopCourse.GAP_FINAL_LEDGE_SETTLE_TICKS)
        forcedWalk(
            ArdougneRooftopCourse.GAP_FINAL_WALK_1,
            maxOf(1, player.coords.chebyshevDistance(ArdougneRooftopCourse.GAP_FINAL_WALK_1)),
        )
        delay(ArdougneRooftopCourse.GAP_FINAL_BREAK_1_PAUSE_TICKS)
        spotHop(ArdougneRooftopCourse.GAP_FINAL_HOP_1, face = ArdougneRooftopCourse.GAP_FINAL_HOP_1)
        forcedWalk(
            ArdougneRooftopCourse.GAP_FINAL_WALK_2,
            maxOf(1, player.coords.chebyshevDistance(ArdougneRooftopCourse.GAP_FINAL_WALK_2)),
        )
        delay(ArdougneRooftopCourse.GAP_FINAL_BREAK_2_PAUSE_TICKS)
        spotHop(ArdougneRooftopCourse.GAP_FINAL_HOP_2, face = ArdougneRooftopCourse.GAP_FINAL_HOP_2)
        leapTo(
            dest = ArdougneRooftopCourse.GAP_FINAL_DEST,
            busyTicks = ArdougneRooftopCourse.GAP_FINAL_LEAP_TICKS,
            seq = "seq.agility_shortcut_wall_jumpdown",
            synth = "synth.JUMP_AND_FALL",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
        )
        awardObstacle(course, 6, ArdougneRooftopCourse.GAP_FINAL_XP, xpMods)
    }
}

internal suspend fun ProtectedAccess.ardougneWoodenBeams() = climbTo(
    ArdougneRooftopCourse.WOODEN_BEAMS_DEST,
    ArdougneRooftopCourse.WOODEN_BEAMS_TICKS,
    "seq.human_climbing",
    "synth.CLIMB_WALL",
    faceAngle = constants.em_face_north,
)
