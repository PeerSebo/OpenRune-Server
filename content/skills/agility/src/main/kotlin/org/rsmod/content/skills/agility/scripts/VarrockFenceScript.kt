package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.shortcuts.AgilityShortcuts
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockFenceScript : PluginScript() {
    private val shortcut = AgilityShortcuts.all.single { "loc.lumbridge_sc_fencejump" in it.locs }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.lumbridge_sc_fencejump") {
            arriveDelay()
            if (player.agilityLvl < shortcut.level) {
                mes("You need an Agility level of ${shortcut.level} to use this shortcut.")
                return@onOpLoc1
            }
            val fromA = shortcut.startsFromA(coords)
            val mount = if (fromA) shortcut.sideA else shortcut.sideB
            val dest = if (fromA) shortcut.sideB else shortcut.sideA
            if (coords.level != mount.level || coords.chebyshevDistance(mount) > 2) return@onOpLoc1
            player.withAgilityAnimation { varrockFenceCross(mount, dest) }
        }
    }
}

internal suspend fun ProtectedAccess.varrockFenceCross(mount: CoordGrid, dest: CoordGrid): Boolean {
    if (!approachMount(mount)) return false
    player.blockRunEnergyRestore(3)
    faceObstacle(dest)
    // The native frames crouch until cycle 18 and land at 54; exact packet timing is unverified.
    moveBeat(dest, "seq.human_longjump", movement = AgilityMovement(18, 54))
    delay(3)
    resetAnim()
    val exit = dest.translate((dest.x - mount.x).compareTo(0), (dest.z - mount.z).compareTo(0))
    forcedWalk(exit, crossTiles = 1)
    return true
}
