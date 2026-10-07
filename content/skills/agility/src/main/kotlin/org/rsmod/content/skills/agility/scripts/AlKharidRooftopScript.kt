package org.rsmod.content.skills.agility.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AlKharidRooftopCourse
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AlKharidRooftopScript
@Inject
constructor(
    private val xpMods: XpModifiers,
    marks: MarksOfGrace,
    private val npcList: NpcList,
) : PluginScript() {
    private val course =
        AlKharidRooftopCourse.COURSE.copy(lapReward = marks.lapReward(AlKharidRooftopCourse.MARKS))

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_kharid_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_kharid_tightrope_1") { tightropeFirst() }
        onOpLoc1("loc.rooftops_kharid_rope_swing") { cableSwing() }
        onOpLoc1("loc.rooftops_kharid_slide_side") { zipLine() }
        onApLoc1("loc.rooftops_kharid_bamboo_tree_top") { tropicalTree(it.loc) }
        onOpLoc1("loc.rooftops_kharid_wallclimb_2") { roofTopBeams() }
        onOpLoc1("loc.rooftops_kharid_tightrope_4") { tightropeSecond() }
        onOpLoc1("loc.rooftops_kharid_leapdown") { gapJump() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You climb the wall...")
        alKharidRoofClimb(AlKharidRooftopCourse.WALL_CLIMB_DEST, constants.em_face_south)
        awardObstacle(course, 0, AlKharidRooftopCourse.WALL_CLIMB_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeFirst() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        if (rollObstacleFail(course.levelReq, AlKharidRooftopCourse.FAIL_LOW, AlKharidRooftopCourse.FAIL_HIGH)) {
            mes("You lose your balance and fall!")
            resetLapProgress()
            stumbleAndFall(
                AlKharidRooftopCourse.TIGHTROPE_1_FAIL_DEST,
                AlKharidRooftopCourse.FAIL_DAMAGE_LOW,
                AlKharidRooftopCourse.FAIL_DAMAGE_HIGH,
            )
            return
        }
        spam("You cross the tightrope.")
        if (!balanceCross(
            mount = AlKharidRooftopCourse.TIGHTROPE_1_MOUNT,
            dest = AlKharidRooftopCourse.TIGHTROPE_1_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(6)),
        )) return
        awardObstacle(course, 1, AlKharidRooftopCourse.TIGHTROPE_1_XP, xpMods)
    }

    private suspend fun ProtectedAccess.cableSwing() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(3)
            faceObstacle(AlKharidRooftopCourse.CABLE_DEST)
            spam("You begin an almighty run-up...")
            moveBeat(AlKharidRooftopCourse.CABLE_RUNUP, "seq.ic_running", movement = AgilityMovement(0, 30, 1536))
            delay(1)
            spam("You gained enough momentum to swing to the other side!")
            moveBeat(AlKharidRooftopCourse.CABLE_DEST, "seq.human_ropeswing_long", movement = AgilityMovement(0, 60, 1536))
            awardObstacle(course, 2, AlKharidRooftopCourse.CABLE_XP, xpMods)
            delay(2)
            resetAnim()
        }
    }

    private suspend fun ProtectedAccess.zipLine() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        player.withAgilityAnimation {
            val failed = rollObstacleFail(
                course.levelReq,
                AlKharidRooftopCourse.FAIL_LOW,
                AlKharidRooftopCourse.FAIL_HIGH,
            )
            player.blockRunEnergyRestore(14)
            spam("You grab hold of the rope with your teeth and slide down.")
            animBeat("seq.agility_shortcut_wall_jumpdown", delayClientCycles = 10)
            delay(AlKharidRooftopCourse.ZIPLINE_MOUNT_TICKS)
            moveBeat(AlKharidRooftopCourse.ZIPLINE_MOUNT, "seq.zipline_bite", "synth.BITE")
            if (!failed) soundSynth("synth.DEATHSLIDE_FIRST", delay = 15)
            delay(AlKharidRooftopCourse.ZIPLINE_BITE_TICKS)
            if (failed) {
                mes("You lose your grip and plummet to the ground!")
                resetLapProgress()
                resetAnim()
                fallAndDamage(
                    AlKharidRooftopCourse.ZIPLINE_FAIL_DEST,
                    AlKharidRooftopCourse.FAIL_DAMAGE_LOW,
                    AlKharidRooftopCourse.FAIL_DAMAGE_HIGH,
                )
                return@withAgilityAnimation
            }
            animBeat("seq.zipline_slide")
            repeat(AlKharidRooftopCourse.ZIPLINE_SLIDE_TILES) { step ->
                val current = player.coords
                val next = CoordGrid(current.x + 1, current.z, current.level)
                glideTo(next, AgilityMovement(0, if (step == AlKharidRooftopCourse.ZIPLINE_SLIDE_TILES - 1) 15 else CLIENT_CYCLES_PER_TICK, 1536))
                delay(1)
            }
            resetAnim()
            forcedWalk(AlKharidRooftopCourse.ZIPLINE_DEST, crossTiles = 1)
            awardObstacle(course, 3, AlKharidRooftopCourse.ZIPLINE_XP, xpMods)
        }
    }

    private suspend fun ProtectedAccess.tropicalTree(loc: BoundLocInfo) {
        if (!isWithinApRange(loc, distance = AlKharidRooftopCourse.TREE_AP_DISTANCE)) return
        if (player.coords.level != AlKharidRooftopCourse.TREE_MOUNT.level) return
        arriveDelay()
        if (!meetsCourseReq(course)) return
        var runUp = 0
        while (player.routeDestination.isNotEmpty() && runUp < TREE_RUNUP_TIMEOUT_TICKS) {
            delay(1)
            runUp++
        }
        if (!approachMount(AlKharidRooftopCourse.TREE_MOUNT)) return
        spam("You swing across the tree.")
        alKharidTreeSwing()
        awardObstacle(course, 4, AlKharidRooftopCourse.TREE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.roofTopBeams() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You climb the beams.")
        alKharidRoofClimb(AlKharidRooftopCourse.BEAMS_DEST, constants.em_face_north)
        awardObstacle(course, 5, AlKharidRooftopCourse.BEAMS_XP, xpMods)
    }

    private suspend fun ProtectedAccess.tightropeSecond() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You cross the tightrope.")
        if (!balanceCross(
            mount = AlKharidRooftopCourse.TIGHTROPE_2_MOUNT,
            dest = AlKharidRooftopCourse.TIGHTROPE_2_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
            sounds = listOf(BalanceSound(6)),
        )) return
        awardObstacle(course, 6, AlKharidRooftopCourse.TIGHTROPE_2_XP, xpMods)
        forcedWalk(AlKharidRooftopCourse.TIGHTROPE_2_EXIT, crossTiles = 1)
    }

    private suspend fun ProtectedAccess.gapJump() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        spam("You jump the gap.")
        player.withAgilityAnimation {
            val bigRedJapan = findBigRedJapan()
            try {
                bigRedJapan?.apply {
                    facePlayer(player)
                    resetAnim()
                    anim("seq.emote_wave")
                }
                player.blockRunEnergyRestore(1)
                faceObstacle(AlKharidRooftopCourse.GAP_DEST)
                animBeat("seq.agility_shortcut_wall_jumpdown")
                soundSynth("synth.JUMP2", delay = 15)
                delay(1)
                telejump(AlKharidRooftopCourse.GAP_DEST, TeleportType.Exempt)
                resetAnim()
                awardObstacle(course, 7, AlKharidRooftopCourse.GAP_XP, xpMods)
            } finally {
                bigRedJapan?.apply {
                    resetFaceEntity()
                    faceDirection(respawnDir)
                }
            }
        }
    }

    private fun ProtectedAccess.findBigRedJapan(): Npc? {
        val npcId = "npc.rooftops_bigredjapan".asRSCM(RSCMType.NPC)
        return npcList
            .filter { it.id == npcId && it.coords.level == player.coords.level }
            .minByOrNull { it.coords.chebyshevDistance(player.coords) }
            ?.takeIf { it.coords.chebyshevDistance(player.coords) <= WAVE_RADIUS }
    }

    private companion object {
        const val WAVE_RADIUS = 10
        const val TREE_RUNUP_TIMEOUT_TICKS = 10
    }
}

internal suspend fun ProtectedAccess.alKharidRoofClimb(dest: CoordGrid, faceAngle: Int) = player.withAgilityAnimation {
    player.blockRunEnergyRestore(2)
    faceObstacleAngle(faceAngle)
    animBeat("seq.human_reachforladder", delayClientCycles = 15)
    delay(2)
    telejump(dest, TeleportType.Exempt)
    resetAnim()
}

internal suspend fun ProtectedAccess.alKharidTreeSwing() = player.withAgilityAnimation {
    player.blockRunEnergyRestore(AlKharidRooftopCourse.TREE_TICKS)
    faceObstacle(AlKharidRooftopCourse.TREE_FIRST_HOLD)
    moveBeat(AlKharidRooftopCourse.TREE_FIRST_HOLD, "seq.agility_shortcut_wall_jump", movement = AgilityMovement(35, 49, 1024))
    soundSynth("synth.JUMP_UP", delay = 10)
    delay(2)
    moveBeat(AlKharidRooftopCourse.TREE_MIDDLE_HOLD, "seq.agilityarena_handholds_middlereverse", movement = AgilityMovement(34, 42, 1536))
    soundSynth("synth.HANDHOLDS_LOOP", delay = 35)
    delay(2)
    moveBeat(AlKharidRooftopCourse.TREE_LAST_HOLD, "seq.agilityarena_handholds_lastreverse", movement = AgilityMovement(34, 52, 1024))
    soundSynth("synth.HANDHOLDS_LOOP", delay = 35)
    delay(2)
    moveBeat(AlKharidRooftopCourse.TREE_DEST, "seq.agility_shortcut_wall_jumpdown2", "synth.FALL_LAND")
    delay(1)
    resetAnim()
}
