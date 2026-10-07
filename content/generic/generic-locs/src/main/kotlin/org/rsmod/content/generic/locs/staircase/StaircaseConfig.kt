package org.rsmod.content.generic.locs.staircase

import org.rsmod.map.CoordGrid

internal typealias staircase_locs = StaircaseLocs

internal object StaircaseLocs

// Placement overrides account for railings and blocked exits that rotation alone cannot identify.
internal object SpiralStaircaseDestinations {
    /** Keyed on the **loc's own** coordinate, which includes its level, so each leg is distinct. */
    val Up: Map<CoordGrid, CoordGrid> =
        mapOf(
            CoordGrid(3144, 3447, 0) to CoordGrid(3144, 3449, 1),
            CoordGrid(3144, 3447, 1) to CoordGrid(3144, 3446, 2),
            CoordGrid(3848, 4919, 0) to CoordGrid(3848, 4921, 1),
            CoordGrid(3848, 4919, 1) to CoordGrid(3848, 4918, 2),
        )

    val Down: Map<CoordGrid, CoordGrid> =
        mapOf(
            CoordGrid(3144, 3447, 2) to CoordGrid(3144, 3449, 1),
            CoordGrid(3144, 3447, 1) to CoordGrid(3144, 3449, 0),
            CoordGrid(3848, 4919, 2) to CoordGrid(3848, 4921, 1),
            CoordGrid(3848, 4919, 1) to CoordGrid(3848, 4921, 0),
        )
}
