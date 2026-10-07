package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.PollnivneachRooftopCourse
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PollnivneachRooftopScript
@Inject
constructor(private val xpMods: XpModifiers, marks: MarksOfGrace) : PluginScript() {

    private val course =
        PollnivneachRooftopCourse.COURSE.copy(
            lapReward = marks.lapReward(PollnivneachRooftopCourse.MARKS)
        )

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_pollnivneach_basket") {
            player.withAgilityAnimation(preserveOnCompletion = true) { basketClimb(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_marketstall") {
            player.withAgilityAnimation(preserveOnCompletion = true) { marketStall(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_hangingbanner") {
            player.withAgilityAnimation(preserveOnCompletion = true) { bannerSwing(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_gap") {
            player.withAgilityAnimation(preserveOnCompletion = true) { gapLeap(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_tree") {
            player.withAgilityAnimation(preserveOnCompletion = true) { treeJump(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { roughWall() }
        }
        onOpLoc1("loc.rooftops_pollnivneach_monkeybars_start") {
            player.withAgilityAnimation(preserveOnCompletion = true) { monkeyBars() }
        }
        onOpLoc1("loc.rooftops_pollnivneach_treetop") {
            player.withAgilityAnimation(preserveOnCompletion = true) { treetopJump(it.loc) }
        }
        onOpLoc1("loc.rooftops_pollnivneach_line") {
            player.withAgilityAnimation(preserveOnCompletion = true) { dryingLineJump(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.basketClimb(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the basket...")
        player.blockRunEnergyRestore(3)
        faceObstacle(loc.coords)
        moveBeat(
            PollnivneachRooftopCourse.BASKET_TOP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 1024))
        delay(1)
        animBeat("seq.agility_shortcut_wall_jump", "synth.JUMP_UP", delayClientCycles = 20)
        delay(1)
        moveBeat(PollnivneachRooftopCourse.BASKET_DEST, "seq.agility_shortcut_wall_jumpdown2")
        delay(1)
        resetAnim()
        awardObstacle(course, 0, PollnivneachRooftopCourse.BASKET_XP, xpMods)
    }

    private suspend fun ProtectedAccess.marketStall(loc: BoundLocInfo) {
        arriveDelay()
        if (
            rollObstacleFail(
                course.levelReq,
                PollnivneachRooftopCourse.STALL_FAIL_LOW,
                PollnivneachRooftopCourse.STALL_FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            val damage =
                player.hitpoints / PollnivneachRooftopCourse.STALL_DAMAGE_HP_DIVISOR +
                    PollnivneachRooftopCourse.STALL_DAMAGE_BASE
            stumbleAndFall(PollnivneachRooftopCourse.STALL_FAIL_DEST, damage, damage)
            return
        }
        spam("You jump onto the market stall.")
        player.blockRunEnergyRestore(6)
        moveBeat(PollnivneachRooftopCourse.STALL_STEP, "seq.ic_running", animDelayClientCycles = 15, movement = AgilityMovement(15, 29, 1024, visualStart = CoordGrid(3349, 2967, 1)))
        delay(1)
        moveBeat(PollnivneachRooftopCourse.STALL_TOP, "seq.human_jump_hurdle", "synth.HURDLE", movement = AgilityMovement(8, 50, 1024, visualEnd = CoordGrid(3350, 2970, 1)))
        delay(2)
        moveBeat(
            PollnivneachRooftopCourse.STALL_JUMP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP", movement = AgilityMovement(25, 59, 1024))
        delay(2)
        moveBeat(
            PollnivneachRooftopCourse.STALL_DEST,
            "seq.agility_shortcut_wall_jumpdown2",
            "synth.FALL_LAND",
        )
        delay(1)
        resetAnim()
        awardObstacle(course, 1, PollnivneachRooftopCourse.STALL_XP, xpMods)
    }

    private suspend fun ProtectedAccess.bannerSwing(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You grab the banner and swing across.")
        player.blockRunEnergyRestore(6)
        faceObstacle(loc.coords)
        animBeat("seq.ic_running", delayClientCycles = 15)
        delay(1)
        moveBeat(
            PollnivneachRooftopCourse.BANNER_LEAP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 10, movement = AgilityMovement(35, 59, 1024))
        delay(2)
        moveBeat(
            PollnivneachRooftopCourse.BANNER_GRAB,
            "seq.agilityarena_handholds_middle",
            "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 59, 1024), soundDelayClientCycles = 35)
        delay(2)
        moveBeat(
            PollnivneachRooftopCourse.BANNER_DEST,
            "seq.agility_shortcut_wall_jumpdown2",
            "synth.FALL_LAND",
        )
        delay(1)
        resetAnim()
        awardObstacle(course, 2, PollnivneachRooftopCourse.BANNER_XP, xpMods)
    }

    private suspend fun ProtectedAccess.gapLeap(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        player.blockRunEnergyRestore(3)
        faceObstacle(loc.coords)
        moveBeat(
            PollnivneachRooftopCourse.GAP_LEAP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 15, movement = AgilityMovement(40, 49, 1536))
        delay(2)
        moveBeat(PollnivneachRooftopCourse.GAP_DEST, "seq.agility_shortcut_wall_jump2", movement = AgilityMovement(25, 30, 1536))
        delay(1)
        resetAnim()
        awardObstacle(course, 3, PollnivneachRooftopCourse.GAP_XP, xpMods)
    }

    private suspend fun ProtectedAccess.treeJump(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump to the tree.")
        pollnivneachTreeCross()
        awardObstacle(course, 4, PollnivneachRooftopCourse.TREE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the wall...")
        player.blockRunEnergyRestore(PollnivneachRooftopCourse.WALL_CLIMB_TICKS)
        faceObstacleAngle(constants.em_face_north)
        animBeat("seq.human_reachforladder", delayClientCycles = 15)
        delay(PollnivneachRooftopCourse.WALL_CLIMB_TICKS)
        telejump(PollnivneachRooftopCourse.WALL_CLIMB_DEST, TeleportType.Exempt)
        resetAnim()
        awardObstacle(course, 5, PollnivneachRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.monkeyBars() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You cross the monkey bars.")
        if (!pollnivneachMonkeyBars()) return
        awardObstacle(course, 6, PollnivneachRooftopCourse.MONKEYBARS_XP, xpMods)
    }

    private suspend fun ProtectedAccess.treetopJump(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump onto the tree.")
        player.blockRunEnergyRestore(4)
        faceObstacle(loc.coords)
        moveBeat(PollnivneachRooftopCourse.TREETOP_HOP, "seq.human_jump_hurdle", "synth.HURDLE", movement = AgilityMovement(8, 50, 1024))
        delay(2)
        moveBeat(
            PollnivneachRooftopCourse.TREETOP_LAND,
            "seq.human_jump_hurdle",
            "synth.HURDLE",
            animDelayClientCycles = 10, movement = AgilityMovement(18, 60, 1024))
        delay(2)
        telejump(PollnivneachRooftopCourse.TREETOP_DEST, TeleportType.Exempt)
        resetAnim()
        awardObstacle(course, 7, PollnivneachRooftopCourse.TREETOP_XP, xpMods)
    }

    private suspend fun ProtectedAccess.dryingLineJump(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump off the edge.")
        player.blockRunEnergyRestore(4)
        faceObstacle(loc.coords)
        moveBeat(PollnivneachRooftopCourse.LINE_HOP, "seq.human_spot_jump", "synth.JUMP", movement = AgilityMovement(15, 45, 0))
        delay(2)
        animBeat("seq.agility_shortcut_wall_jumpdown", "synth.JUMP2", delayClientCycles = 15)
        delay(1)
        moveBeat(PollnivneachRooftopCourse.LINE_DEST, "seq.agility_shortcut_wall_jumpdown2")
        val xp =
            if (vars["varbit.desert_diary_hard_complete"] == 1) {
                PollnivneachRooftopCourse.LINE_XP_DESERT_HARD
            } else {
                PollnivneachRooftopCourse.LINE_XP
            }
        awardObstacle(course, 8, xp, xpMods)
        delay(1)
        resetAnim()
    }
}

internal suspend fun ProtectedAccess.pollnivneachMonkeyBars(): Boolean {
    if (!approachMount(PollnivneachRooftopCourse.MONKEYBARS_APPROACH)) return false
    player.withAgilityAnimation {
        player.blockRunEnergyRestore(9)
        withBalancePose("seq.human_monkeybars_walk", readySeq = "seq.human_monkeybars_ready") {
            animBeat("seq.human_monkeybars_on", "synth.MONKEYBARS_ON")
            soundSynth("synth.MONKEYBARS_LOOP", loops = PollnivneachRooftopCourse.MONKEYBARS_LOOP_COUNT, delay = 40)
            forcedWalk(PollnivneachRooftopCourse.MONKEYBARS_MOUNT, crossTiles = 1)
            resetAnim()
            forcedWalk(PollnivneachRooftopCourse.MONKEYBARS_DEST, crossTiles = 6)
            delay(1)
        }
        animBeat("seq.human_monkeybars_off", "synth.MONKEYBARS_OFF")
        delay(1)
        resetAnim()
    }
    return true
}

internal suspend fun ProtectedAccess.pollnivneachTreeCross() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(7)
    faceObstacle(PollnivneachRooftopCourse.TREE_JUMP)
    moveBeat(
        PollnivneachRooftopCourse.TREE_JUMP,
        "seq.agility_shortcut_wall_jump",
        "synth.JUMP_UP",
        animDelayClientCycles = 10, movement = AgilityMovement(35, 49, 1024))
    delay(2)
    moveBeat(
        PollnivneachRooftopCourse.TREE_RUNG,
        "seq.agilityarena_handholds_middlereverse",
        "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 52, 1536), soundDelayClientCycles = 35)
    delay(2)
    moveBeat(
        PollnivneachRooftopCourse.TREE_LAST_RUNG,
        "seq.agilityarena_handholds_lastreverse",
        "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 52, 1536), soundDelayClientCycles = 35)
    delay(2)
    moveBeat(
        PollnivneachRooftopCourse.TREE_DEST,
        "seq.agility_shortcut_wall_jumpdown2",
        "synth.FALL_LAND",
    )
    delay(1)
    resetAnim()
}
