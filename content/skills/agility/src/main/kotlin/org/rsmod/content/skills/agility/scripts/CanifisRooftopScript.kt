package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.CanifisRooftopCourse
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CanifisRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        CanifisRooftopCourse.COURSE.copy(lapReward = marks.lapReward(CanifisRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_canifis_start_tree") {
            player.withAgilityAnimation(preserveOnCompletion = true) { tallTree(it.loc) }
        }
        onOpLoc1("loc.rooftops_canifis_jump") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapOne() }
        }
        onOpLoc1("loc.rooftops_canifis_jump_2") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapTwo() }
        }
        onOpLoc1("loc.rooftops_canifis_jump_5") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapThree(it.loc) }
        }
        onOpLoc1("loc.rooftops_canifis_jump_3") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFour() }
        }
        onOpLoc1("loc.rooftops_canifis_polevault") {
            player.withAgilityAnimation(preserveOnCompletion = true) { poleVault() }
        }
        onOpLoc1("loc.rooftops_canifis_jump_4") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapFive() }
        }
        onOpLoc1("loc.rooftops_canifis_leapdown") {
            player.withAgilityAnimation(preserveOnCompletion = true) { bankLeap() }
        }
    }

    private suspend fun ProtectedAccess.tallTree(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(CanifisRooftopCourse.TREE_MOUNT)
        spam("You climb the tree...")
        canifisTreeClimb()
        awardObstacle(course, 0, CanifisRooftopCourse.TREE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapOne() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        player.blockRunEnergyRestore(3)
        moveBeat(CanifisRooftopCourse.GAP_1_EDGE, "seq.ic_running", animDelayClientCycles = 20, movement = AgilityMovement(20, 40, 1024))
        delay(1)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(CanifisRooftopCourse.GAP_1_DEST, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        resetAnim()
        awardObstacle(course, 1, CanifisRooftopCourse.GAP_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapTwo() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        player.blockRunEnergyRestore(3)
        faceObstacle(CanifisRooftopCourse.GAP_2_LAND)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(CanifisRooftopCourse.GAP_2_LAND, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        resetAnim()
        forcedWalk(CanifisRooftopCourse.GAP_2_DEST, 1)
        awardObstacle(course, 2, CanifisRooftopCourse.GAP_2_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapThree(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (
            rollObstacleFail(
                course.levelReq,
                CanifisRooftopCourse.GAP_3_FAIL_LOW,
                CanifisRooftopCourse.GAP_3_FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            faceObstacle(loc.coords)
            resetAnim()
            anim("seq.human_longjump")
            soundSynth("synth.JUMP2")
            delay(CanifisRooftopCourse.GAP_3_FAIL_ANIM_TICKS)
            resetAnim()
            fallAndDamage(
                CanifisRooftopCourse.GAP_3_FAIL_DEST,
                CanifisRooftopCourse.GAP_3_DAMAGE_LOW,
                CanifisRooftopCourse.GAP_3_DAMAGE_HIGH,
            )
            return
        }
        spam("You leap across the gap.")
        player.blockRunEnergyRestore(4)
        moveBeat(
            CanifisRooftopCourse.GAP_3_LAND,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 512))
        delay(1)
        animBeat("seq.agility_shortcut_wall_jump2")
        delay(1)
        glideTo(CanifisRooftopCourse.GAP_3_STEP, AgilityMovement(17, 26, 512))
        delay(1)
        telejump(CanifisRooftopCourse.GAP_3_DEST, TeleportType.Exempt)
        resetAnim()
        awardObstacle(course, 3, CanifisRooftopCourse.GAP_3_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapFour() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        climbTo(
            CanifisRooftopCourse.GAP_4_DEST,
            CanifisRooftopCourse.GAP_4_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 4, CanifisRooftopCourse.GAP_4_XP, xpMods)
    }

    private suspend fun ProtectedAccess.poleVault() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You vault over the gap.")
        player.blockRunEnergyRestore(9)
        moveBeat(CanifisRooftopCourse.POLEVAULT_RUNUP, "seq.ic_running", movement = AgilityMovement(0, 30, 1536, visualStart = CoordGrid(3478, 3486, 2), visualEnd = CoordGrid(3480, 3484, 2)))
        delay(1)
        moveBeat(CanifisRooftopCourse.POLEVAULT_START, "seq.rooftops_pole_vault", movement = AgilityMovement(0, 30, 1536, visualEnd = CoordGrid(3480, 3483, 2)))
        delay(1)
        for (tile in CanifisRooftopCourse.POLEVAULT_GLIDE) {
            glideTo(tile, AgilityMovement(0, if (tile == CanifisRooftopCourse.POLEVAULT_GLIDE.last()) 28 else 30, 1536))
            delay(1)
        }
        moveBeat(CanifisRooftopCourse.POLEVAULT_DEST, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        resetAnim()
        awardObstacle(course, 5, CanifisRooftopCourse.POLEVAULT_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapFive() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        climbTo(
            CanifisRooftopCourse.GAP_5_DEST,
            CanifisRooftopCourse.GAP_5_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 6, CanifisRooftopCourse.GAP_5_XP, xpMods)
    }

    private suspend fun ProtectedAccess.bankLeap() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump off the edge.")
        climbTo(
            CanifisRooftopCourse.FINISH_DEST,
            CanifisRooftopCourse.FINISH_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 7, CanifisRooftopCourse.FINISH_XP, xpMods)
    }
}

internal suspend fun ProtectedAccess.canifisTreeClimb() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(CanifisRooftopCourse.TREE_TICKS)
    moveBeat(
        CoordGrid(3507, 3489, 0),
        "seq.mdaughter_tree_climb_combi",
        "synth.TREE_CLIMBING",
        animDelayClientCycles = 60,
        movement = AgilityMovement(30, 60, 1024, visualEnd = CoordGrid(3505, 3489, 0)),
    )
    delay(2)
    glideTo(CoordGrid(3505, 3489, 0), AgilityMovement(60, 115, 1536))
    delay(4)
    telejump(CanifisRooftopCourse.TREE_DEST, TeleportType.Exempt)
    resetAnim()
}
