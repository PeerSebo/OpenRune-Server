package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object PollnivneachRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 8,
            displayName = "Pollnivneach Rooftop",
            lapCountVarp = "varp.agility_laps_pollnivneach",
            levelReq = 70,
            obstacleCount = 9,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    const val STALL_FAIL_LOW = 60
    const val STALL_FAIL_HIGH = 300

    const val BASKET_XP = 10.0

    val BASKET_TOP = CoordGrid(3351, 2962, 1)

    val BASKET_DEST = CoordGrid(3351, 2964, 1)

    const val STALL_XP = 45.0

    val STALL_STEP = CoordGrid(3350, 2967, 1)

    val STALL_TOP = CoordGrid(3350, 2971, 2)

    val STALL_JUMP = CoordGrid(3352, 2973, 2)

    val STALL_DEST = CoordGrid(3352, 2973, 1)

    val STALL_FAIL_DEST = CoordGrid(3351, 2971, 0)

    const val STALL_DAMAGE_BASE = 2
    const val STALL_DAMAGE_HP_DIVISOR = 17

    const val BANNER_XP = 65.0

    val BANNER_LEAP = CoordGrid(3357, 2977, 2)

    val BANNER_GRAB = CoordGrid(3360, 2977, 2)

    val BANNER_DEST = CoordGrid(3360, 2977, 1)

    const val GAP_XP = 35.0

    val GAP_LEAP = CoordGrid(3365, 2976, 2)

    val GAP_DEST = CoordGrid(3366, 2976, 1)

    const val TREE_XP = 75.0

    val TREE_JUMP = CoordGrid(3368, 2978, 2)

    val TREE_RUNG = CoordGrid(3368, 2979, 2)

    val TREE_LAST_RUNG = CoordGrid(3368, 2982, 2)

    val TREE_DEST = CoordGrid(3368, 2982, 1)

    const val WALL_CLIMB_XP = 5.0

    val WALL_CLIMB_DEST = CoordGrid(3365, 2983, 2)
    const val WALL_CLIMB_TICKS = 2

    const val MONKEYBARS_XP = 55.0

    val MONKEYBARS_MOUNT = CoordGrid(3358, 2985, 2)

    val MONKEYBARS_APPROACH = CoordGrid(3358, 2984, 2)

    val MONKEYBARS_DEST = CoordGrid(3358, 2991, 2)

    const val MONKEYBARS_LOOP_COUNT = 13

    const val TREETOP_XP = 60.0

    val TREETOP_HOP = CoordGrid(3360, 2997, 3)

    val TREETOP_LAND = CoordGrid(3359, 3000, 3)

    val TREETOP_DEST = CoordGrid(3359, 3000, 2)

    const val LINE_XP = 540.0

    const val LINE_XP_DESERT_HARD = 666.0

    val LINE_HOP = CoordGrid(3363, 3000, 3)

    val LINE_DEST = CoordGrid(3363, 2998, 0)

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(3347, 2969, 1),
                    CoordGrid(3353, 2975, 1),
                    CoordGrid(3359, 2983, 2),
                    CoordGrid(3357, 3002, 2),
                    CoordGrid(3362, 2993, 2),
                ),
        )
}
