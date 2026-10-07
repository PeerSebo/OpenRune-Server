package org.rsmod.game.map.collision

import org.rsmod.map.CoordGrid

public fun CoordGrid.tilesInChebyshevSquare(
    radius: Int,
    exclude: CoordGrid? = null,
): Sequence<CoordGrid> =
    sequence {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val t = translate(dx, dz)
                if (exclude == null || t != exclude) {
                    yield(t)
                }
            }
        }
    }
