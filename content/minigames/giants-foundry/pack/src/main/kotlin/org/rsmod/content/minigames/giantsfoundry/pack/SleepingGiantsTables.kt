package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun sleepingGiantsRepairs() = dbTable("dbtable.sleeping_giants_repairs", serverOnly = true) {
    column("name", 0, VarType.STRING)
    column("original", 1, VarType.LOC)
    column("multi", 2, VarType.LOC)
    column("broken", 3, VarType.LOC)
    column("repaired", 4, VarType.LOC)
    column("state", 5, VarType.INT)
    column("tool", 6, VarType.OBJ)
    column("materials", 7, VarType.OBJ)
    column("counts", 8, VarType.INT)
    column("nails", 9, VarType.INT)
    column("animations", 10, VarType.INT)
    column("durations", 11, VarType.INT)
    column("coords", 12, VarType.INT)

    row("dbrow.sleeping_giants_repair_hammer") {
        column(0, "trip hammer")
        columnRSCM(1, "loc.giants_foundry_trip_hammer")
        columnRSCM(2, "loc.giants_foundry_trip_hammer_quest_multi")
        columnRSCM(3, "loc.giants_foundry_trip_hammer_broken_quest")
        columnRSCM(4, "loc.giants_foundry_trip_hammer_quest")
        columnRSCM(5, "varbit.sleeping_giants_repair_hammer")
        columnRSCM(6, "obj.hammer")
        columnRSCM(7, "obj.oak_logs", "obj.wool")
        column(8, 1, 0)
        column(9, 5)
        columnRSCM(10, "seq.hosidius_plough_fix")
        column(11, 4)
        column(12, (3363 shl 14) or 11496)
    }
    row("dbrow.sleeping_giants_repair_grind") {
        column(0, "grindstone")
        columnRSCM(1, "loc.giants_foundry_grindstone")
        columnRSCM(2, "loc.giants_foundry_grindstone_quest_multi")
        columnRSCM(3, "loc.giants_foundry_grindstone_broken_quest")
        columnRSCM(4, "loc.giants_foundry_grindstone_quest")
        columnRSCM(5, "varbit.sleeping_giants_repair_grind")
        columnRSCM(6, "obj.chisel")
        columnRSCM(7, "obj.oak_logs", "obj.wool")
        column(8, 0, 0)
        column(9, 0)
        columnRSCM(10, "seq.human_pickupfloor", "seq.sg_grindstone_repair")
        column(11, 4, 5)
        column(12, (3361 shl 14) or 11491)
    }
    row("dbrow.sleeping_giants_repair_polish") {
        column(0, "polishing wheel")
        columnRSCM(1, "loc.giants_foundry_polishing_wheel")
        columnRSCM(2, "loc.giants_foundry_polishing_wheel_quest_multi")
        columnRSCM(3, "loc.giants_foundry_polishing_wheel_broken_quest")
        columnRSCM(4, "loc.giants_foundry_polishing_wheel_quest")
        columnRSCM(5, "varbit.sleeping_giants_repair_polish")
        columnRSCM(6, "obj.hammer")
        columnRSCM(7, "obj.oak_logs", "obj.wool")
        column(8, 2, 1)
        column(9, 5)
        columnRSCM(10, "seq.human_leather_crafting", "seq.hosidius_plough_fix")
        column(11, 3, 4)
        column(12, (3362 shl 14) or 11484)
    }
}

internal fun sleepingGiantsSupplies() = dbTable("dbtable.sleeping_giants_supplies", serverOnly = true) {
    column("items", 0, VarType.OBJ)
    column("counts", 1, VarType.INT)
    column("nails", 2, VarType.OBJ)
    row("dbrow.sleeping_giants_supplies") {
        columnRSCM(0, "obj.bronze_bar", "obj.iron_bar", "obj.bronze_battleaxe",
            "obj.iron_longsword", "obj.bronze_platebody", "obj.iron_platebody")
        column(1, 8, 6, 2, 2, 1, 1)
        columnRSCM(2, "obj.nails_bronze", "obj.nails_iron", "obj.nails", "obj.nails_black",
            "obj.nails_mithril", "obj.nails_adamant", "obj.nails_rune")
    }
}
