package org.rsmod.content.skills.agility.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AgilityPyramidCourse
import org.rsmod.content.skills.agility.configs.AgilityPyramidCourse.Kind
import org.rsmod.content.skills.agility.configs.AgilityPyramidCourse.Obstacle
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.objtx.Transaction
import org.rsmod.objtx.TransactionInventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AgilityPyramidScript @Inject constructor(
    private val xpMods: XpModifiers,
    private val accessLauncher: ProtectedAccessLauncher,
) : PluginScript() {
    private val course = AgilityPyramidCourse.COURSE

    override fun ScriptContext.startup() {
        for (loc in AgilityPyramidCourse.CLICKABLE_LOCS) {
            onOpLoc1(loc) {
                val obstacle = AgilityPyramidCourse.clickedAt(it.loc.coords) ?: return@onOpLoc1
                traverse(obstacle)
            }
        }
        onOpLoc1("loc.agility_pyramid_steps_top_hotspot") {
            val dest = descents[it.loc.coords] ?: return@onOpLoc1
            arriveDelay()
            telejump(dest, TeleportType.Exempt)
        }
        onOpLoc1("loc.agility_pyramid_wall_rocks") {
            if (it.loc.coords in AgilityPyramidCourse.TOP_ROCKS) collectTop()
        }
        onOpNpcU(AgilityPyramidCourse.SIMON_NPC, AgilityPyramidCourse.TOP_OBJ) { exchangeTops() }
        onPlayerCoordsChanged {
            if (lastKnownCoords == player.coords || player.isAccessProtected) return@onPlayerCoordsChanged
            if (player.pendingTelejump || player.pendingTeleport) return@onPlayerCoordsChanged
            if (player.pendingStepCount !in 1..2) return@onPlayerCoordsChanged
            val intermediate = if (player.pendingStepCount == 2) player.lastProcessedCoord else CoordGrid.NULL
            val stone = AgilityPyramidCourse.crossedStone(lastKnownCoords, intermediate, player.coords)
                ?: return@onPlayerCoordsChanged
            accessLauncher.launch(player) {
                // Running may have crossed the trigger already; return to its mount before hopping.
                if (player.coords != stone.mount) telejump(stone.mount, TeleportType.Exempt)
                traverse(stone, automatic = true)
            }
        }
    }

    private suspend fun ProtectedAccess.traverse(obstacle: Obstacle, automatic: Boolean = false) {
        if (!automatic) arriveDelay()
        if (!meetsCourseReq(course)) return
        if (!AgilityPyramidCourse.isForwardApproach(obstacle, player.coords)) {
            mes("You need to approach this obstacle from the other side.")
            return
        }
        if (!approachMount(obstacle.mount)) return
        if (obstacle.kind == Kind.Exit) {
            player.blockRunEnergyRestore(2)
            delay(2)
            telejump(obstacle.dest, TeleportType.Exempt)
            if (player.coords != obstacle.dest) return
            val completedCourse = course.copy(lapBonusXp = AgilityPyramidCourse.completionXp(player.baseAgilityLvl))
            awardObstacle(completedCourse, obstacle.index, 0.0, xpMods)
            AgilityPyramidProgress.resetTop(player)
            mesbox("You climb down the steep passage. It leads to the base of the pyramid.")
            return
        }
        if (obstacle.kind == Kind.Stairs) {
            telejump(obstacle.dest, TeleportType.Exempt)
        } else {
            if (fails(obstacle)) {
                val landing = AgilityPyramidCourse.failureLanding(obstacle)
                if (landing.expectedIndex != null && AgilityCourseProgress.canCompleteObstacle(player, course, obstacle.index)) {
                    if (landing.expectedIndex == 0) resetLapProgress()
                    else AgilityCourseProgress.rewindCourse(player, course, landing.expectedIndex)
                }
                mes("You slip and fall!")
                fallAndDamage(landing.dest, obstacle.kind.failDamage, obstacle.kind.failDamage)
                return
            }
            when (obstacle.kind) {
                Kind.Plank -> {
                    spam("You walk carefully across the slippery plank...")
                    if (!balanceCross(obstacle.mount, obstacle.dest, "seq.human_walk_logbalance", "synth.LOG_BALANCE")) return
                }
                Kind.Ledge -> {
                    spam("You put your foot on the ledge and try to edge across...")
                    if (!balanceCross(obstacle.mount, obstacle.dest, "seq.human_walk_sidestepl", "synth.BALANCING_LEDGE")) return
                    spam("You skillfully edge across the gap.")
                }
                Kind.Wallhang -> {
                    if (!balanceCross(obstacle.mount, obstacle.dest, "seq.agility_pyramid_ledge_walk_right", "synth.HANDHOLDS_GRAB_TO_SECOND")) return
                }
                Kind.Wall -> {
                    spam("You climb the low wall...")
                    crossingBeat(obstacle, "seq.human_walk_crumbledwall", "synth.CLIMB_WALL", 3)
                    spam("... and make it over.")
                }
                Kind.Jump -> {
                    spam("You jump the gap...")
                    crossingBeat(obstacle, "seq.agility_pyramid_gap_jump", "synth.JUMP_FURTHER", 3)
                }
                Kind.Stone -> crossingBeat(obstacle, "seq.agilityarena_dive_player", "synth.PYRAMID_SCRAPE", 2)
                else -> error("Unexpected obstacle: $obstacle")
            }
        }
        if (player.coords == obstacle.dest) awardObstacle(course, obstacle.index, obstacle.kind.xp, xpMods)
    }

    private suspend fun ProtectedAccess.crossingBeat(obstacle: Obstacle, seq: String, synth: String, ticks: Int) {
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(ticks)
            moveBeat(obstacle.dest, seq, synth)
            delay(ticks)
        }
    }

    private fun ProtectedAccess.fails(obstacle: Obstacle): Boolean {
        val safeLevel = if (obstacle.kind == Kind.Jump) 75 else 70
        return player.agilityLvl < safeLevel && rollObstacleFail(course.levelReq, 150, 289)
    }

    private suspend fun ProtectedAccess.collectTop() {
        arriveDelay()
        if (!meetsCourseReq(course)) return
        if (AgilityPyramidProgress.isTopClaimed(player)) {
            mes("You have already taken the pyramid top. Complete the course before collecting another.")
            return
        }
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(2)
            resetAnim()
            anim("seq.agility_pyramid_top_climb")
            soundSynth("synth.CLIMBING_LOOP", loops = 4)
            delay(2)
        }
        // Deliver the top and mark it claimed without suspension to prevent loss or duplication.
        if (!player.claimPyramidTop { invAdd(inv, AgilityPyramidCourse.TOP_OBJ).success }) {
            mes("You need a free inventory space to take the pyramid top.")
            return
        }
        objbox(AgilityPyramidCourse.TOP_OBJ, "You find a golden pyramid!")
    }

    private fun ProtectedAccess.exchangeTops() {
        val count = inv.count(AgilityPyramidCourse.TOP_OBJ)
        if (count == 0) {
            mes("Bring me a gold pyramid top and I'll pay you 10,000 coins for it.")
            return
        }
        val result = player.invTransaction(inv) {
            exchangePyramidTops(select(inv), AgilityPyramidCourse.TOP_OBJ.asRSCM(RSCMType.OBJ), "obj.coins".asRSCM(RSCMType.OBJ), count)
        }
        if (result.success) mes("Simon Templeton gives you ${count * AgilityPyramidCourse.SIMON_REWARD_GP} coins.")
    }

    private val descents: Map<CoordGrid, CoordGrid> = AgilityPyramidCourse.OBSTACLES
        .filter { it.kind == Kind.Stairs }
        .associate {
            val top = if (it.index == 21) CoordGrid(3040, 4693, 2) else it.clicks.first().translateLevel(1)
            top to it.mount
        }
}

internal fun Player.claimPyramidTop(addTop: () -> Boolean): Boolean {
    if (AgilityPyramidProgress.isTopClaimed(this) || !addTop()) return false
    AgilityPyramidProgress.markTopClaimed(this)
    return true
}

// Selling a nonstackable top frees the coin slot atomically, even with a full inventory.
internal fun Transaction<InvObj>.exchangePyramidTops(
    inventory: TransactionInventory<InvObj>, top: Int, coins: Int, count: Int,
) {
    require(count in 1..28)
    delete { from = inventory; obj = top; strictCount = count }
    insert { into = inventory; obj = coins; strictCount = count * AgilityPyramidCourse.SIMON_REWARD_GP }
}
