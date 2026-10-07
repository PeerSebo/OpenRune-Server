package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object ArdougneRooftopCourse {
    val COURSE =
        AgilityCourse(
            id = 10,
            displayName = "Ardougne Rooftop",
            lapCountVarp = "varp.agility_laps_ardougne",
            levelReq = 90,
            obstacleCount = 7,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = true,
        )

    // Failure endpoints are fitted; the stop-fail level is sourced.
    const val FAIL_LOW = 0
    const val FAIL_HIGH = 266

    const val FAIL_DAMAGE_LOW = AgilityPlaceholderFailDamage.LOW
    const val FAIL_DAMAGE_HIGH = AgilityPlaceholderFailDamage.HIGH

    const val WOODEN_BEAMS_XP = 43.0

    val WOODEN_BEAMS_DEST = CoordGrid(2671, 3300, 3)

    const val WOODEN_BEAMS_TICKS = 4

    const val GAP_1_XP = 65.0

    val GAP_1_DEST = CoordGrid(2665, 3318, 3)
    const val GAP_1_TICKS = 4

    val GAP_1_FAIL_DEST = CoordGrid(2671, 3313, 0)

    const val PLANK_XP = 50.0

    val PLANK_MOUNT = CoordGrid(2662, 3318, 3)

    val PLANK_DEST = CoordGrid(2655, 3318, 3)

    const val PLANK_TICKS = 8

    const val GAP_2_XP = 21.0

    val GAP_2_DEST = CoordGrid(2653, 3313, 3)
    const val GAP_2_TICKS = 3

    val GAP_2_FAIL_DEST = CoordGrid(2654, 3315, 0)

    const val GAP_3_XP = 28.0

    val GAP_3_DEST = CoordGrid(2651, 3308, 3)
    const val GAP_3_TICKS = 3

    const val STEEP_ROOF_XP = 57.0

    val STEEP_ROOF_DEST = CoordGrid(2656, 3297, 3)

    val STEEP_ROOF_MOUNT = CoordGrid(2654, 3300, 3)

    val STEEP_ROOF_WAYPOINTS =
        listOf(STEEP_ROOF_MOUNT, CoordGrid(2654, 3297, 3), STEEP_ROOF_DEST)

    const val STEEP_ROOF_TICKS = 8

    const val GAP_FINAL_XP = 625.0

    val GAP_FINAL_MOUNT = CoordGrid(2656, 3297, 3)

    val GAP_FINAL_LEDGE = CoordGrid(2658, 3298, 1)

    val GAP_FINAL_WALK_1 = CoordGrid(2661, 3298, 1)

    val GAP_FINAL_HOP_1 = CoordGrid(2663, 3297, 1)

    val GAP_FINAL_WALK_2 = CoordGrid(2666, 3297, 1)

    val GAP_FINAL_HOP_2 = CoordGrid(2667, 3297, 1)

    val GAP_FINAL_DEST = CoordGrid(2668, 3297, 0)

    const val GAP_FINAL_LEAP_TICKS = 2

    // The leap's landing tick already covers one tick of the ledge pause.
    const val GAP_FINAL_LEDGE_SETTLE_TICKS = 1

    const val GAP_FINAL_BREAK_1_PAUSE_TICKS = 2

    const val GAP_FINAL_BREAK_2_PAUSE_TICKS = 3

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 3,
            tiles = listOf(CoordGrid(2657, 3318, 3)),
        )
}
