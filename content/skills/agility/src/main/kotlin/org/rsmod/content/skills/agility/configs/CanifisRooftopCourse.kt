package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object CanifisRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 5,
            displayName = "Canifis Rooftop",
            lapCountVarp = "varp.agility_laps_canifis",
            levelReq = 40,
            obstacleCount = 8,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    const val GAP_3_FAIL_LOW = 175
    const val GAP_3_FAIL_HIGH = 300

    const val TREE_XP = 10.0

    val TREE_MOUNT = CoordGrid(3505, 3488, 0)

    val TREE_DEST = CoordGrid(3506, 3492, 2)

    const val TREE_TICKS = 6

    const val GAP_1_XP = 8.0

    val GAP_1_EDGE = CoordGrid(3505, 3498, 2)

    val GAP_1_DEST = CoordGrid(3502, 3504, 2)

    const val GAP_2_XP = 8.0

    val GAP_2_LAND = CoordGrid(3493, 3504, 2)

    val GAP_2_DEST = CoordGrid(3492, 3504, 2)

    const val GAP_3_XP = 10.0

    val GAP_3_LAND = CoordGrid(3481, 3499, 2)

    val GAP_3_STEP = CoordGrid(3479, 3499, 2)

    val GAP_3_DEST = CoordGrid(3479, 3499, 3)

    const val GAP_3_DAMAGE_LOW = 2
    const val GAP_3_DAMAGE_HIGH = 4

    val GAP_3_FAIL_DEST = CoordGrid(3482, 3499, 0)

    const val GAP_3_FAIL_ANIM_TICKS = 2

    const val GAP_4_XP = 8.0

    val GAP_4_DEST = CoordGrid(3478, 3486, 2)
    const val GAP_4_TICKS = 2

    const val POLEVAULT_XP = 10.0

    val POLEVAULT_RUNUP = CoordGrid(3478, 3485, 2)

    val POLEVAULT_START = CoordGrid(3482, 3481, 2)

    val POLEVAULT_GLIDE =
        listOf(
            CoordGrid(3483, 3480, 2),
            CoordGrid(3484, 3479, 2),
            CoordGrid(3485, 3478, 2),
            CoordGrid(3486, 3477, 2),
            CoordGrid(3487, 3476, 2),
        )

    val POLEVAULT_DEST = CoordGrid(3489, 3476, 3)

    const val GAP_5_XP = 11.0

    val GAP_5_DEST = CoordGrid(3510, 3476, 2)
    const val GAP_5_TICKS = 2

    const val FINISH_XP = 175.0

    val FINISH_DEST = CoordGrid(3510, 3485, 0)
    const val FINISH_TICKS = 2

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 3,
            overlevelPenaltyApplies = false,
            tiles =
                listOf(
                    CoordGrid(3506, 3495, 2),
                    CoordGrid(3501, 3505, 2),
                    CoordGrid(3490, 3501, 2),
                    CoordGrid(3478, 3496, 3),
                    CoordGrid(3478, 3484, 2),
                ),
        )
}
