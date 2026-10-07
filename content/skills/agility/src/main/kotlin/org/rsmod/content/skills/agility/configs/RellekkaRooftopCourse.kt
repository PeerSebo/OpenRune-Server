package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object RellekkaRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 9,
            displayName = "Rellekka Rooftop",
            lapCountVarp = "varp.agility_laps_rellekka",
            levelReq = 80,
            obstacleCount = 7,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints are fitted; the stop-fail level is sourced.
    const val FAIL_LOW = 0
    const val FAIL_HIGH = 298

    const val FAIL_DAMAGE_DIVISOR = 10

    const val WALL_CLIMB_XP = 20.0

    val WALL_CLIMB_DEST = CoordGrid(2626, 3676, 3)
    const val WALL_CLIMB_TICKS = 3

    const val GAP_1_XP = 30.0

    val GAP_1_DEST = CoordGrid(2620, 3667, 3)
    const val GAP_1_TICKS = 3

    const val TIGHTROPE_1_XP = 40.0

    val TIGHTROPE_1_MOUNT = CoordGrid(2623, 3658, 3)

    val TIGHTROPE_1_DEST = CoordGrid(2626, 3654, 3)

    val TIGHTROPE_1_WAYPOINTS =
        listOf(TIGHTROPE_1_MOUNT, CoordGrid(2623, 3654, 3), TIGHTROPE_1_DEST)

    val TIGHTROPE_1_EXIT = CoordGrid(2627, 3654, 3)

    val TIGHTROPE_1_FAIL_DEST = CoordGrid(2624, 3656, 0)

    const val GAP_2_XP = 85.0

    val GAP_2_LEAP_DEST = CoordGrid(2632, 3658, 3)

    const val GAP_2_LEAP_TICKS = 3

    val GAP_2_SHUFFLE_WAYPOINT = CoordGrid(2635, 3658, 3)

    val GAP_2_SHUFFLE_TURN = CoordGrid(2639, 3654, 3)

    val GAP_2_DEST = CoordGrid(2639, 3653, 3)

    const val GAP_3_XP = 25.0

    val GAP_3_DEST = CoordGrid(2643, 3658, 3)
    const val GAP_3_TICKS = 3

    const val TIGHTROPE_3_XP = 105.0

    val TIGHTROPE_3_MOUNT = CoordGrid(2647, 3663, 3)

    val TIGHTROPE_3_DEST = CoordGrid(2654, 3670, 3)

    val TIGHTROPE_3_WAYPOINTS =
        listOf(
            TIGHTROPE_3_MOUNT,
            CoordGrid(2648, 3663, 3),
            CoordGrid(2648, 3664, 3),
            CoordGrid(2649, 3664, 3),
            CoordGrid(2649, 3665, 3),
            CoordGrid(2650, 3665, 3),
            CoordGrid(2650, 3666, 3),
            CoordGrid(2651, 3666, 3),
            CoordGrid(2651, 3667, 3),
            CoordGrid(2652, 3667, 3),
            CoordGrid(2652, 3668, 3),
            CoordGrid(2653, 3668, 3),
            CoordGrid(2653, 3669, 3),
            CoordGrid(2654, 3669, 3),
            TIGHTROPE_3_DEST,
        )

    val TIGHTROPE_3_EXIT = CoordGrid(2655, 3670, 3)

    const val FISH_XP = 475.0

    val FISH_MOUNT = CoordGrid(2652, 3676, 3)

    val FISH_DEST = CoordGrid(2653, 3676, 0)

    const val FISH_TICKS = 4

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 5,
            tiles =
                listOf(
                    CoordGrid(2623, 3675, 3),
                    CoordGrid(2628, 3652, 3),
                    CoordGrid(2629, 3653, 3),
                    CoordGrid(2641, 3650, 3),
                    CoordGrid(2642, 3651, 3),
                ),
        )
}
