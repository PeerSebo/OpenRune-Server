package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object ShayzienBasicCourse {
    val COURSE =
        AgilityCourse(
            id = 11,
            displayName = "Shayzien Basic Agility Course",
            lapCountVarp = "varp.agility_laps_shayzien_basic",
            levelReq = 1,
            obstacleCount = 7,
            lapBonusXp = 0.0,
            finalObstacleXpRequiresLap = true,
            restoresRunEnergy = false,
        )

    const val LADDER_XP = 5.5
    val LADDER_DEST = CoordGrid(1554, 3632, 3)
    const val LADDER_TICKS = 2

    const val ROPE_CLIMB_XP = 8.0
    val ROPE_CLIMB_DEST = CoordGrid(1538, 3633, 2)
    const val ROPE_CLIMB_TICKS = 3

    const val ROPE_WALK_1_XP = 9.0
    val ROPE_WALK_1_MOUNT = CoordGrid(1536, 3633, 2)
    val ROPE_WALK_1_DEST = CoordGrid(1528, 3633, 2)

    const val BAR_CLIMB_XP = 7.0
    val BAR_CLIMB_DEST = CoordGrid(1524, 3644, 3)
    const val BAR_CLIMB_TICKS = 2

    const val ROPE_WALK_2_XP = 9.0
    val ROPE_WALK_2_MOUNT = CoordGrid(1525, 3644, 3)
    val ROPE_WALK_2_DEST = CoordGrid(1538, 3644, 3)
    val ROPE_WALK_2_DROP = CoordGrid(1539, 3644, 2)

    const val ROPE_WALK_3_XP = 9.0
    val ROPE_WALK_3_MOUNT = CoordGrid(1541, 3644, 2)
    val ROPE_WALK_3_DEST = CoordGrid(1552, 3644, 2)

    const val END_JUMP_XP = 106.0
    val END_JUMP_DEST = CoordGrid(1554, 3639, 0)
    const val END_JUMP_TICKS = 3
}
