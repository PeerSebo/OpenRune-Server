package org.rsmod.content.skills.agility.shortcuts.configs

import org.rsmod.map.CoordGrid

object UnderwallTunnel {
    const val LEVEL_REQ = 21

    val WEST_HOLE = CoordGrid(3138, 3516, 0)
    val WEST_START = CoordGrid(3137, 3516, 0)

    val EAST_HOLE = CoordGrid(3141, 3513, 0)
    val EAST_START = CoordGrid(3142, 3513, 0)

    const val UNDER_WALL_TICKS = 2
}

object CrumblingWall {
    const val LEVEL_REQ = 5
    const val XP = 0.5

    val WALL = CoordGrid(2935, 3355, 0)
    val WEST_SIDE = CoordGrid(2934, 3355, 0)
    val EAST_SIDE = CoordGrid(2936, 3355, 0)

    const val CROSS_TICKS = 4
    const val EM_START_CC = 30
    const val EM_END_CC = 94
}

object MossGiantRopeswings {
    const val LEVEL_REQ = 10
    const val XP = 3.0

    val ROPE1_MOUNT = CoordGrid(2709, 3209, 0)
    val ROPE1_DEST = CoordGrid(2704, 3209, 0)

    val ROPE2_MOUNT = CoordGrid(2705, 3205, 0)
    val ROPE2_DEST = CoordGrid(2709, 3205, 0)

    const val SWING_TICKS = 2
}

object McGruborRailing {
    val EAST_SIDE = CoordGrid(2662, 3500, 0)
    val WEST_SIDE = CoordGrid(2661, 3500, 0)

    const val SQUEEZE_TICKS = 3
}
