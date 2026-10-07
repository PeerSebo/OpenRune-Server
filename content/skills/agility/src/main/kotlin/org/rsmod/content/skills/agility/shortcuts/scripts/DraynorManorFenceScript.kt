package org.rsmod.content.skills.agility.shortcuts.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.scripts.AgilityMovement
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.faceObstacle
import org.rsmod.content.skills.agility.scripts.glideTo
import org.rsmod.content.skills.agility.scripts.withAgilityAnimation
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorManorFenceScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Fence) {
            arriveDelay()
            val (start, end) = if (coords.x <= West.x) West to East else East to West
            if (coords.level != start.level || coords.chebyshevDistance(start) > 4) return@onOpLoc1
            if (approachMount(start)) player.withAgilityAnimation { squeezeManorFence(end) }
        }
    }

    internal companion object {
        const val Fence = "loc.draynor_manor_fence_broken"
        val West = CoordGrid(3085, 3352)
        val East = CoordGrid(3086, 3352)
    }
}

internal suspend fun ProtectedAccess.squeezeManorFence(end: CoordGrid) {
    faceObstacle(end)
    resetAnim()
    anim("seq.human_squeeze")
    glideTo(end, AgilityMovement(10, 40))
    delay(2)
    resetAnim()
}
