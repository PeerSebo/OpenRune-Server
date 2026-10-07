package org.rsmod.content.raids.theatreofblood

import org.rsmod.map.CoordGrid

internal object TheatreBarriers {
    data class Crossing(val approach: CoordGrid, val destination: CoordGrid)

    fun crossing(player: CoordGrid, barrier: CoordGrid, angle: Int): Crossing {
        val horizontal = angle and 1 != 0
        val offset = if (horizontal) player.x - barrier.x else player.z - barrier.z
        val side = if (offset < 0) -1 else 1
        return if (horizontal) Crossing(barrier.translate(side, 0), barrier.translate(-side, 0))
            else Crossing(barrier.translate(0, side), barrier.translate(0, -side))
    }
}
