package org.rsmod.content.minigames.sorceressgarden.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun gardenSeasons() = dbTable("dbtable.garden_seasons", serverOnly = true) {
    column("season", 0, VarType.STRING)
    column("thieving_level", 1, VarType.INT)
    column("door_loc", 2, VarType.LOC)
    column("catch_eject_coord", 3, VarType.COORDGRID)
    column("tree_loc", 4, VarType.LOC)
    column("herb_loc", 5, VarType.LOC)
    column("fruit_obj", 6, VarType.OBJ)
    column("juice_obj", 7, VarType.OBJ)
    column("sqirk_count", 8, VarType.INT)
    column("farming_xp", 9, VarType.INT)
    column("osman_xp", 10, VarType.INT)
    column("thieving_boost", 11, VarType.INT)
    column("run_energy_percent", 12, VarType.INT)
    column("tree_anchor", 13, VarType.COORDGRID)
    column("herb_anchor", 14, VarType.COORDGRID)

    row("dbrow.garden_season_winter") {
        column(0, "WINTER")
        column(1, 1)
        columnRSCM(2, "loc.100_osman_door_winter")
        columnCoord(3, CoordGrid(2905, 5470, 0))
        columnRSCM(4, "loc.100_osman_pomy_tree_winter")
        columnRSCM(5, "loc.osman_herb_patch_winter")
        columnRSCM(6, "obj.osman_squirk_winter")
        columnRSCM(7, "obj.osman_squirk_j_winter")
        column(8, 5)
        column(9, 30)
        column(10, 350)
        column(11, 0)
        column(12, 5)
        columnCoord(13, CoordGrid(2891, 5475, 0))
        columnCoord(14, CoordGrid(2892, 5467, 0))
    }
    row("dbrow.garden_season_spring") {
        column(0, "SPRING")
        column(1, 25)
        columnRSCM(2, "loc.100_osman_door_spring")
        columnCoord(3, CoordGrid(2918, 5473, 0))
        columnRSCM(4, "loc.100_osman_pomy_tree_spring")
        columnRSCM(5, "loc.osman_herb_patch_spring")
        columnRSCM(6, "obj.osman_squirk_spring")
        columnRSCM(7, "obj.osman_squirk_j_spring")
        column(8, 4)
        column(9, 40)
        column(10, 1350)
        column(11, 1)
        column(12, 10)
        columnCoord(13, CoordGrid(2931, 5463, 0))
        columnCoord(14, CoordGrid(2934, 5477, 0))
    }
    row("dbrow.garden_season_autumn") {
        column(0, "AUTUMN")
        column(1, 45)
        columnRSCM(2, "loc.100_osman_door_autumn")
        columnCoord(3, CoordGrid(2913, 5465, 0))
        columnRSCM(4, "loc.100_osman_pomy_tree_autumn")
        columnRSCM(5, "loc.osman_herb_patch_autumn")
        columnRSCM(6, "obj.osman_squirk_autumn")
        columnRSCM(7, "obj.osman_squirk_j_autumn")
        column(8, 3)
        column(9, 50)
        column(10, 2350)
        column(11, 2)
        column(12, 15)
        columnCoord(13, CoordGrid(2912, 5450, 0))
        columnCoord(14, CoordGrid(2916, 5459, 0))
    }
    row("dbrow.garden_season_summer") {
        column(0, "SUMMER")
        column(1, 65)
        columnRSCM(2, "loc.100_osman_door_summer")
        columnCoord(3, CoordGrid(2910, 5478, 0))
        columnRSCM(4, "loc.100_osman_pomy_tree_summer")
        columnRSCM(5, "loc.osman_herb_patch_summer")
        columnRSCM(6, "obj.osman_squirk_summer")
        columnRSCM(7, "obj.osman_squirk_j_summer")
        column(8, 2)
        column(9, 60)
        column(10, 3000)
        column(11, 3)
        column(12, 20)
        columnCoord(13, CoordGrid(2915, 5491, 0))
        columnCoord(14, CoordGrid(2923, 5483, 0))
    }
}
