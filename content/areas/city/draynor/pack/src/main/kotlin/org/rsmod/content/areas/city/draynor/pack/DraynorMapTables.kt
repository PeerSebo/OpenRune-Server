package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun draynorBankers() = dbTable("dbtable.draynor_bankers", serverOnly = true) {
    column("npc", 0, VarType.NPC)
    column("tile", 1, VarType.COORDGRID)
    column("facing", 2, VarType.STRING)
    val tellers = listOf(
        Triple("npc.banker1", 3088, 3242),
        Triple("npc.banker1", 3090, 3245),
        Triple("npc.banker2", 3090, 3242),
        Triple("npc.banker2", 3090, 3243),
    )
    for ((index, teller) in tellers.withIndex()) {
        row("dbrow.draynor_banker_$index") {
            columnRSCM(0, teller.first)
            columnCoord(1, CoordGrid(teller.second, teller.third))
            column(2, "East")
        }
    }
}

internal fun draynorSewerRoutes() = dbTable("dbtable.draynor_sewer_routes", serverOnly = true) {
    column("loc", 0, VarType.LOC)
    column("origin", 1, VarType.COORDGRID)
    column("destination", 2, VarType.COORDGRID)
    row("dbrow.draynor_sewer_up") {
        columnRSCM(0, "loc.vampire_ladder")
        columnCoord(1, CoordGrid(3118, 9643))
        columnCoord(2, CoordGrid(3117, 3244))
    }
    row("dbrow.draynor_sewer_down") {
        columnRSCM(0, "loc.vampire_trap2")
        columnCoord(1, CoordGrid(3118, 3244))
        columnCoord(2, CoordGrid(3118, 9644))
    }
}
