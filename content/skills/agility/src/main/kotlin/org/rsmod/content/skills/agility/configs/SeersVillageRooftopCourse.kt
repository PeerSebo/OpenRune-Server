package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object SeersVillageRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 7,
            displayName = "Seers' Village Rooftop",
            lapCountVarp = "varp.agility_laps_seers",
            levelReq = 60,
            obstacleCount = 6,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints are fitted; the stop-fail level is sourced.
    const val FAIL_LOW = 0
    const val FAIL_HIGH = 321

    const val FAIL_DAMAGE_LOW = AgilityPlaceholderFailDamage.LOW
    const val FAIL_DAMAGE_HIGH = AgilityPlaceholderFailDamage.HIGH

    const val WALL_CLIMB_XP = 45.0

    val WALL_CLIMB_SIGN = CoordGrid(2729, 3488, 1)

    val WALL_CLIMB_DEST = CoordGrid(2729, 3491, 3)

    const val GAP_1_XP = 20.0
    val GAP_1_FACE = CoordGrid(2718, 3495, 3)
    val GAP_1_DROP = CoordGrid(2719, 3495, 2)

    val GAP_1_DEST = CoordGrid(2713, 3494, 2)

    val GAP_1_FAIL_DEST = CoordGrid(2717, 3494, 0)

    const val GAP_1_FAIL_AIR_TICKS = 2

    const val TIGHTROPE_XP = 20.0

    val TIGHTROPE_MOUNT = CoordGrid(2710, 3489, 2)

    val TIGHTROPE_DEST = CoordGrid(2710, 3480, 2)

    val TIGHTROPE_FAIL_DEST = CoordGrid(2710, 3485, 0)

    const val GAP_2_XP = 35.0

    val GAP_2_LAUNCH = CoordGrid(2710, 3474, 2)

    val GAP_2_STEP_UP = CoordGrid(2710, 3474, 3)

    val GAP_2_DEST = CoordGrid(2710, 3472, 3)

    const val GAP_3_XP = 15.0

    val GAP_3_DEST = CoordGrid(2702, 3465, 2)
    const val GAP_3_TICKS = 2

    const val EDGE_XP = 435.0

    val EDGE_DEST = CoordGrid(2704, 3464, 0)
    const val EDGE_TICKS = 2

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(2727, 3498, 3),
                    CoordGrid(2707, 3493, 2),
                    CoordGrid(2710, 3493, 2),
                    CoordGrid(2712, 3481, 2),
                    CoordGrid(2698, 3465, 2),
                ),
        )
}
