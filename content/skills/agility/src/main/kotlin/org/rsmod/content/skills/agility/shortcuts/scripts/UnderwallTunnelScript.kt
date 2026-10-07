package org.rsmod.content.skills.agility.shortcuts.scripts

import org.rsmod.api.config.constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.scripts.CLIENT_CYCLES_PER_TICK
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.withAgilityAnimation
import org.rsmod.content.skills.agility.shortcuts.configs.UnderwallTunnel
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class UnderwallTunnelScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.varrock_sc_tunnel_west") {
            crawl(
                start = UnderwallTunnel.WEST_START,
                entryHole = UnderwallTunnel.WEST_HOLE,
                exitHole = UnderwallTunnel.EAST_HOLE,
                exitDest = UnderwallTunnel.EAST_START,
                dir = constants.em_face_east,
            )
        }
        onOpLoc1("loc.varrock_sc_tunnel_east") {
            crawl(
                start = UnderwallTunnel.EAST_START,
                entryHole = UnderwallTunnel.EAST_HOLE,
                exitHole = UnderwallTunnel.WEST_HOLE,
                exitDest = UnderwallTunnel.WEST_START,
                dir = constants.em_face_west,
            )
        }
    }

    private suspend fun ProtectedAccess.crawl(
        start: CoordGrid,
        entryHole: CoordGrid,
        exitHole: CoordGrid,
        exitDest: CoordGrid,
        dir: Int,
    ) {
        arriveDelay()
        if (!meetsShortcutReq(UnderwallTunnel.LEVEL_REQ, "use this shortcut")) {
            return
        }
        // Align with the hole before the relative crawl-in move.
        if (!approachMount(start)) {
            return
        }
        player.blockRunEnergyRestore(4 + UnderwallTunnel.UNDER_WALL_TICKS)
        player.withAgilityAnimation {
            // Play the opening frames standing before exactMove to avoid clipping them.
            faceSquare(entryHole)
            resetAnim()
            // Sequence 2589 ends with a 2000-cycle held frame; it needs an explicit reset.
            anim("seq.agilty_shortcut_enter_hole")
            soundSynth("synth.SQUEEZE_IN")
            delay(1)
            exactMove(
                start = player.coords,
                end = entryHole,
                delay1 = 0,
                delay2 = CLIENT_CYCLES_PER_TICK,
                dir = dir,
                teleportType = TeleportType.Exempt,
            )
            delay(1)
            delay(UnderwallTunnel.UNDER_WALL_TICKS)
            telejump(exitHole, TeleportType.Exempt)
            resetAnim()
            anim("seq.agilty_shortcut_exit_hole")
            soundSynth("synth.SQUEEZE_OUT")
            delay(1)
            exactMove(
                start = player.coords,
                end = exitDest,
                delay1 = 0,
                delay2 = CLIENT_CYCLES_PER_TICK,
                dir = dir,
                teleportType = TeleportType.Exempt,
            )
            delay(1)
            resetAnim()
        }
    }
}
