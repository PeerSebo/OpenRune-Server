package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object FaladorRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 6,
            displayName = "Falador Rooftop",
            lapCountVarp = "varp.agility_laps_falador",
            levelReq = 50,
            obstacleCount = 13,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints are fitted; the stop-fail level is sourced.
    const val FAIL_LOW = 0
    const val FAIL_HIGH = 385

    const val WALL_CLIMB_XP = 10.5

    val WALL_CLIMB_DEST = CoordGrid(3036, 3342, 3)

    const val WALL_CLIMB_TICKS = 2

    const val TIGHTROPE_1_XP = 22.5

    val TIGHTROPE_1_MOUNT = CoordGrid(3040, 3343, 3)

    val TIGHTROPE_1_DEST = CoordGrid(3047, 3344, 3)

    val TIGHTROPE_1_WAYPOINTS =
        listOf(
            TIGHTROPE_1_MOUNT,
            CoordGrid(3047, 3343, 3),
            TIGHTROPE_1_DEST,
        )

    const val TIGHTROPE_1_SOUND_LOOPS = 5

    const val HANDHOLDS_XP = 60.5

    const val HANDHOLDS_DAMAGE_LOW = 3
    const val HANDHOLDS_DAMAGE_HIGH = 8

    val HANDHOLDS_MOUNT = CoordGrid(3050, 3349, 3)

    val HANDHOLDS_JUMP_ON = CoordGrid(3050, 3351, 3)

    val HANDHOLDS_HANG = CoordGrid(3050, 3351, 2)

    val HANDHOLDS_CORNER = CoordGrid(3051, 3351, 2)

    val HANDHOLDS_CATCHUP = CoordGrid(3051, 3352, 2)

    val HANDHOLDS_RUNGS =
        listOf(CoordGrid(3051, 3353, 2), CoordGrid(3051, 3354, 2), CoordGrid(3051, 3355, 2))

    val HANDHOLDS_LAST_RUNG = CoordGrid(3050, 3357, 2)

    val HANDHOLDS_DEST = CoordGrid(3050, 3357, 3)

    val HANDHOLDS_FAIL_DEST = CoordGrid(3050, 3353, 0)

    const val GAP_1_XP = 27.0

    val GAP_1_MOUNT = CoordGrid(3048, 3358, 3)

    val GAP_1_DEST = CoordGrid(3048, 3361, 3)

    const val GAP_2_XP = 26.5

    val GAP_2_MOUNT = CoordGrid(3045, 3361, 3)

    val GAP_2_DEST = CoordGrid(3041, 3361, 3)

    const val TIGHTROPE_2_XP = 60.5

    val TIGHTROPE_2_MOUNT = CoordGrid(3034, 3361, 3)

    val TIGHTROPE_2_DEST = CoordGrid(3028, 3354, 3)

    val TIGHTROPE_2_WAYPOINTS =
        listOf(
            TIGHTROPE_2_MOUNT,
            CoordGrid(3033, 3361, 3),
            CoordGrid(3028, 3356, 3),
            TIGHTROPE_2_DEST,
        )

    const val TIGHTROPE_2_SOUND_LOOPS = 4

    const val TIGHTROPE_3_XP = 53.5

    val TIGHTROPE_3_MOUNT = CoordGrid(3026, 3353, 3)

    val TIGHTROPE_3_DASH_END = CoordGrid(3021, 3353, 3)

    val TIGHTROPE_3_DEST = CoordGrid(3020, 3353, 3)

    const val GAP_3_XP = 30.0

    val GAP_3_MOUNT = CoordGrid(3018, 3353, 3)

    val GAP_3_DEST = CoordGrid(3018, 3349, 3)

    const val LEDGE_1_XP = 13.5

    val LEDGE_1_MOUNT = CoordGrid(3016, 3346, 3)

    val LEDGE_1_DEST = CoordGrid(3014, 3346, 3)

    const val LEDGE_2_XP = 13.5

    val LEDGE_2_MOUNT = CoordGrid(3013, 3344, 3)

    val LEDGE_2_DEST = CoordGrid(3013, 3342, 3)

    const val LEDGE_3_XP = 13.0

    val LEDGE_3_MOUNT = CoordGrid(3013, 3335, 3)

    val LEDGE_3_DEST = CoordGrid(3013, 3333, 3)

    const val LEDGE_4_XP = 13.5

    val LEDGE_4_MOUNT = CoordGrid(3017, 3333, 3)

    val LEDGE_4_DEST = CoordGrid(3019, 3333, 3)

    const val EDGE_XP = 241.5

    val EDGE_HOP = CoordGrid(3027, 3333, 3)

    val EDGE_DEST = CoordGrid(3029, 3333, 0)

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(3045, 3345, 3),
                    CoordGrid(3049, 3357, 3),
                    CoordGrid(3019, 3345, 3),
                    CoordGrid(3013, 3345, 3),
                    CoordGrid(3015, 3333, 3),
                ),
        )
}
