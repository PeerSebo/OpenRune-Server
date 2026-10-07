package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object AlKharidRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 3,
            displayName = "Al Kharid Rooftop",
            lapCountVarp = "varp.agility_laps_alkharid",
            levelReq = 20,
            obstacleCount = 8,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints and the stop-fail level are fitted assumptions.
    const val FAIL_LOW = 116
    const val FAIL_HIGH = 507

    const val FAIL_DAMAGE_LOW = 1
    const val FAIL_DAMAGE_HIGH = 5

    const val WALL_CLIMB_XP = 12.0

    val WALL_CLIMB_DEST = CoordGrid(3273, 3192, 3)
    const val WALL_CLIMB_TICKS = 2

    const val TIGHTROPE_1_XP = 36.0

    val TIGHTROPE_1_MOUNT = CoordGrid(3272, 3181, 3)

    val TIGHTROPE_1_DEST = CoordGrid(3272, 3172, 3)

    val TIGHTROPE_1_FAIL_DEST = CoordGrid(3271, 3177, 0)

    const val CABLE_XP = 48.0

    val CABLE_DEST = CoordGrid(3284, 3166, 3)

    val CABLE_RUNUP = CoordGrid(3269, 3166, 3)

    const val ZIPLINE_XP = 48.0

    // The slide runs below the wire on plane 1.
    val ZIPLINE_MOUNT = CoordGrid(3303, 3163, 1)

    const val ZIPLINE_MOUNT_TICKS = 1

    val ZIPLINE_DEST = CoordGrid(3315, 3163, 1)

    const val ZIPLINE_BITE_TICKS = 1

    const val ZIPLINE_SLIDE_TILES = 11

    val ZIPLINE_FAIL_DEST = CoordGrid(3308, 3163, 0)

    const val TREE_XP = 12.0

    val TREE_MOUNT = CoordGrid(3318, 3165, 1)

    val TREE_DEST = CoordGrid(3317, 3174, 2)

    const val TREE_TICKS = 7
    val TREE_FIRST_HOLD = CoordGrid(3318, 3169, 1)
    val TREE_MIDDLE_HOLD = CoordGrid(3318, 3170, 1)
    val TREE_LAST_HOLD = CoordGrid(3317, 3174, 1)

    // The tree stands across a gap; ordinary adjacency cannot reach its mount.
    const val TREE_AP_DISTANCE = 4

    const val BEAMS_XP = 6.0

    val BEAMS_MOUNT = CoordGrid(3316, 3179, 2)

    val BEAMS_DEST = CoordGrid(3316, 3180, 3)
    const val BEAMS_TICKS = 2

    const val TIGHTROPE_2_XP = 18.0
    val TIGHTROPE_2_MOUNT = CoordGrid(3313, 3186, 3)

    val TIGHTROPE_2_DEST = CoordGrid(3302, 3186, 3)

    // Force the exit step because the rope anchor may be collision-blocked.
    val TIGHTROPE_2_EXIT = CoordGrid(3302, 3187, 3)

    const val GAP_XP = 36.0

    val GAP_MOUNT = CoordGrid(3300, 3192, 3)

    val GAP_DEST = CoordGrid(3299, 3194, 0)
    const val GAP_TICKS = 1

    // TODO: determine the unused tropical-tree Jump-to operation's role.

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(3315, 3161, 1),
                    CoordGrid(3266, 3166, 3),
                    CoordGrid(3284, 3172, 3),
                    CoordGrid(3276, 3185, 3),
                    CoordGrid(3302, 3187, 3),
                ),
        )
}
