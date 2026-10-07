package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.ShayzienBasicCourse
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ShayzienBasicScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    private val course = ShayzienBasicCourse.COURSE

    override fun ScriptContext.startup() {
        onOpLoc1("loc.shayzien_agility_both_start_ladder") { startLadder() }
        onOpLoc1("loc.shayzien_agility_both_rope_climb") { ropeClimb() }
        onOpLoc1("loc.shayzien_agility_both_rope_walk") { ropeWalk1() }
        onOpLoc1("loc.shayzien_agility_low_bar_climb") { barClimb() }
        onOpLoc1("loc.shayzien_agility_low_rope_walk_1") { ropeWalk2() }
        onOpLoc1("loc.shayzien_agility_low_rope_walk_2") { ropeWalk3() }
        onOpLoc1("loc.shayzien_agility_low_end_jump") { endJump() }
    }

    private suspend fun ProtectedAccess.startLadder() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the ladder.")
        climbTo(
            ShayzienBasicCourse.LADDER_DEST,
            ShayzienBasicCourse.LADDER_TICKS,
            "seq.human_climbing",
            "synth.CLIMB_WALL",
        )
        awardObstacle(course, 0, ShayzienBasicCourse.LADDER_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ropeClimb() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb across the monkeybars.")
        climbTo(
            ShayzienBasicCourse.ROPE_CLIMB_DEST,
            ShayzienBasicCourse.ROPE_CLIMB_TICKS,
            "seq.human_climbing",
            "synth.CLIMB_WALL",
        )
        awardObstacle(course, 1, ShayzienBasicCourse.ROPE_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ropeWalk1() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the rope.")
        if (!balanceCross(
            mount = ShayzienBasicCourse.ROPE_WALK_1_MOUNT,
            dest = ShayzienBasicCourse.ROPE_WALK_1_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        awardObstacle(course, 2, ShayzienBasicCourse.ROPE_WALK_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.barClimb() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the bar.")
        climbTo(
            ShayzienBasicCourse.BAR_CLIMB_DEST,
            ShayzienBasicCourse.BAR_CLIMB_TICKS,
            "seq.human_climbing",
            "synth.CLIMB_WALL",
        )
        awardObstacle(course, 3, ShayzienBasicCourse.BAR_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ropeWalk2() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the rope.")
        if (!balanceCross(
            mount = ShayzienBasicCourse.ROPE_WALK_2_MOUNT,
            dest = ShayzienBasicCourse.ROPE_WALK_2_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        spotHop(ShayzienBasicCourse.ROPE_WALK_2_DROP)
        awardObstacle(course, 4, ShayzienBasicCourse.ROPE_WALK_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ropeWalk3() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the rope.")
        if (!balanceCross(
            mount = ShayzienBasicCourse.ROPE_WALK_3_MOUNT,
            dest = ShayzienBasicCourse.ROPE_WALK_3_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        awardObstacle(course, 5, ShayzienBasicCourse.ROPE_WALK_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.endJump() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump down.")
        leapTo(
            dest = ShayzienBasicCourse.END_JUMP_DEST,
            busyTicks = ShayzienBasicCourse.END_JUMP_TICKS,
            seq = "seq.agility_shortcut_wall_jumpdown",
            synth = "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
        )
        awardObstacle(course, 6, ShayzienBasicCourse.END_JUMP_XP, xpMods)
    }
}
