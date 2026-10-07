package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.RellekkaRooftopCourse
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RellekkaRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        RellekkaRooftopCourse.COURSE.copy(lapReward = marks.lapReward(RellekkaRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_rellekka_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { roughWall() }
        }
        onOpLoc1("loc.rooftops_rellekka_gap_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapLeap(it.loc) }
        }
        onOpLoc1("loc.rooftops_rellekka_tightrope_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightropeFirst() }
        }
        onOpLoc1("loc.rooftops_rellekka_gap_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { slopingRoofGap(it.loc) }
        }
        onOpLoc1("loc.rooftops_rellekka_gap_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { hutHurdle(it.loc) }
        }
        onOpLoc1("loc.rooftops_rellekka_tightrope_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightropeLong() }
        }
        onOpLoc1("loc.rooftops_rellekka_dropoff") {
            player.withAgilityAnimation(preserveOnCompletion = true) { fishPileJump() }
        }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the wall...")
        climbTo(
            RellekkaRooftopCourse.WALL_CLIMB_DEST,
            RellekkaRooftopCourse.WALL_CLIMB_TICKS,
            "seq.human_climbing",
            "synth.CLIMB_WALL",
            face = RellekkaRooftopCourse.WALL_CLIMB_DEST,
        )
        awardObstacle(course, 0, RellekkaRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapLeap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        leapTo(
            dest = RellekkaRooftopCourse.GAP_1_DEST,
            busyTicks = RellekkaRooftopCourse.GAP_1_TICKS,
            seq = "seq.human_longjump",
            synth = "synth.JUMP2",
            face = loc.coords,
        )
        awardObstacle(course, 1, RellekkaRooftopCourse.GAP_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeFirst() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (
            rollObstacleFail(
                course.levelReq,
                RellekkaRooftopCourse.FAIL_LOW,
                RellekkaRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            val damage =
                (player.hitpoints / RellekkaRooftopCourse.FAIL_DAMAGE_DIVISOR).coerceAtLeast(1)
            stumbleAndFall(RellekkaRooftopCourse.TIGHTROPE_1_FAIL_DEST, damage, damage)
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            waypoints = RellekkaRooftopCourse.TIGHTROPE_1_WAYPOINTS,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        forcedWalk(RellekkaRooftopCourse.TIGHTROPE_1_EXIT, crossTiles = 1)
        awardObstacle(course, 2, RellekkaRooftopCourse.TIGHTROPE_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.slopingRoofGap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap...")
        leapTo(
            dest = RellekkaRooftopCourse.GAP_2_LEAP_DEST,
            busyTicks = RellekkaRooftopCourse.GAP_2_LEAP_TICKS,
            seq = "seq.human_longjump",
            synth = "synth.JUMP2",
            face = loc.coords,
        )
        spam("... and shuffle along the sloping roof.")
        if (!balanceCross(
            mount = player.coords,
            dest = RellekkaRooftopCourse.GAP_2_SHUFFLE_WAYPOINT,
            seq = "seq.human_walk_logbalance",
            synth = "synth.BALANCING_LEDGE",
        )) {
            return
        }
        if (!balanceCross(
            mount = player.coords,
            dest = RellekkaRooftopCourse.GAP_2_SHUFFLE_TURN,
            seq = "seq.human_walk_logbalance",
            synth = "synth.BALANCING_LEDGE",
        )) {
            return
        }
        if (!balanceCross(
            mount = player.coords,
            dest = RellekkaRooftopCourse.GAP_2_DEST,
            seq = "seq.human_walk_logbalance",
            synth = null,
        )) {
            return
        }
        awardObstacle(course, 3, RellekkaRooftopCourse.GAP_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.hutHurdle(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You hop onto the hut.")
        climbTo(
            RellekkaRooftopCourse.GAP_3_DEST,
            RellekkaRooftopCourse.GAP_3_TICKS,
            "seq.human_jump_hurdle",
            "synth.HURDLE",
            face = loc.coords,
        )
        awardObstacle(course, 4, RellekkaRooftopCourse.GAP_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeLong() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            waypoints = RellekkaRooftopCourse.TIGHTROPE_3_WAYPOINTS,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        forcedWalk(RellekkaRooftopCourse.TIGHTROPE_3_EXIT, crossTiles = 1)
        awardObstacle(course, 5, RellekkaRooftopCourse.TIGHTROPE_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.fishPileJump() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(RellekkaRooftopCourse.FISH_MOUNT)
        spam("You jump into the pile of fish.")
        leapTo(
            dest = RellekkaRooftopCourse.FISH_DEST,
            busyTicks = RellekkaRooftopCourse.FISH_TICKS,
            seq = "seq.agility_shortcut_wall_jumpdown",
            synth = "synth.JUMP_AND_FALL",
            face = RellekkaRooftopCourse.FISH_DEST,
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
        )
        // TODO(diary): award 615 XP with the hard Fremennik Diary complete.
        awardObstacle(course, 6, RellekkaRooftopCourse.FISH_XP, xpMods)
    }
}
