package org.rsmod.api.area.checker

import org.rsmod.map.CoordGrid

public fun CoordGrid.wildernessLevel(areaChecker: AreaChecker): Int {
    if (!isInWilderness(areaChecker)) {
        return -1
    }
    return wildernessLevelInArea()
}

/** Coordinate calculation only; callers must first establish Wilderness area membership. */
internal fun CoordGrid.wildernessLevelInArea(): Int {
    val y = z
    return when {
        // Callisto's modern chamber is level 40 throughout; the generic dungeon transform
        // incorrectly reports level 50-55 for these repacked underground coordinates.
        level == 0 && x in 3338..3380 && y in 10314..10355 -> 40
        level == 0 && x in 2944..3392 && y in 3520..4351 -> ((y - 3520) shr 3) + 1
        level == 0 && x in 3008..3071 && y in 10112..10175 -> ((y - 9920) shr 3) - 1
        level == 0 && x in 2944..3455 && y in 9920..10879 -> ((y - 9920) shr 3) + 1
        level == 0 && x in 1725..1919 && y in 11520..11583 -> 21
        level == 0 && x in 1600..1663 && y in 11520..11583 -> 29
        else -> 1
    }
}
