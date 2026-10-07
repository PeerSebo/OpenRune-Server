package org.rsmod.content.skills.agility.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.withoutRunEnergyRestore
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.GnomeStrongholdCourse
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GnomeStrongholdScript
@Inject
constructor(
    private val xpMods: XpModifiers,
    marks: MarksOfGrace,
    private val npcList: NpcList,
) : PluginScript() {

    private val course =
        GnomeStrongholdCourse.COURSE.copy(lapReward = marks.lapReward(GnomeStrongholdCourse.MARKS))

    private fun ProtectedAccess.gnomeTrainerHeckle(line: String) {
        val trainerId = "npc.gnometrainer".asRSCM(RSCMType.NPC)
        val trainer =
            npcList
                .filter { it.id == trainerId && it.coords.level == player.coords.level }
                .minByOrNull { it.coords.chebyshevDistance(player.coords) } ?: return
        if (trainer.coords.chebyshevDistance(player.coords) > HECKLE_RADIUS) {
            return
        }
        trainer.say(line)
    }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.gnome_log_balance1") { logBalance() }
        onOpLoc1("loc.obstical_net2") { netClimbFirst(it.loc) }
        onOpLoc1("loc.climbing_branch") { branchClimbUp(it.loc) }
        onOpLoc1("loc.balancing_rope") { ropeBalance() }
        onOpLoc1("loc.climbing_tree") { branchClimbDown(it.loc) }
        onOpLoc1("loc.obstical_net3") { netClimbSecond(it.loc) }
        onOpLoc1("loc.obstical_pipe3_1") { pipeSqueeze(it.loc) }
        onOpLoc1("loc.obstical_pipe3_2") { pipeSqueeze(it.loc) }
    }

    private suspend fun ProtectedAccess.logBalance() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        gnomeTrainerHeckle("Okay get over that log, quick quick!")
        spam("You walk carefully across the slippery log...")
        if (!balanceCross(
            mount = GnomeStrongholdCourse.LOG_MOUNT,
            dest = GnomeStrongholdCourse.LOG_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.LOG_BALANCE",
        )) {
            return
        }
        spam("... You make it safely to the other side.")
        awardObstacle(course, 0, GnomeStrongholdCourse.LOG_XP, xpMods)
    }

    private suspend fun ProtectedAccess.netClimbFirst(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        gnomeTrainerHeckle("Move it, move it, move it!")
        spam("You climb the netting...")
        val destX = loc.coords.x.coerceIn(GnomeStrongholdCourse.NET_FIRST_X_RANGE)
        val dest =
            CoordGrid(
                destX,
                GnomeStrongholdCourse.NET_FIRST_DEST_Z,
                GnomeStrongholdCourse.NET_FIRST_DEST_LEVEL,
            )
        climbTo(
            dest,
            GnomeStrongholdCourse.NET_TICKS,
            "seq.human_reachforladder",
            "synth.ROPECLIMB",
            face = loc.coords,
        )
        awardObstacle(course, 1, GnomeStrongholdCourse.NET_FIRST_XP, xpMods)
    }

    private suspend fun ProtectedAccess.branchClimbUp(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        gnomeTrainerHeckle("That's it - straight up")
        spam("You climb the tree..")
        gnomeBranchClimb(GnomeStrongholdCourse.BRANCH_UP_DEST, loc.coords)
        spam("...To the platform above.")
        awardObstacle(course, 2, GnomeStrongholdCourse.BRANCH_UP_XP, xpMods)
    }

    private suspend fun ProtectedAccess.ropeBalance() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        gnomeTrainerHeckle("Come on scaredy cat, get across that rope!")
        spam("You carefully cross the tightrope.")
        if (!balanceCross(
            mount = GnomeStrongholdCourse.ROPE_MOUNT,
            dest = GnomeStrongholdCourse.ROPE_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.TIGHTROPE",
        )) {
            return
        }
        awardObstacle(course, 3, GnomeStrongholdCourse.ROPE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.branchClimbDown(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        spam("You climb down the tree...")
        gnomeBranchClimb(GnomeStrongholdCourse.BRANCH_DOWN_DEST, loc.coords)
        spam("You land on the ground.")
        awardObstacle(course, 4, GnomeStrongholdCourse.BRANCH_DOWN_XP, xpMods)
    }

    private suspend fun ProtectedAccess.netClimbSecond(loc: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (player.coords.z > GnomeStrongholdCourse.NET_SECOND_APPROACH_MAX_Z) {
            mes("You need to climb the obstacle from the other side.")
            return
        }
        gnomeTrainerHeckle("My Granny can move faster than you.")
        spam("You climb the netting...")
        val destX = loc.coords.x.coerceIn(GnomeStrongholdCourse.NET_SECOND_X_RANGE)
        val dest =
            CoordGrid(
                destX,
                GnomeStrongholdCourse.NET_SECOND_DEST_Z,
                GnomeStrongholdCourse.NET_SECOND_DEST_LEVEL,
            )
        climbTo(
            dest,
            GnomeStrongholdCourse.NET_TICKS,
            "seq.human_reachforladder",
            "synth.ROPECLIMB",
            face = loc.coords,
        )
        awardObstacle(course, 5, GnomeStrongholdCourse.NET_SECOND_XP, xpMods)
    }

    private suspend fun ProtectedAccess.pipeSqueeze(pipe: BoundLocInfo) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        val column = GnomeStrongholdCourse.PIPE_COLUMNS.minBy { x -> abs(x - pipe.coords.x) }
        val mount = CoordGrid(column, GnomeStrongholdCourse.PIPE_SOUTH_Z, 0)
        if (player.coords.z > GnomeStrongholdCourse.PIPE_SOUTH_Z) {
            mes("You can't enter the pipe from this side.")
            return
        }
        // Align with the pipe mouth before forced movement to avoid clipping its walls.
        if (!approachMount(mount)) {
            return
        }
        withoutRunEnergyRestore {
            player.withAgilityAnimation {
                spam("You pulled yourself through the pipes.")
                faceSquare(CoordGrid(column, GnomeStrongholdCourse.PIPE_NORTH_Z, 0))
                resetAnim()
                anim("seq.human_doublepipesqueeze")
                soundSynth("synth.SQUEEZE_IN")
                delay(1)
                exactMove(
                    start = player.coords,
                    end = CoordGrid(column, GnomeStrongholdCourse.PIPE_SOUTH_Z + 1, 0),
                    delay1 = 0,
                    delay2 = CLIENT_CYCLES_PER_TICK,
                    dir = constants.em_face_north,
                    teleportType = TeleportType.Exempt,
                )
                delay(1)
                // Pipe tiles are collision-blocked; force the interior walk and leave the final tile for crawl-out.
                resetAnim()
                val interiorEnd = CoordGrid(column, GnomeStrongholdCourse.PIPE_NORTH_Z - 1, 0)
                forcedWalk(interiorEnd, maxOf(1, player.coords.chebyshevDistance(interiorEnd)))
                resetAnim()
                anim("seq.human_doublepipesqueeze")
                soundSynth("synth.SQUEEZE_OUT")
                exactMove(
                    start = player.coords,
                    end = CoordGrid(column, GnomeStrongholdCourse.PIPE_NORTH_Z, 0),
                    delay1 = 0,
                    delay2 = CLIENT_CYCLES_PER_TICK,
                    dir = constants.em_face_north,
                    teleportType = TeleportType.Exempt,
                )
                delay(2)
                resetAnim()
                awardObstacle(course, 6, GnomeStrongholdCourse.PIPE_XP, xpMods)
            }
        }
    }

    private companion object {
        private const val HECKLE_RADIUS = 5
    }
}
