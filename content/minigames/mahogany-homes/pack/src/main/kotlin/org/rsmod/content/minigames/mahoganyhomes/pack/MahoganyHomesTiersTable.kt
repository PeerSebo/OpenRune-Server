package org.rsmod.content.minigames.mahoganyhomes.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun mahoganyHomesTiers() = dbTable("dbtable.mahogany_homes_tiers", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("level", 2, VarType.INT)
    column("plank", 3, VarType.OBJ)
    column("build_xp_tenths", 4, VarType.INT)
    column("repair_xp_tenths", 5, VarType.INT)
    column("completion_xp_tenths", 6, VarType.INT)
    column("points", 7, VarType.INT)
    row("dbrow.mahogany_homes_tier_beginner") {
        column(0, 0)
        column(1, "BEGINNER")
        column(2, 1)
        columnRSCM(3, "obj.woodplank")
        column(4, 225)
        column(5, 1275)
        column(6, 5000)
        column(7, 2)
    }
    row("dbrow.mahogany_homes_tier_novice") {
        column(0, 1)
        column(1, "NOVICE")
        column(2, 20)
        columnRSCM(3, "obj.plank_oak")
        column(4, 480)
        column(5, 1600)
        column(6, 12500)
        column(7, 3)
    }
    row("dbrow.mahogany_homes_tier_adept") {
        column(0, 2)
        column(1, "ADEPT")
        column(2, 50)
        columnRSCM(3, "obj.plank_teak")
        column(4, 720)
        column(5, 1900)
        column(6, 22500)
        column(7, 4)
    }
    row("dbrow.mahogany_homes_tier_expert") {
        column(0, 3)
        column(1, "EXPERT")
        column(2, 70)
        columnRSCM(3, "obj.plank_mahogany")
        column(4, 1120)
        column(5, 2400)
        column(6, 27500)
        column(7, 5)
    }
}
