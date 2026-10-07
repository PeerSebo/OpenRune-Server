package org.rsmod.content.minigames.gnomeball.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

internal fun gnomeballRules() = dbTable("dbtable.gnomeball_rules", serverOnly = true) {
    column("bounds", 0, VarType.INT)
    column("gate", 1, VarType.COORDGRID)
    column("goal", 2, VarType.COORDGRID)
    column("timings", 3, VarType.INT)
    column("ranges", 4, VarType.INT)
    column("goal_xp", 5, VarType.INT)
    column("win_xp", 6, VarType.INT)
    column("shooting", 7, VarType.INT)
    column("tackling", 8, VarType.INT)
    column("projectile", 9, VarType.INT)
    row("dbrow.gnomeball_rules") {
        column(0, 2384, 3479, 2405, 3497, 0)
        column(1, CoordGrid(2383, 3488).packed)
        column(2, CoordGrid(2404, 3487).packed)
        column(3, 4, 3, 6, 8, 20)
        column(4, 10, 10, 6)
        column(5, 40, 50, 60, 70, 50)
        column(6, 250)
        column(7, 35, 60, 6, 5, 95)
        column(8, 40, 2, 90, 65, 2, 15, 1, 2)
        column(9, 41, 5, 34, 60, 146, 163, 10, 15, 60, 11)
    }
}

internal fun gnomeballPlayers() = dbTable("dbtable.gnomeball_players", serverOnly = true) {
    column("base", 0, VarType.NPC)
    column("ball", 1, VarType.NPC)
    column("floor", 2, VarType.NPC)
    column("winger", 3, VarType.INT)
    for (colour in listOf("red", "orange", "yellow", "green")) {
        row("dbrow.gnomeball_$colour") {
            columnRSCM(0, "npc.gnomeballer_$colour")
            columnRSCM(1, "npc.gnomeballer_${colour}_withball")
            columnRSCM(2, if (colour == "green") "npc.gnomeballer_green" else "npc.gnomeballer_${colour}_tackled")
            column(3, if (colour == "green") 1 else 0)
        }
    }
}
