package org.rsmod.content.areas.city.falador

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WhiteKnightsCastleStairs : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(OutdoorStairsBottom) { climb(it.loc) }
        onOpLoc1(OutdoorStairsTop) { climb(it.loc) }

        onOpLoc1(CastleStairsBottom) { climbStraight(it.loc) }
        onOpLoc1(CastleStairsTop) { climbStraight(it.loc) }
    }

    private fun ProtectedAccess.climb(loc: BoundLocInfo) {
        val destination = FLIGHTS[loc.coords] ?: return
        telejump(destination)
    }

    private fun ProtectedAccess.climbStraight(loc: BoundLocInfo) {
        val flight = STRAIGHT_FLIGHTS[loc.coords] ?: return
        val x = player.coords.x.coerceIn(flight.columns)
        telejump(CoordGrid(x, flight.destZ, flight.destLevel))
    }

    private data class StraightFlight(val destZ: Int, val destLevel: Int, val columns: IntRange)

    private companion object {
        private const val OutdoorStairsBottom = "loc.fai_falador_outdoorstairs_bottom"
        private const val OutdoorStairsTop = "loc.fai_falador_outdoorstairs_top"

        private const val CastleStairsBottom = "loc.fai_falador_castle_stairs"

        private const val CastleStairsTop = "loc.fai_falador_castle_stairstop"

        private val CastleStairColumns = 2984..2985

        private val FLIGHTS: Map<CoordGrid, CoordGrid> =
            mapOf(
                CoordGrid(2968, 3347, 0) to CoordGrid(2968, 3348, 1),
                CoordGrid(2968, 3347, 1) to CoordGrid(2971, 3347, 0),
            )

        private val STRAIGHT_FLIGHTS: Map<CoordGrid, StraightFlight> =
            mapOf(
                CoordGrid(2984, 3337, 1) to
                    StraightFlight(destZ = 3340, destLevel = 2, columns = CastleStairColumns),
                CoordGrid(2984, 3338, 2) to
                    StraightFlight(destZ = 3336, destLevel = 1, columns = CastleStairColumns),
            )
    }
}
