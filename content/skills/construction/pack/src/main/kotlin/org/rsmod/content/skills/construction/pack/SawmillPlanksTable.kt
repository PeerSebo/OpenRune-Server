package org.rsmod.content.skills.construction.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun sawmillPlanks() = dbTable("dbtable.sawmill_planks", serverOnly = true) {
    column("logs", 0, VarType.OBJ)
    column("plank", 1, VarType.OBJ)
    column("coins", 2, VarType.INT)

    fun plank(key: String, logs: String, output: String, cost: Int) {
        row("dbrow.sawmill_plank_$key") {
            columnRSCM(0, logs)
            columnRSCM(1, output)
            column(2, cost)
        }
    }

    plank("plain", "obj.logs", "obj.woodplank", 100)
    plank("oak", "obj.oak_logs", "obj.plank_oak", 250)
    plank("teak", "obj.teak_logs", "obj.plank_teak", 500)
    plank("mahogany", "obj.mahogany_logs", "obj.plank_mahogany", 1500)
    plank("camphor", "obj.camphor_logs", "obj.plank_camphor", 2500)
    plank("ironwood", "obj.ironwood_logs", "obj.plank_ironwood", 5000)
    plank("rosewood", "obj.rosewood_logs", "obj.plank_rosewood", 7500)
}
