package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun sleepingGiantsCamera() = dbTable("dbtable.sleeping_giants_camera", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("eye", 1, VarType.COORDGRID)
    column("eye_height", 2, VarType.INT)
    column("look", 3, VarType.COORDGRID)
    column("look_height", 4, VarType.INT)
    column("move_rate", 5, VarType.INT)
    column("look_rate", 6, VarType.INT)
    column("ticks", 7, VarType.INT)

    val shots = listOf(
        listOf(3367, 11487, 500, 3367, 11479, 300, 232, 232, 1),
        listOf(3367, 11492, 500, 3367, 11484, 280, 4, 3, 5),
        listOf(3369, 11477, 700, 3364, 11493, 300, 4, 4, 8),
        listOf(3384, 11490, 700, 3364, 11491, 400, 4, 4, 8),
        listOf(3380, 11504, 900, 3366, 11489, 400, 4, 2, 8),
        listOf(3371, 11493, 700, 3368, 11486, 400, 232, 232, 0),
    )
    for ((index, shot) in shots.withIndex()) {
        row("dbrow.sleeping_giants_camera_${index + 1}") {
            column(0, index)
            columnCoord(1, CoordGrid(shot[0], shot[1]))
            column(2, shot[2])
            columnCoord(3, CoordGrid(shot[3], shot[4]))
            column(4, shot[5])
            column(5, shot[6])
            column(6, shot[7])
            column(7, shot[8])
        }
    }
}
