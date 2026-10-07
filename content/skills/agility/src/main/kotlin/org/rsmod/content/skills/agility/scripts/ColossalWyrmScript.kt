package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AgilityCourse
import org.rsmod.content.skills.agility.configs.ColossalWyrmCourse as Wyrm
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Op-less obstacles continue automatically; registering them as clicks would split the traversal.
class ColossalWyrmScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    private val basic = Wyrm.BASIC
    private val advanced = Wyrm.ADVANCED

    override fun ScriptContext.startup() {
        onOpLoc1("loc.varlamore_wyrm_agility_start_ladder_trigger") { startLadder() }
        onOpLoc1("loc.varlamore_wyrm_agility_balance_1_trigger") { firstTightrope() }
        onOpLoc1("loc.varlamore_wyrm_agility_basic_balance_1_trigger") { basicTightrope() }
        onOpLoc1("loc.varlamore_wyrm_agility_basic_monkeybars_1_trigger") { basicRope() }
        onOpLoc1("loc.varlamore_wyrm_agility_basic_ladder_1_trigger") { basicLadder() }
        onOpLoc1("loc.varlamore_wyrm_agility_advanced_ladder_1_trigger") { advancedLadder() }
        onOpLoc1("loc.varlamore_wyrm_agility_advanced_jump_1_trigger") { advancedJump() }
        onOpLoc1("loc.varlamore_wyrm_agility_advanced_balance_1_trigger") { advancedTightrope() }
        onOpLoc1("loc.varlamore_wyrm_agility_end_zipline_trigger") { zipline() }
    }

    private suspend fun ProtectedAccess.startLadder() {
        arriveDelay()
        if (!meetsCourseReq(basic)) return
        ladderTo(Wyrm.START_LADDER_DEST)
        awardAt(basic, 0, Wyrm.BASIC_OBSTACLE_XP, Wyrm.START_LADDER_DEST)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        if (!meetsCourseReq(basic)) return
        if (!forwardApproach(Wyrm.BALANCE_1_MOUNT, Wyrm.BALANCE_1_DEST)) return
        if (!balanceCross(Wyrm.BALANCE_1_MOUNT, Wyrm.BALANCE_1_DEST, "seq.human_walk_logbalance", "synth.TIGHTROPE")) return
        hop(Wyrm.BALANCE_1_LANDING, "seq.ic_running", null, 2)
        if (player.coords != Wyrm.BALANCE_1_LANDING) return
        awardAt(basic, 1, Wyrm.BASIC_OBSTACLE_XP, Wyrm.BALANCE_1_LANDING)
        for ((index, dest) in Wyrm.SHARED_HOPS.withIndex()) {
            hop(dest, "seq.human_jump_hurdle", "synth.HURDLE", 3)
            if (player.coords != dest) return
            if (index == Wyrm.SHARED_HOPS.lastIndex) {
                awardObstacle(basic, 2, Wyrm.SHARED_HOP_XP, xpMods)
            } else {
                statAdvance(AGILITY_STAT, Wyrm.SHARED_HOP_XP * xpMods.get(player, AGILITY_STAT))
            }
        }
    }

    private suspend fun ProtectedAccess.basicTightrope() {
        arriveDelay()
        if (!meetsCourseReq(basic)) return
        if (!forwardApproach(Wyrm.BASIC_BALANCE_MOUNT, Wyrm.BASIC_BALANCE_DEST)) return
        if (!balanceCross(Wyrm.BASIC_BALANCE_MOUNT, Wyrm.BASIC_BALANCE_DEST, "seq.human_walk_logbalance", "synth.TIGHTROPE")) return
        awardAt(basic, 3, Wyrm.BASIC_OBSTACLE_XP, Wyrm.BASIC_BALANCE_DEST)
    }

    private suspend fun ProtectedAccess.basicRope() {
        arriveDelay()
        if (!meetsCourseReq(basic)) return
        if (!forwardApproach(Wyrm.BASIC_ROPE_MOUNT, Wyrm.BASIC_ROPE_DEST)) return
        if (!balanceCross(Wyrm.BASIC_ROPE_MOUNT, Wyrm.BASIC_ROPE_DEST, "seq.wyrm_agility_ledge_walk", "synth.LOG_BALANCE")) return
        hop(Wyrm.BASIC_ROPE_LANDING, "seq.ic_running", null, 2)
        if (player.coords != Wyrm.BASIC_ROPE_LANDING) return
        awardAt(basic, 4, Wyrm.BASIC_OBSTACLE_XP, Wyrm.BASIC_ROPE_LANDING)
        for ((index, dest) in Wyrm.BASIC_AUTO_JUMPS.withIndex()) {
            val shortHop = index == 1
            hop(dest, if (shortHop) "seq.ic_running" else "seq.wyrm_agility_longjump",
                if (shortHop) null else "synth.VARL_WYRM_AGILITY_LONGJUMP_01", 2)
            if (player.coords != dest) return
        }
        awardAt(basic, 5, Wyrm.BASIC_OBSTACLE_XP, Wyrm.BASIC_AUTO_JUMPS.last())
    }

    private suspend fun ProtectedAccess.basicLadder() {
        arriveDelay()
        if (!meetsCourseReq(basic)) return
        ladderTo(Wyrm.BASIC_LADDER_DEST)
        awardAt(basic, 6, Wyrm.BASIC_OBSTACLE_XP, Wyrm.BASIC_LADDER_DEST)
    }

    private suspend fun ProtectedAccess.advancedLadder() {
        arriveDelay()
        if (!meetsCourseReq(advanced)) return
        ladderTo(Wyrm.ADV_LADDER_DEST)
        if (player.coords != Wyrm.ADV_LADDER_DEST) return
        // Switch routes only after the common prefix, preserving earned progress.
        AgilityCourseProgress.switchCourse(player, basic, advanced, expectedIndex = 3)
        awardObstacle(advanced, 3, Wyrm.ADVANCED_OBSTACLE_XP, xpMods)
    }

    private suspend fun ProtectedAccess.advancedJump() {
        arriveDelay()
        if (!meetsCourseReq(advanced) || !forwardApproach(Wyrm.ADV_JUMP_MOUNT, Wyrm.ADV_JUMPS.last())) return
        if (!approachMount(Wyrm.ADV_JUMP_MOUNT)) return
        for (dest in Wyrm.ADV_JUMPS) {
            hop(dest, "seq.human_jump_hurdle", "synth.HURDLE", 2)
            if (player.coords != dest) return
        }
        awardAt(advanced, 4, Wyrm.ADVANCED_OBSTACLE_XP, Wyrm.ADV_JUMPS.last())
    }

    private suspend fun ProtectedAccess.advancedTightrope() {
        arriveDelay()
        if (!meetsCourseReq(advanced)) return
        if (!forwardApproach(Wyrm.ADV_BALANCE_MOUNT, Wyrm.ADV_BALANCE_DEST)) return
        if (!balanceCross(Wyrm.ADV_BALANCE_MOUNT, Wyrm.ADV_BALANCE_DEST, "seq.human_walk_logbalance", "synth.TIGHTROPE")) return
        hop(Wyrm.ADV_BALANCE_LANDING, "seq.ic_running", null, 2)
        if (player.coords != Wyrm.ADV_BALANCE_LANDING) return
        awardAt(advanced, 5, Wyrm.ADVANCED_OBSTACLE_XP, Wyrm.ADV_BALANCE_LANDING)
        if (!balanceCross(Wyrm.ADV_ROPE_MOUNT, Wyrm.ADV_ROPE_DEST, "seq.wyrm_agility_ledge_walk", "synth.LOG_BALANCE")) return
        awardAt(advanced, 6, Wyrm.ADVANCED_OBSTACLE_XP, Wyrm.ADV_ROPE_DEST)
    }

    private suspend fun ProtectedAccess.zipline() {
        arriveDelay()
        if (!approachMount(Wyrm.ZIPLINE_MOUNT)) return
        val course = when (AgilityCourseProgress.currentCourseId(player)) {
            basic.id -> basic
            advanced.id -> advanced
            else -> null
        }
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(14)
            resetAnim()
            anim("seq.wyrm_agility_zipline_on")
            soundSynth("synth.MONKEYBARS_ON")
            delay(1)
            soundSynth("synth.VARL_WYRM_AGILITY_CAPE_DRAG_SLIDE_LAND_02")
            for (x in (Wyrm.ZIPLINE_MOUNT.x + 2)..Wyrm.ZIPLINE_END.x step 2) {
                moveBeat(CoordGrid(x, Wyrm.ZIPLINE_END.z, 2), "seq.wyrm_agility_zipline_cape_drag")
                delay(1)
            }
            moveBeat(Wyrm.ZIPLINE_END, "seq.wyrm_agility_zipline_jump")
            delay(1)
            moveBeat(Wyrm.ZIPLINE_END.translateLevel(-2), "seq.wyrm_agility_knockdown_loop", "synth.MONKEYBARS_OFF")
            delay(1)
            moveBeat(Wyrm.ZIPLINE_DEST, "seq.human_getup")
            delay(2)
        }
        // Allow exit from invalid laps, but gate the entire finish award on ordered completion.
        if (course != null && player.coords == Wyrm.ZIPLINE_DEST) awardObstacle(course, 7, 0.0, xpMods)
    }

    private suspend fun ProtectedAccess.ladderTo(dest: CoordGrid) {
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(2)
            resetAnim()
            anim("seq.human_reachforladder")
            delay(2)
            telejump(dest, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.hop(dest: CoordGrid, seq: String, synth: String?, ticks: Int) {
        player.withAgilityAnimation {
            player.blockRunEnergyRestore(ticks)
            moveBeat(dest, seq, synth)
            delay(ticks)
        }
    }

    private fun ProtectedAccess.awardAt(course: AgilityCourse, index: Int, xp: Double, dest: CoordGrid) {
        if (player.coords == dest) awardObstacle(course, index, xp, xpMods)
    }

    private fun ProtectedAccess.forwardApproach(mount: CoordGrid, dest: CoordGrid): Boolean {
        if (Wyrm.isForwardApproach(player.coords, mount, dest)) return true
        mes("You need to approach this obstacle from the other side.")
        return false
    }
}
