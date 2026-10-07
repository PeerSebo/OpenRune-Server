package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object WildernessObstacleCourse {
    val COURSE =
        AgilityCourse(
            id = 15,
            displayName = "Wilderness Agility Course",
            lapCountVarp = "varp.agility_laps_wilderness",
            levelReq = 1,
            obstacleCount = 5,
            lapBonusXp = ROCKS_LAP_XP,
            restoresRunEnergy = false,
        )

    const val ENTER_LEVEL_REQ = 52

    const val PIPE_LEVEL_REQ = 49

    const val PIPE_XP = 12.5
    const val PIPE_X = 3004
    const val PIPE_SOUTH_Z = 3937
    const val PIPE_NORTH_Z = 3950

    const val ROPESWING_XP = 20.0
    val ROPESWING_DEST = CoordGrid(3005, 3958, 0)
    val ROPESWING_FACE = CoordGrid(3005, 3954, 0)
    val ROPESWING_FAIL_DEST = CoordGrid(3005, 10353, 0)
    const val ROPESWING_TICKS = 3

    const val STEPPING_STONE_XP = 20.0
    val STEPPING_STONE_MOUNT = CoordGrid(3002, 3960, 0)
    val STEPPING_STONE_HOPS = (3001 downTo 2996).map { CoordGrid(it, 3960, 0) }
    val STEPPING_STONE_FAIL_DEST = CoordGrid(3002, 3960, 0)

    const val LOG_BALANCE_XP = 20.0
    val LOG_BALANCE_MOUNT = CoordGrid(3002, 3945, 0)
    val LOG_BALANCE_DEST = CoordGrid(2994, 3945, 0)
    val LOG_BALANCE_FAIL_DEST = CoordGrid(3002, 10346, 0)

    const val ROCKS_XP = 0.0
    const val ROCKS_LAP_XP = 498.9
    const val ROCKS_NORTH_Z = 3937
    const val ROCKS_DEST_Z = 3933

    const val SWING_LOG_FAIL_LOW = 200
    const val SWING_LOG_FAIL_HIGH = 250

    const val STONE_FAIL_LOW = 180
    const val STONE_FAIL_HIGH = 250
}
