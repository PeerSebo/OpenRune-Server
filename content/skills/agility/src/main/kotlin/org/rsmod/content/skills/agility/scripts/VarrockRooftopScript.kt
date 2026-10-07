package org.rsmod.content.skills.agility.scripts

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.VarrockRooftopCourse
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockRooftopScript
@Inject
constructor(
    private val xpMods: XpModifiers,
    marks: MarksOfGrace,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val course =
        VarrockRooftopCourse.COURSE.copy(lapReward = marks.lapReward(VarrockRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_varrock_wallclimb") {
            player.withAgilityAnimation(preserveOnCompletion = true) { roughWall() }
        }
        onOpLoc1("loc.rooftops_varrock_clothesline") {
            player.withAgilityAnimation(preserveOnCompletion = true) { clothesLine() }
        }
        onOpLoc1("loc.rooftops_varrock_leaptoruins") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ruinsLeap() }
        }
        onApLoc1("loc.rooftops_varrock_wallswing") {
            player.withAgilityAnimation(preserveOnCompletion = true) { wallBalance(it.loc) }
        }
        onOpLoc1("loc.rooftops_varrock_wallscramble") {
            player.withAgilityAnimation(preserveOnCompletion = true) { scrambleLeap() }
        }
        onOpLoc1("loc.rooftops_varrock_leaptobalcony") {
            player.withAgilityAnimation(preserveOnCompletion = true) { balconyLeap() }
        }
        onOpLoc1("loc.rooftops_varrock_leapdown") {
            player.withAgilityAnimation(preserveOnCompletion = true) { leapDown() }
        }
        onOpLoc1("loc.rooftops_varrock_stepuproof") {
            player.withAgilityAnimation(preserveOnCompletion = true) { ledgeHurdle() }
        }
        onOpLoc1("loc.rooftops_varrock_finish") {
            player.withAgilityAnimation(preserveOnCompletion = true) { edgeJumpOff() }
        }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb the wall...")
        varrockRoughWall()
        awardObstacle(course, 0, VarrockRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.clothesLine() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(VarrockRooftopCourse.CLOTHESLINE_MOUNT)
        if (
            rollObstacleFail(
                course.levelReq,
                VarrockRooftopCourse.FAIL_LOW,
                VarrockRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                VarrockRooftopCourse.CLOTHESLINE_FAIL_DEST,
                VarrockRooftopCourse.CLOTHESLINE_DAMAGE_LOW,
                VarrockRooftopCourse.CLOTHESLINE_DAMAGE_HIGH,
            )
            return
        }
        spam("You cross the clothes line.")
        varrockClothesLineCross { startlePigeon() }
        awardObstacle(course, 1, VarrockRooftopCourse.CLOTHESLINE_XP, xpMods)
    }

    private fun ProtectedAccess.startlePigeon() {
        if (player.staffInvisible) {
            soundSynth("synth.QUACK")
            return
        }
        val perch = CoordGrid(3211, 3414, 3)
        // The pigeon is scenery; a short-lived invisible NPC carries its overhead text.
        val voice = Npc("npc.desert_treasure_invisible_npc", perch)
        npcRepo.add(voice, duration = 2)
        voice.mode = NpcMode.None
        voice.say("Squawk!")
        worldRepo.soundArea(perch, "synth.QUACK", radius = 5)
    }

    private suspend fun ProtectedAccess.ruinsLeap() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        climbTo(
            VarrockRooftopCourse.RUINS_DEST,
            VarrockRooftopCourse.RUINS_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 2, VarrockRooftopCourse.RUINS_XP, xpMods)
    }

    private suspend fun ProtectedAccess.wallBalance(loc: BoundLocInfo) {
        if (!isWithinApRange(loc, distance = VarrockRooftopCourse.WALL_SWING_AP_DISTANCE)) {
            return
        }
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(VarrockRooftopCourse.WALL_SWING_MOUNT)
        if (
            rollObstacleFail(
                course.levelReq,
                VarrockRooftopCourse.FAIL_LOW,
                VarrockRooftopCourse.FAIL_HIGH,
            )
        ) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                VarrockRooftopCourse.WALL_SWING_FAIL_DEST,
                VarrockRooftopCourse.WALL_SWING_DAMAGE_LOW,
                VarrockRooftopCourse.WALL_SWING_DAMAGE_HIGH,
            )
            return
        }
        spam("You balance across the wall.")
        player.blockRunEnergyRestore(13)
        moveBeat(VarrockRooftopCourse.WALL_SWING_STEP, "seq.ic_running", animDelayClientCycles = 15, movement = AgilityMovement(15, 45, 512))
        delay(1)
        moveBeat(
            VarrockRooftopCourse.WALL_SWING_TOP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 512))
        delay(1)
        for (rung in VarrockRooftopCourse.WALL_SWING_RUNGS) {
            moveBeat(rung, "seq.agilityarena_handholds_middlereverse", "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 52, 512), soundDelayClientCycles = 35)
            delay(2)
        }
        moveBeat(
            VarrockRooftopCourse.WALL_SWING_LAST_RUNG,
            "seq.agilityarena_handholds_lastreverse",
            "synth.HANDHOLDS_LOOP", movement = AgilityMovement(34, 52, 512), soundDelayClientCycles = 35)
        delay(2)
        varrockWallSidestep()
        moveBeat(VarrockRooftopCourse.WALL_SWING_HOP_OFF, "seq.human_spot_jump", "synth.JUMP_UP", movement = AgilityMovement(5, 30, 1536))
        delay(1)
        telejump(VarrockRooftopCourse.WALL_SWING_DEST, TeleportType.Exempt)
        resetAnim()
        delay(1)
        awardObstacle(course, 3, VarrockRooftopCourse.WALL_SWING_XP, xpMods)
    }

    private suspend fun ProtectedAccess.scrambleLeap() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(VarrockRooftopCourse.SCRAMBLE_MOUNT)
        spam("You leap across the gap.")
        player.blockRunEnergyRestore(3)
        faceObstacle(VarrockRooftopCourse.SCRAMBLE_LAUNCH)
        moveBeat(
            VarrockRooftopCourse.SCRAMBLE_LAUNCH,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP",
            animDelayClientCycles = 20, movement = AgilityMovement(25, 30, 0))
        delay(1)
        animBeat("seq.agility_shortcut_wall_jump2")
        delay(1)
        glideTo(VarrockRooftopCourse.SCRAMBLE_DEST, AgilityMovement(17, 26, 0))
        delay(1)
        resetAnim()
        awardObstacle(course, 4, VarrockRooftopCourse.SCRAMBLE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.balconyLeap() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        approachMount(VarrockRooftopCourse.BALCONY_MOUNT)
        spam("You leap across to the balcony.")
        player.blockRunEnergyRestore(5)
        faceObstacle(VarrockRooftopCourse.BALCONY_LEAP)
        animBeat("seq.ic_running")
        delay(1)
        moveBeat(
            VarrockRooftopCourse.BALCONY_LEAP,
            "seq.seaslug_ken_leap",
            "synth.JUMP",
            animDelayClientCycles = 15, movement = AgilityMovement(20, 50, 1536))
        delay(2)
        moveBeat(
            VarrockRooftopCourse.BALCONY_JUMP,
            "seq.agility_shortcut_wall_jump",
            "synth.JUMP_UP", movement = AgilityMovement(5, 10, 1536))
        delay(1)
        moveBeat(VarrockRooftopCourse.BALCONY_DEST, "seq.agility_shortcut_wall_jump2", movement = AgilityMovement(18, 27, 1536))
        delay(1)
        resetAnim()
        awardObstacle(course, 5, VarrockRooftopCourse.BALCONY_XP, xpMods)
    }

    private suspend fun ProtectedAccess.leapDown() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You leap across the gap.")
        climbTo(
            VarrockRooftopCourse.LEAPDOWN_DEST,
            VarrockRooftopCourse.LEAPDOWN_TICKS,
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
            animDelayClientCycles = 15,
        )
        awardObstacle(course, 6, VarrockRooftopCourse.LEAPDOWN_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ledgeHurdle() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You hurdle the ledge.")
        player.blockRunEnergyRestore(2)
        faceObstacle(VarrockRooftopCourse.HURDLE_DEST)
        moveBeat(VarrockRooftopCourse.HURDLE_DEST, "seq.human_jump_hurdle", "synth.HURDLE", movement = AgilityMovement(8, 50, 1024))
        delay(2)
        resetAnim()
        awardObstacle(course, 7, VarrockRooftopCourse.HURDLE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.edgeJumpOff() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You jump off the edge.")
        player.blockRunEnergyRestore(3)
        faceObstacle(VarrockRooftopCourse.FINISH_LEDGE)
        moveBeat(VarrockRooftopCourse.FINISH_LEDGE, "seq.human_spot_jump", "synth.JUMP", movement = AgilityMovement(15, 30, 1024))
        delay(1)
        animBeat(
            "seq.agility_shortcut_wall_jumpdown",
            "synth.JUMP2",
            delayClientCycles = 15,
        )
        delay(1)
        moveBeat(VarrockRooftopCourse.FINISH_DEST, "seq.agility_shortcut_wall_jumpdown2")
        awardObstacle(course, 8, VarrockRooftopCourse.FINISH_XP, xpMods)
        delay(1)
        resetAnim()
    }
}

internal suspend fun ProtectedAccess.varrockWallSidestep() {
    withBalancePose("seq.human_walk_sidestepl") {
        animBeat("seq.human_into_sidestepl")
        soundSynth("synth.BALANCING_LEDGE", loops = 2, delay = CLIENT_CYCLES_PER_TICK)
        soundSynth("synth.BALANCING_LEDGE", loops = 2, delay = 2 * CLIENT_CYCLES_PER_TICK)
        forcedWalk(VarrockRooftopCourse.WALL_SWING_WALK_END, crossTiles = 2)
        delay(1)
    }
}

internal suspend fun ProtectedAccess.varrockClothesLineCross(onPigeon: () -> Unit = {}) = player.withAgilityAnimation {
    val hops = VarrockRooftopCourse.CLOTHESLINE_HOPS
    faceObstacle(hops[0])
    delay(1)
    player.blockRunEnergyRestore(5)
    moveBeat(hops[0], "seq.human_spot_jump", "synth.JUMP", movement = AgilityMovement(15, 30, 512))
    delay(1)
    moveBeat(hops[1], "seq.human_spot_jump", "synth.JUMP", animDelayClientCycles = 15, movement = AgilityMovement(30, 45, 512))
    delay(1)
    onPigeon()
    delay(1)
    moveBeat(hops[2], "seq.human_spot_jump", "synth.JUMP", movement = AgilityMovement(15, 30, 512))
    delay(2)
    resetAnim()
}

internal suspend fun ProtectedAccess.varrockRoughWall() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(4)
    faceObstacleAngle(constants.em_face_west)
    animBeat("seq.human_reachforladder", delayClientCycles = 15)
    delay(2)
    moveBeat(VarrockRooftopCourse.WALL_CLIMB_STEP, "seq.agility_shortcut_wall_jump2", "synth.JUMP_UP")
    delay(1)
    glideTo(VarrockRooftopCourse.WALL_CLIMB_DEST, AgilityMovement(17, 26, 512))
    delay(1)
    resetAnim()
}
