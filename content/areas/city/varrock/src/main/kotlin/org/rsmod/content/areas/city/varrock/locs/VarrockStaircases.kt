package org.rsmod.content.areas.city.varrock.locs

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockStaircases : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fai_varrock_stairs") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_stairs_taller") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_stairs_top") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_bank_stairs") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_stairs_from_cellar") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_woodenstairs_castle") { climb(it.loc) }
        onOpLoc1("loc.fai_varrock_stairs_taller_new_fix") { climb(it.loc) }
    }

    private fun ProtectedAccess.climb(loc: BoundLocInfo) {
        val fixed = FIXED_FLIGHTS[loc.coords]
        if (fixed != null) {
            telejump(fixed)
            return
        }

        val flight = FLIGHTS[loc.coords]
        if (flight != null) {
            // Clamp the approach row to the two walkable rows to avoid landing inside a wall.
            val z = player.coords.z.coerceIn(flight.southRow, flight.northRow)
            telejump(CoordGrid(flight.destX, z + flight.destZOffset, flight.destLevel))
            return
        }

        val zFlight = Z_FLIGHTS[loc.coords] ?: return

        val x = player.coords.x.coerceIn(zFlight.westColumn, zFlight.eastColumn)
        telejump(CoordGrid(x, zFlight.destZ, zFlight.destLevel))
    }

    private data class Flight(
        val destX: Int,
        val destLevel: Int,
        val southRow: Int,
        val northRow: Int,
        val destZOffset: Int = 0,
    )

    private data class ZFlight(
        val destZ: Int,
        val destLevel: Int,
        val westColumn: Int,
        val eastColumn: Int,
    )

    private companion object {
        private val FLIGHTS: Map<CoordGrid, Flight> =
            mapOf(
                CoordGrid(3156, 3435, 0) to Flight(destX = 3155, destLevel = 1, 3435, 3436),
                CoordGrid(3156, 3435, 1) to Flight(destX = 3159, destLevel = 0, 3435, 3436),
                CoordGrid(3227, 3393, 0) to Flight(destX = 3230, destLevel = 1, 3393, 3394),
                CoordGrid(3228, 3393, 1) to Flight(destX = 3226, destLevel = 0, 3393, 3394),
                CoordGrid(3187, 3433, 0) to
                    Flight(destX = 3190, destLevel = 0, 3433, 3434, destZOffset = 6400),
                CoordGrid(3187, 9833, 0) to
                    Flight(destX = 3186, destLevel = 0, 9833, 9834, destZOffset = -6400),
            )

        private val Z_FLIGHTS: Map<CoordGrid, ZFlight> =
            mapOf(
                CoordGrid(3188, 3355, 0) to ZFlight(destZ = 3354, destLevel = 1, 3188, 3189),
                CoordGrid(3188, 3355, 1) to ZFlight(destZ = 3358, destLevel = 0, 3188, 3189),
                CoordGrid(3266, 3452, 0) to ZFlight(destZ = 3455, destLevel = 1, 3266, 3267),
                CoordGrid(3266, 3453, 1) to ZFlight(destZ = 3451, destLevel = 0, 3266, 3267),
                CoordGrid(3212, 3473, 0) to ZFlight(destZ = 3476, destLevel = 1, 3212, 3213),
                CoordGrid(3212, 3474, 1) to ZFlight(destZ = 3472, destLevel = 0, 3212, 3213),
            )

        private val FIXED_FLIGHTS: Map<CoordGrid, CoordGrid> =
            mapOf(
                CoordGrid(3188, 3389, 0) to CoordGrid(3188, 3392, 1),
                CoordGrid(3188, 3390, 1) to CoordGrid(3188, 3388, 0),
            )
    }
}
