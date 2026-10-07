package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.SeersVillageRooftopCourse
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SeersVillageRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        SeersVillageRooftopCourse.COURSE.copy(
            lapReward = marks.lapReward(SeersVillageRooftopCourse.MARKS)
        )

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_seers_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { wallClimb() }
        }
        onOpLoc1("loc.rooftops_seers_jump") {
            player.withAgilityAnimation(preserveOnCompletion = true) { firstGap(it.loc) }
        }
        onOpLoc1("loc.rooftops_seers_tightrope") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tightrope() }
        }
        onOpLoc1("loc.rooftops_seers_jump_1") {
            player.withAgilityAnimation(preserveOnCompletion = true) { jumpUpGap(it.loc) }
        }
        onOpLoc1("loc.rooftops_seers_jump_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { churchGap(it.loc) }
        }
        onOpLoc1("loc.rooftops_seers_leapdown") {
            player.withAgilityAnimation(preserveOnCompletion = true) { edgeJumpOff(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.wallClimb() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        seersWallClimb()
        awardObstacle(course, 0, SeersVillageRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.firstGap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (
            rollObstacleFail(
                course.levelReq,
                SeersVillageRooftopCourse.FAIL_LOW,
                SeersVillageRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            faceObstacle(loc.coords)
            resetAnim()
            anim("seq.human_longjump")
            soundSynth("synth.JUMP2")
            delay(SeersVillageRooftopCourse.GAP_1_FAIL_AIR_TICKS)
            resetAnim()
            fallAndDamage(
                SeersVillageRooftopCourse.GAP_1_FAIL_DEST,
                SeersVillageRooftopCourse.FAIL_DAMAGE_LOW,
                SeersVillageRooftopCourse.FAIL_DAMAGE_HIGH,
            )
            return
        }
        spam("You jump the gap.")
        player.blockRunEnergyRestore(5)
        faceObstacle(SeersVillageRooftopCourse.GAP_1_FACE)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(SeersVillageRooftopCourse.GAP_1_DROP, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(SeersVillageRooftopCourse.GAP_1_DEST, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        resetAnim()
        awardObstacle(course, 1, SeersVillageRooftopCourse.GAP_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightrope() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(SeersVillageRooftopCourse.TIGHTROPE_MOUNT)
        if (
            rollObstacleFail(
                course.levelReq,
                SeersVillageRooftopCourse.FAIL_LOW,
                SeersVillageRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                SeersVillageRooftopCourse.TIGHTROPE_FAIL_DEST,
                SeersVillageRooftopCourse.FAIL_DAMAGE_LOW,
                SeersVillageRooftopCourse.FAIL_DAMAGE_HIGH,
            )
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            mount = SeersVillageRooftopCourse.TIGHTROPE_MOUNT,
            dest = SeersVillageRooftopCourse.TIGHTROPE_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(3), BalanceSound(2, delayTicks = 5)),
        )) {
            return
        }
        awardObstacle(course, 2, SeersVillageRooftopCourse.TIGHTROPE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.jumpUpGap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump the gap.")
        player.blockRunEnergyRestore(4)
        faceObstacle(loc.coords)
        moveBeat(
            SeersVillageRooftopCourse.GAP_2_LAUNCH,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 0))
        delay(1)
        moveBeat(SeersVillageRooftopCourse.GAP_2_STEP_UP, "seq.agility_shortcut_wall_jump2")
        delay(1)
        glideTo(SeersVillageRooftopCourse.GAP_2_DEST, AgilityMovement(17, 26, 0))
        delay(1)
        resetAnim()
        awardObstacle(course, 3, SeersVillageRooftopCourse.GAP_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.churchGap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump the gap.")
        faceObstacle(loc.coords)
        climbTo(
            SeersVillageRooftopCourse.GAP_3_DEST,
            SeersVillageRooftopCourse.GAP_3_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 4, SeersVillageRooftopCourse.GAP_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.edgeJumpOff(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump off the edge.")
        faceObstacle(loc.coords)
        climbTo(
            SeersVillageRooftopCourse.EDGE_DEST,
            SeersVillageRooftopCourse.EDGE_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 5, SeersVillageRooftopCourse.EDGE_XP, xpMods)
    }
}

internal suspend fun ProtectedAccess.seersWallClimb() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(4)
    faceObstacleAngle(constants.em_face_north)
    spam("You climb up the wall...")
    animBeat("seq.human_climbing", delayClientCycles = 15)
    delay(2)
    spam("...jump, and grab hold of the sign!")
    telejump(SeersVillageRooftopCourse.WALL_CLIMB_SIGN, TeleportType.Exempt)
    animBeat("seq.agilityarena_handholds_middle")
    delay(2)
    telejump(SeersVillageRooftopCourse.WALL_CLIMB_DEST, TeleportType.Exempt)
    resetAnim()
}
