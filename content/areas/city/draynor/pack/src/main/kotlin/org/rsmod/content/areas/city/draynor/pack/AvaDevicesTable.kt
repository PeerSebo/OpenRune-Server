package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun avaDevices() = dbTable("dbtable.ava_devices", serverOnly = true) {
    column("output", 0, VarType.OBJ)
    column("level", 1, VarType.INT)
    column("coins", 2, VarType.INT)
    column("upgrade", 3, VarType.OBJ)
    column("arrows", 4, VarType.OBJ)
    column("arrow_count", 5, VarType.INT)
    column("quest", 6, VarType.STRING)
    row("dbrow.ava_device_attractor") {
        columnRSCM(0, "obj.anma_30_reward")
        column(1, 0)
        column(2, 999)
        column(3, -1)
        column(4, -1)
        column(5, 0)
        column(6, "quest_animalmagnetism")
    }
    row("dbrow.ava_device_accumulator") {
        columnRSCM(0, "obj.anma_50_reward")
        column(1, 50)
        column(2, 999)
        columnRSCM(3, "obj.anma_30_reward")
        columnRSCM(4, "obj.steel_arrow")
        column(5, 75)
        column(6, "quest_animalmagnetism")
    }
}
