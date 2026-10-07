package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object ColossalWyrmCourse {
    val BASIC = AgilityCourse(
        id = 12, displayName = "Colossal Wyrm Agility Course (Basic)",
        lapCountVarp = "varp.agility_laps_wyrm_basic", levelReq = 50,
        obstacleCount = 8, lapBonusXp = 341.2, restoresRunEnergy = false,
    )
    val ADVANCED = AgilityCourse(
        id = 13, displayName = "Colossal Wyrm Agility Course (Advanced)",
        lapCountVarp = "varp.agility_laps_wyrm_advanced", levelReq = 62,
        obstacleCount = 8, lapBonusXp = 662.0, restoresRunEnergy = false,
    )
    const val BASIC_OBSTACLE_XP = 37.2
    const val ADVANCED_OBSTACLE_XP = 70.0
    const val SHARED_HOP_XP = 6.2
    val START_LADDER_DEST = CoordGrid(1653, 2931, 1)
    val BALANCE_1_MOUNT = CoordGrid(1655, 2926, 1)
    val BALANCE_1_DEST = CoordGrid(1655, 2918, 1)
    val BALANCE_1_LANDING = CoordGrid(1655, 2916, 1)
    val SHARED_HOPS = listOf(
        CoordGrid(1655, 2914, 1), CoordGrid(1653, 2914, 1), CoordGrid(1653, 2912, 1),
        CoordGrid(1651, 2912, 1), CoordGrid(1651, 2910, 1), CoordGrid(1649, 2910, 1),
    )
    val BASIC_BALANCE_MOUNT = CoordGrid(1646, 2910, 1)
    val BASIC_BALANCE_DEST = CoordGrid(1635, 2910, 1)
    val BASIC_ROPE_MOUNT = CoordGrid(1630, 2911, 1)
    val BASIC_ROPE_DEST = CoordGrid(1627, 2914, 1)
    val BASIC_ROPE_LANDING = CoordGrid(1627, 2918, 1)
    val BASIC_AUTO_JUMPS = listOf(CoordGrid(1627, 2923, 1), CoordGrid(1627, 2926, 1), CoordGrid(1627, 2931, 1))
    val BASIC_LADDER_DEST = CoordGrid(1625, 2932, 2)
    val ADV_LADDER_DEST = CoordGrid(1648, 2908, 2)
    val ADV_JUMP_MOUNT = CoordGrid(1647, 2907, 2)
    val ADV_JUMPS = listOf(CoordGrid(1643, 2907, 2), CoordGrid(1639, 2907, 2), CoordGrid(1635, 2907, 2))
    val ADV_BALANCE_MOUNT = CoordGrid(1633, 2908, 2)
    val ADV_BALANCE_DEST = CoordGrid(1625, 2916, 2)
    val ADV_BALANCE_LANDING = CoordGrid(1624, 2918, 2)
    val ADV_ROPE_MOUNT = CoordGrid(1624, 2919, 2)
    val ADV_ROPE_DEST = CoordGrid(1624, 2931, 2)
    val ZIPLINE_MOUNT = CoordGrid(1626, 2933, 2)
    val ZIPLINE_END = CoordGrid(1644, 2933, 2)
    val ZIPLINE_DEST = CoordGrid(1645, 2933, 0)

    fun isForwardApproach(from: CoordGrid, mount: CoordGrid, dest: CoordGrid): Boolean =
        from.level == mount.level && from.chebyshevDistance(mount) <= from.chebyshevDistance(dest)
}
