package org.rsmod.content.areas.wilderness.locs

import kotlin.math.abs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FeroxBarriers : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in BarrierSymbols) onOpLoc1(symbol) { cross(it.loc) }
    }

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        val gate = Gates.firstOrNull { loc.coords in it.leaves } ?: return
        if (player.isFrozen) {
            mes("You can't move right now.")
            return
        }
        val leaving = gate.inside(player.coords)
        if (!canCross(leaving, player.vars["varbit.teleblock_cycles"])) {
            mes("You cannot enter the Enclave while you are teleblocked.")
            return
        }
        if (leaving && player.vars["varbit.wildy_hub_warning"] == 0) {
            var accepted = false
            startDialogue {
                mesbox("Beyond this barrier lies the Wilderness. Other players can attack you there. " +
                    "If you are teleblocked, you will be unable to return to the Enclave until it wears off.")
                accepted = when (choice3("Proceed.", 1, "Proceed and don't ask again.", 2, "Stay here.", 3)) {
                    1 -> true
                    2 -> { vars["varbit.wildy_hub_warning"] = 1; true }
                    else -> false
                }
            }
            if (!accepted) return
        }
        if (player.isFrozen) return
        val route = gate.route(player.coords)
        var previous = player.coords
        val distance = route.sumOf { point -> previous.chebyshevDistance(point).also { previous = point } }
        // Cross with orthogonal waypoints while preserving the barrier collision for everyone else.
        if (route.isNotEmpty()) walkAcross(route, distance)
    }

    private suspend fun ProtectedAccess.walkAcross(route: List<CoordGrid>, distance: Int) {
        player.forcedRoute = true
        try {
            player.routeDestination.clear()
            player.routeDestination.addAll(route)
            var elapsed = 0
            while (player.coords != route.last() && elapsed < distance + 2 && !player.isFrozen) {
                player.moveSpeed = MoveSpeed.Walk
                player.tempMoveSpeed = MoveSpeed.Walk
                delay(1)
                elapsed++
            }
        } finally {
            player.forcedRoute = false
            player.routeDestination.clear()
            player.tempMoveSpeed = null
            player.resetPendingFaceSquare()
        }
        // A freeze or stun arriving during the crossing must stop it. The usual obstacle
        // helper's final relocation would let a newly frozen player escape into the Enclave.
    }

    internal data class Gate(val leaves: List<CoordGrid>, val dx: Int, val dz: Int) {
        fun inside(point: CoordGrid): Boolean =
            (point.x - leaves.first().x) * dx + (point.z - leaves.first().z) * dz <= 0

        fun route(point: CoordGrid): List<CoordGrid> {
            // Chebyshev distance ties both leaves when standing one tile outside the second
            // leaf; Manhattan distance keeps the crossing in the player's existing lane.
            val inner = leaves.minBy { abs(it.x - point.x) + abs(it.z - point.z) }
            val outer = inner.translate(dx, dz)
            return (if (inside(point)) listOf(inner, outer) else listOf(outer, inner))
                .dropWhile { it == point }
        }
    }

    internal companion object {
        val BarrierSymbols = listOf("loc.wildy_hub_entry_barrier", "loc.wildy_hub_entry_barrier_m")
        val Gates = listOf(
            Gate(listOf(CoordGrid(3123, 3628), CoordGrid(3123, 3629)), -1, 0),
            Gate(listOf(CoordGrid(3134, 3639), CoordGrid(3135, 3639)), 0, 1),
            Gate(listOf(CoordGrid(3154, 3634), CoordGrid(3154, 3635)), 1, 0),
            Gate(listOf(CoordGrid(3134, 3617), CoordGrid(3135, 3617)), 0, -1),
        )
        fun canCross(leaving: Boolean, teleblockTicks: Int): Boolean = leaving || teleblockTicks <= 0
    }
}
