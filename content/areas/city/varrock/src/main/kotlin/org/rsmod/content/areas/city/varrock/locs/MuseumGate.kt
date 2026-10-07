package org.rsmod.content.areas.city.varrock.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MuseumGate @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Gate) { cross(it.loc) }
        onOpNpc1("npc.vm_museum_guard") {
            startDialogue {
                chatNpcSpecific("Museum guard", "npc.vm_museum_guard", neutral,
                    "This area is for cleaning finds from the Dig Site. Speak to one of the archaeologists if you'd like to help.")
            }
        }
    }

    internal suspend fun ProtectedAccess.cross(gate: BoundLocInfo) {
        if (gate.coords != Position || gate.shape != LocShape.WallStraight) return
        val north = Position.translate(0, 1)
        val path = if (coords.z > Position.z) listOf(north, Position) else listOf(Position, north)
        val crossing = path.filter { it != coords }
        val original = LocInfo(gate.layer, gate.coords, gate.entity)
        if (!locs.del(gate, Int.MAX_VALUE)) return
        try {
            mes("The museum guard opens the gate for you.")
            forcedWalk(crossing, coords.chebyshevDistance(path.first()) + 1)
        } finally {
            if (locs.findExact(gate.coords, gate.shape) == null) locs.add(original, Int.MAX_VALUE)
        }
    }

    internal companion object {
        const val Gate = "loc.vm_barrier_guard_gate"
        val Position = CoordGrid(3261, 3446)
    }
}
