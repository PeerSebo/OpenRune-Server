package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object DraynorRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 2,
            displayName = "Draynor Village Rooftop",
            lapCountVarp = "varp.agility_laps_draynor",
            levelReq = 1,
            obstacleCount = 7,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    const val WALL_CLIMB_XP = 5.0

    val WALL_CLIMB_DEST = CoordGrid(3102, 3279, 3)
    const val WALL_CLIMB_TICKS = 2

    const val TIGHTROPE_1_XP = 8.0

    val TIGHTROPE_1_MOUNT = CoordGrid(3098, 3277, 3)

    val TIGHTROPE_1_DEST = CoordGrid(3090, 3277, 3)

    val TIGHTROPE_1_EXIT = CoordGrid(3090, 3276, 3)

    const val TIGHTROPE_2_XP = 7.0

    val TIGHTROPE_2_MOUNT = CoordGrid(3092, 3276, 3)

    val TIGHTROPE_2_DEST = CoordGrid(3092, 3267, 3)

    val TIGHTROPE_2_EXIT = CoordGrid(3092, 3266, 3)

    const val NARROW_WALL_XP = 7.0

    val NARROW_WALL_MOUNT = CoordGrid(3089, 3264, 3)
    val NARROW_WALL_APPROACH = CoordGrid(3089, 3265, 3)

    val NARROW_WALL_DEST = CoordGrid(3088, 3261, 3)

    val NARROW_WALL_WAYPOINTS =
        listOf(NARROW_WALL_MOUNT, CoordGrid(3089, 3262, 3), NARROW_WALL_DEST)

    const val WALL_SCRAMBLE_XP = 10.0

    val WALL_SCRAMBLE_LAUNCH = CoordGrid(3088, 3256, 3)
    val WALL_SCRAMBLE_DEST = CoordGrid(3088, 3255, 3)
    const val WALL_SCRAMBLE_TICKS = 3

    const val GAP_XP = 4.0

    val GAP_DEST = CoordGrid(3096, 3256, 3)
    const val GAP_TICKS = 2

    const val CRATE_XP = 79.0

    val CRATE_DEST = CoordGrid(3103, 3261, 0)

    val CRATE_LEDGE = CoordGrid(3102, 3261, 1)
    const val CRATE_TICKS = 5

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(3099, 3279, 3),
                    CoordGrid(3088, 3274, 3),
                    CoordGrid(3092, 3266, 3),
                    CoordGrid(3099, 3259, 3),
                ),
        )
}
