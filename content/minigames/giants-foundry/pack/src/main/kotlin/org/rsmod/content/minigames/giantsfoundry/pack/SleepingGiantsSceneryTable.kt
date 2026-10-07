package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun sleepingGiantsScenery() = dbTable("dbtable.sleeping_giants_scenery", serverOnly = true) {
    column("original", 0, VarType.LOC)
    column("cutscene", 1, VarType.LOC)
    column("coords", 2, VarType.COORDGRID)
    column("original_level", 3, VarType.INT)

    data class Scenery(val name: String, val original: String, val target: String,
        val coords: List<CoordGrid>, val originalLevel: Int = 0)

    val scenery = listOf(
        Scenery("hammer", "trip_hammer_quest_multi", "trip_hammer_broken_cutscene", listOf(CoordGrid(3363, 11496))),
        Scenery("grindstone", "grindstone_quest_multi", "grindstone_broken_cutscene", listOf(CoordGrid(3361, 11491))),
        Scenery("polishing_wheel", "polishing_wheel_quest_multi", "polishing_wheel_broken_cutscene", listOf(CoordGrid(3362, 11484))),
        Scenery("exit", "exit", "exit_cutscene", listOf(CoordGrid(3364, 11482))),
        Scenery("crucible", "crucible_multi", "crucible_empty_cutscene", listOf(CoordGrid(3373, 11488))),
        Scenery("jig", "mould_jig", "jig_empty_cutscene", listOf(CoordGrid(3370, 11488))),
        Scenery("chest", "chest", "chest_cutscene", listOf(CoordGrid(3375, 11493))),
        Scenery("lava", "lava_pool", "lava_pool_cutscene", listOf(CoordGrid(3372, 11497))),
        Scenery("waterfall", "waterfall", "waterfall_cutscene", listOf(CoordGrid(3357, 11488))),
        Scenery("storage", "preform_storage", "preform_storage_empty_cutscene", listOf(CoordGrid(3369, 11501))),
        Scenery("supplies", "supply_box_multi", "supply_box_cutscene", listOf(CoordGrid(3370, 11483))),
        Scenery("power", "tool_powerunit", "tool_powerunit_quest", listOf(CoordGrid(3361, 11494), CoordGrid(3362, 11487))),
    )
    for (entry in scenery) {
        row("dbrow.sleeping_giants_scenery_${entry.name}") {
            columnRSCM(0, "loc.giants_foundry_${entry.original}")
            columnRSCM(1, "loc.giants_foundry_${entry.target}")
            if (entry.coords.size == 1) columnCoord(2, entry.coords.single())
            else column(2, *entry.coords.map { it.packed }.toTypedArray<Any>())
            column(3, entry.originalLevel)
        }
    }
}
