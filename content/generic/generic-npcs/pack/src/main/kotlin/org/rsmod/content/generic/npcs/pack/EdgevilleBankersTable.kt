package org.rsmod.content.generic.npcs.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun edgevilleBankers() = dbTable("dbtable.edgeville_bankers", serverOnly = true) {
    column("npc", 0, VarType.NPC)
    column("tile", 1, VarType.COORDGRID)
    column("facing", 2, VarType.STRING)
    val tellers = listOf(
        Triple("npc.banker1", CoordGrid(3096, 3489), "West"),
        Triple("npc.banker2", CoordGrid(3096, 3491), "West"),
        Triple("npc.banker2", CoordGrid(3096, 3492), "North"),
        Triple("npc.banker1", CoordGrid(3098, 3492), "North"),
    )
    for ((index, teller) in tellers.withIndex()) {
        row("dbrow.edgeville_banker_$index") {
            columnRSCM(0, teller.first)
            columnCoord(1, teller.second)
            column(2, teller.third)
        }
    }
}
