package org.rsmod.content.skills.agility.shortcuts.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.withAgilityAnimation
import org.rsmod.content.skills.agility.shortcuts.configs.CrumblingWall
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CrumblingWallScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.fai_falador_castle_crumble_mid") {
            player.withAgilityAnimation(preserveOnCompletion = true) { climbOver() }
        }
    }

    private suspend fun ProtectedAccess.climbOver() {
        arriveDelay()
        if (!meetsShortcutReq(CrumblingWall.LEVEL_REQ, "use this shortcut")) {
            return
        }
        val eastSide = player.coords.x > CrumblingWall.WALL.x
        val start = if (eastSide) CrumblingWall.EAST_SIDE else CrumblingWall.WEST_SIDE
        val dest = if (eastSide) CrumblingWall.WEST_SIDE else CrumblingWall.EAST_SIDE
        val dir = if (eastSide) constants.em_face_west else constants.em_face_east
        // Align with the wall before forced movement to the fixed destination.
        if (!approachMount(start)) {
            return
        }
        player.blockRunEnergyRestore(CrumblingWall.CROSS_TICKS)
        // Delay animation and glide together by one tick to preserve the opening frames.
        resetAnim()
        exactMove(
            start = player.coords,
            end = dest,
            delay1 = CrumblingWall.EM_START_CC,
            delay2 = CrumblingWall.EM_END_CC,
            dir = dir,
            teleportType = TeleportType.Exempt,
        )
        // Sequence 839 contains skeletal audio; an additional synth would double it.
        anim("seq.human_walk_style", delay = CrumblingWall.EM_START_CC)
        delay(CrumblingWall.CROSS_TICKS)
        if (player.coords == dest) awardShortcutXp(CrumblingWall.XP, xpMods)
    }
}
