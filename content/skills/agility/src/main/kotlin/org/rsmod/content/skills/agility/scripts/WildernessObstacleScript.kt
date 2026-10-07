package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.withoutRunEnergyRestore
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.WildernessObstacleCourse
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessObstacleScript
@Inject
constructor(private val xpMods: XpModifiers, private val locRepo: LocRepository) : PluginScript() {
    private val course = WildernessObstacleCourse.COURSE

    override fun ScriptContext.startup() {
        onOpLoc1("loc.balancegate52a") { gate(OUTER_GATE) }
        onOpLoc1("loc.balancegate52b_left") { gate(INNER_GATE) }
        onOpLoc1("loc.balancegate52b_right") { gate(INNER_GATE) }
        onOpLoc1("loc.obstical_pipe2") { pipeSqueeze() }
        onOpLoc1("loc.obstical_ropeswing2") { ropeswing() }
        onOpLoc1("loc.steppingstone1") { steppingStones() }
        onOpLoc1("loc.wilderness_log_balance1") { logBalance() }
        onOpLoc1("loc.wildclimbingrock") { rocks(it.loc.coords) }
    }

    private suspend fun ProtectedAccess.gate(leaves: List<GateLeaf>) {
        if (player.agilityLvl < WildernessObstacleCourse.ENTER_LEVEL_REQ) {
            mes(
                "You need an Agility level of " +
                    "${WildernessObstacleCourse.ENTER_LEVEL_REQ} to enter the course.",
            )
            return
        }
        val leaf = leaves.minBy { abs(it.tile.x - player.coords.x) }
        val across = leaf.tile.translateZ(-1)
        val exit = if (player.coords == across) leaf.tile else across
        val route =
            if (player.coords == leaf.tile || exit == leaf.tile) listOf(exit)
            else listOf(leaf.tile, exit)
        val openTicks = maxOf(MIN_GATE_OPEN_TICKS, crossingTiles(route) + GATE_OPEN_TAIL_TICKS)

        for (opened in leaves) {
            openLeaf(opened, openTicks)
        }
        soundSynth("synth.GATE_OPEN")
        spam("You open the gate.")
        crossGate(route)
    }

    // Remove the closed leaf's collision; redraw the same loc across its edge with a quarter turn.
    private fun openLeaf(leaf: GateLeaf, openTicks: Int) {
        val closed = locRepo.findExact(leaf.tile, LocShape.WallStraight)
        if (closed != null) {
            locRepo.del(closed, openTicks)
        }
        locRepo.add(leaf.tile.translateZ(-1), leaf.closed, openTicks, leaf.openAngle, LocShape.WallStraight)
    }

    private fun ProtectedAccess.crossingTiles(route: List<CoordGrid>): Int {
        var tiles = 0
        var from = player.coords
        for (waypoint in route) {
            tiles += from.chebyshevDistance(waypoint)
            from = waypoint
        }
        return maxOf(1, tiles)
    }

    private suspend fun ProtectedAccess.crossGate(route: List<CoordGrid>) {
        val trimmed = route.dropWhile { it == player.coords }
        if (trimmed.isEmpty()) {
            return
        }
        forcedWalk(trimmed, crossingTiles(trimmed))
    }

    private suspend fun ProtectedAccess.pipeSqueeze() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (player.agilityLvl < WildernessObstacleCourse.PIPE_LEVEL_REQ) {
            mes(
                "You need an Agility level of " +
                    "${WildernessObstacleCourse.PIPE_LEVEL_REQ} to use this obstacle.",
            )
            return
        }
        val southMount = CoordGrid(WildernessObstacleCourse.PIPE_X, WildernessObstacleCourse.PIPE_SOUTH_Z, 0)
        val northMount = CoordGrid(WildernessObstacleCourse.PIPE_X, WildernessObstacleCourse.PIPE_NORTH_Z, 0)
        val northbound =
            abs(player.coords.z - southMount.z) <= abs(player.coords.z - northMount.z)
        val mount = if (northbound) southMount else northMount
        val exit = if (northbound) northMount else southMount
        val dirZ = if (northbound) 1 else -1
        val emDir = if (northbound) constants.em_face_north else constants.em_face_south
        // Align with the mouth before the relative crawl-in move.
        if (!approachMount(mount)) {
            return
        }
        withoutRunEnergyRestore {
            player.withAgilityAnimation {
                spam("You pull yourself through the pipe.")
                faceSquare(mount.translateZ(dirZ))
                resetAnim()
                anim("seq.human_doublepipesqueeze")
                soundSynth("synth.SQUEEZE_IN")
                delay(1)
                exactMove(
                    start = player.coords,
                    end = mount.translateZ(dirZ),
                    delay1 = 0,
                    delay2 = CLIENT_CYCLES_PER_TICK,
                    dir = emDir,
                    // Obstacle movement must bypass wilderness teleport restrictions.
                    teleportType = TeleportType.Exempt,
                )
                delay(1)
                resetAnim()
                val interiorEnd = exit.translateZ(-dirZ)
                forcedWalk(interiorEnd, maxOf(1, player.coords.chebyshevDistance(interiorEnd)))
                resetAnim()
                anim("seq.human_doublepipesqueeze")
                soundSynth("synth.SQUEEZE_OUT")
                exactMove(
                    start = player.coords,
                    end = exit,
                    delay1 = 0,
                    delay2 = CLIENT_CYCLES_PER_TICK,
                    dir = emDir,
                    teleportType = TeleportType.Exempt,
                )
                delay(2)
                resetAnim()
                if (northbound) {
                    awardObstacle(course, 0, WildernessObstacleCourse.PIPE_XP, xpMods)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.ropeswing() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (rollObstacleFail(course.levelReq, SWING_LOG_FAIL_LOW, SWING_LOG_FAIL_HIGH)) {
            mes("You lose your grip and fall!")
            val damage = hpScaledDamage(RATE_15_PERCENT)
            fallAndDamage(WildernessObstacleCourse.ROPESWING_FAIL_DEST, damage, damage)
            return
        }
        spam("You swing across.")
        leapTo(
            dest = WildernessObstacleCourse.ROPESWING_DEST,
            busyTicks = WildernessObstacleCourse.ROPESWING_TICKS,
            seq = "seq.human_ropeswing_long",
            synth = "synth.SWING_ACROSS",
            face = WildernessObstacleCourse.ROPESWING_FACE,
        )
        awardObstacle(course, 1, WildernessObstacleCourse.ROPESWING_XP, xpMods)
    }

    private suspend fun ProtectedAccess.steppingStones() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (!approachMount(WildernessObstacleCourse.STEPPING_STONE_MOUNT)) {
            return
        }
        spam("You carefully start crossing the stepping stones...")
        val crossed = wildernessStoneHops {
            !rollObstacleFail(course.levelReq, STONE_FAIL_LOW, STONE_FAIL_HIGH)
        }
        if (!crossed) {
            mes("You slip on the final stone and fall in!")
            val damage = hpScaledDamage(RATE_20_PERCENT)
            fallAndDamage(WildernessObstacleCourse.STEPPING_STONE_FAIL_DEST, damage, damage)
            return
        }
        spam("...You safely cross to the other side.")
        awardObstacle(course, 2, WildernessObstacleCourse.STEPPING_STONE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.logBalance() {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (!approachMount(WildernessObstacleCourse.LOG_BALANCE_MOUNT)) {
            return
        }
        if (rollObstacleFail(course.levelReq, SWING_LOG_FAIL_LOW, SWING_LOG_FAIL_HIGH)) {
            mes("You lose your balance and fall!")
            val damage = hpScaledDamage(RATE_15_PERCENT)
            fallAndDamage(WildernessObstacleCourse.LOG_BALANCE_FAIL_DEST, damage, damage)
            return
        }
        spam("You balance across the log.")
        if (!balanceCross(
            mount = WildernessObstacleCourse.LOG_BALANCE_MOUNT,
            dest = WildernessObstacleCourse.LOG_BALANCE_DEST,
            seq = "seq.human_walk_logbalance",
            synth = "synth.LOG_BALANCE",
        )) {
            return
        }
        awardObstacle(course, 3, WildernessObstacleCourse.LOG_BALANCE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.rocks(rock: CoordGrid) {
        arriveDelay()
        if (!meetsCourseReq(course)) {
            return
        }
        if (player.coords.z < WildernessObstacleCourse.ROCKS_NORTH_Z) {
            mes("You can't climb the rocks from this side.")
            return
        }
        if (!approachMount(CoordGrid(rock.x, WildernessObstacleCourse.ROCKS_NORTH_Z, 0))) return
        wildernessRockClimb(rock.x)
        spam("You reach the top.")
        awardObstacle(course, 4, WildernessObstacleCourse.ROCKS_XP, xpMods)
    }

    private fun ProtectedAccess.hpScaledDamage(rate: Double): Int =
        (player.hitpoints * rate).toInt() + 1

    private class GateLeaf(val tile: CoordGrid, val closed: String, val openAngle: LocAngle)

    private companion object {
        const val SWING_LOG_FAIL_LOW = WildernessObstacleCourse.SWING_LOG_FAIL_LOW
        const val SWING_LOG_FAIL_HIGH = WildernessObstacleCourse.SWING_LOG_FAIL_HIGH
        const val STONE_FAIL_LOW = WildernessObstacleCourse.STONE_FAIL_LOW
        const val STONE_FAIL_HIGH = WildernessObstacleCourse.STONE_FAIL_HIGH

        const val RATE_15_PERCENT = 0.15
        const val RATE_20_PERCENT = 0.20

        const val MIN_GATE_OPEN_TICKS = 4
        const val GATE_OPEN_TAIL_TICKS = 2

        val OUTER_GATE =
            listOf(GateLeaf(CoordGrid(2998, 3917, 0), "loc.balancegate52a", LocAngle.West))
        val INNER_GATE =
            listOf(
                GateLeaf(CoordGrid(2997, 3931, 0), "loc.balancegate52b_right", LocAngle.West),
                GateLeaf(CoordGrid(2998, 3931, 0), "loc.balancegate52b_left", LocAngle.East),
            )
    }
}
