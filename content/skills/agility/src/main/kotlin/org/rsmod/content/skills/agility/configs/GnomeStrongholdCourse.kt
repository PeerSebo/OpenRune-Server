package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object GnomeStrongholdCourse {
    val COURSE =
        AgilityCourse(
            id = 1,
            displayName = "Gnome Stronghold Agility Course",
            lapCountVarp = "varp.agility_laps_gnome",
            levelReq = 1,
            obstacleCount = 7,
            lapBonusXp = 50.0,
            restoresRunEnergy = false,
        )

    const val LOG_XP = 10.0
    val LOG_MOUNT = CoordGrid(2474, 3436, 0)
    val LOG_DEST = CoordGrid(2474, 3429, 0)
    const val LOG_TICKS = 7

    const val NET_FIRST_XP = 10.0
    const val NET_FIRST_DEST_Z = 3423
    const val NET_FIRST_DEST_LEVEL = 1
    val NET_FIRST_X_RANGE = 2473..2475
    const val NET_TICKS = 2

    const val BRANCH_UP_XP = 6.5
    val BRANCH_UP_DEST = CoordGrid(2473, 3420, 2)
    const val BRANCH_TICKS = 1

    const val ROPE_XP = 10.0
    val ROPE_MOUNT = CoordGrid(2477, 3420, 2)
    val ROPE_DEST = CoordGrid(2483, 3420, 2)
    const val ROPE_TICKS = 6

    const val BRANCH_DOWN_XP = 6.5
    val BRANCH_DOWN_DEST = CoordGrid(2487, 3420, 0)

    const val NET_SECOND_XP = 10.0
    const val NET_SECOND_DEST_Z = 3428
    const val NET_SECOND_DEST_LEVEL = 0
    val NET_SECOND_X_RANGE = 2483..2487

    // Reject the landing side to prevent repeat-clicking the net for XP.
    const val NET_SECOND_APPROACH_MAX_Z = 3425

    const val PIPE_XP = 7.5
    const val PIPE_SOUTH_Z = 3430
    const val PIPE_NORTH_Z = 3437
    val PIPE_COLUMNS = intArrayOf(2484, 2487)
    const val PIPE_ENTER_TICKS = 2

    const val PIPE_EXIT_TICKS = 2

    val MARKS =
        MarkSpawnConfig(
            chanceNumerator = 2,
            chanceDenominator = 6,
            tiles =
                listOf(
                    CoordGrid(2486, 3422, 2)
                ),
        )
}
