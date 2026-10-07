package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object VarrockRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 4,
            displayName = "Varrock Rooftop",
            lapCountVarp = "varp.agility_laps_varrock",
            levelReq = 30,
            obstacleCount = 9,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints are fitted; the stop-fail level is sourced.
    const val FAIL_LOW = 76
    const val FAIL_HIGH = 467

    const val WALL_CLIMB_XP = 13.5

    val WALL_CLIMB_STEP = CoordGrid(3220, 3414, 3)

    val WALL_CLIMB_DEST = CoordGrid(3219, 3414, 3)

    const val CLOTHESLINE_XP = 23.0

    const val CLOTHESLINE_DAMAGE_LOW = 3
    const val CLOTHESLINE_DAMAGE_HIGH = 8

    val CLOTHESLINE_MOUNT = CoordGrid(3214, 3414, 3)

    val CLOTHESLINE_HOPS =
        listOf(CoordGrid(3212, 3414, 3), CoordGrid(3210, 3414, 3), CoordGrid(3208, 3414, 3))

    val CLOTHESLINE_FAIL_DEST = CoordGrid(3210, 3411, 0)

    const val RUINS_XP = 19.0

    val RUINS_DEST = CoordGrid(3197, 3416, 1)
    const val RUINS_TICKS = 2

    const val WALL_SWING_XP = 28.0

    const val WALL_SWING_DAMAGE_LOW = 2
    const val WALL_SWING_DAMAGE_HIGH = 5

    val WALL_SWING_MOUNT = CoordGrid(3194, 3416, 1)

    // The routefinder cannot reach this wall's last adjacency tile; use its two-tile approach trigger.
    const val WALL_SWING_AP_DISTANCE = 2

    val WALL_SWING_STEP = CoordGrid(3193, 3416, 1)

    val WALL_SWING_TOP = CoordGrid(3190, 3414, 1)

    val WALL_SWING_RUNGS =
        listOf(
            CoordGrid(3190, 3413, 1),
            CoordGrid(3190, 3412, 1),
            CoordGrid(3190, 3411, 1),
            CoordGrid(3190, 3410, 1),
        )

    val WALL_SWING_LAST_RUNG = CoordGrid(3190, 3409, 1)

    val WALL_SWING_WALK_END = CoordGrid(3190, 3407, 1)

    val WALL_SWING_HOP_OFF = CoordGrid(3192, 3406, 1)

    val WALL_SWING_DEST = CoordGrid(3192, 3406, 3)

    val WALL_SWING_FAIL_DEST = CoordGrid(3191, 3411, 0)

    const val SCRAMBLE_XP = 10.0

    val SCRAMBLE_MOUNT = CoordGrid(3193, 3402, 3)

    val SCRAMBLE_LAUNCH = CoordGrid(3193, 3399, 3)

    val SCRAMBLE_DEST = CoordGrid(3193, 3398, 3)

    const val BALCONY_XP = 24.5

    val BALCONY_MOUNT = CoordGrid(3208, 3397, 3)

    val BALCONY_LEAP = CoordGrid(3215, 3399, 3)

    val BALCONY_JUMP = CoordGrid(3217, 3399, 3)

    val BALCONY_DEST = CoordGrid(3218, 3399, 3)

    const val LEAPDOWN_XP = 4.5

    val LEAPDOWN_DEST = CoordGrid(3236, 3403, 3)
    const val LEAPDOWN_TICKS = 2

    const val HURDLE_XP = 3.5

    val HURDLE_DEST = CoordGrid(3236, 3410, 3)
    const val HURDLE_TICKS = 2

    const val FINISH_XP = 143.7

    val FINISH_LEDGE = CoordGrid(3236, 3416, 3)

    val FINISH_DEST = CoordGrid(3236, 3417, 0)

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(3185, 3394, 3),
                    CoordGrid(3191, 3394, 3),
                    CoordGrid(3196, 3415, 1),
                    CoordGrid(3238, 3406, 3),
                    CoordGrid(3220, 3401, 3),
                ),
        )
}
