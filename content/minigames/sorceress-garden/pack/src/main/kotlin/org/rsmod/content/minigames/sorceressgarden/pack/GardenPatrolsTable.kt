package org.rsmod.content.minigames.sorceressgarden.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun gardenPatrols() = dbTable("dbtable.garden_patrols", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("npc", 1, VarType.NPC)
    column("season", 2, VarType.STRING)
    column("start", 3, VarType.COORDGRID)
    column("runs", 4, VarType.STRING)

    row("dbrow.garden_patrol_1") {
        column(0, 0)
        columnRSCM(1, "npc.100_osman_winter_elemental_1")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2899, 5466, 0))
        column(4, "N2 W2 S2 N2 E2 S2")
    }
    row("dbrow.garden_patrol_2") {
        column(0, 1)
        columnRSCM(1, "npc.100_osman_winter_elemental_2")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2891, 5470, 0))
        column(4, "E6 W6")
    }
    row("dbrow.garden_patrol_3") {
        column(0, 2)
        columnRSCM(1, "npc.100_osman_winter_elemental_3")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2897, 5478, 0))
        column(4, "S7 E2 N7 W2")
    }
    row("dbrow.garden_patrol_4") {
        column(0, 3)
        columnRSCM(1, "npc.100_osman_winter_elemental_4")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2900, 5480, 0))
        column(4, "W2 NW1 W1 N2 E4 S3")
    }
    row("dbrow.garden_patrol_5") {
        column(0, 4)
        columnRSCM(1, "npc.100_osman_winter_elemental_5")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2891, 5483, 0))
        column(4, "E5 S2 W5 N2")
    }
    row("dbrow.garden_patrol_6") {
        column(0, 5)
        columnRSCM(1, "npc.100_osman_winter_elemental_6")
        column(2, "WINTER")
        columnCoord(3, CoordGrid(2900, 5485, 0))
        column(4, "W11 E11")
    }
    row("dbrow.garden_patrol_7") {
        column(0, 6)
        columnRSCM(1, "npc.100_osman_spring_elemental_1")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2922, 5471, 0))
        column(4, "S12 N12")
    }
    row("dbrow.garden_patrol_8") {
        column(0, 7)
        columnRSCM(1, "npc.100_osman_spring_elemental_2")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2924, 5463, 0))
        column(4, "E4 S2 W4 N2")
    }
    row("dbrow.garden_patrol_9") {
        column(0, 8)
        columnRSCM(1, "npc.100_osman_spring_elemental_3")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2926, 5461, 0))
        column(4, "S3 W2 N3 E2")
    }
    row("dbrow.garden_patrol_10") {
        column(0, 9)
        columnRSCM(1, "npc.100_osman_spring_elemental_4")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2934, 5460, 0))
        column(4, "S2 W6 N2 E6")
    }
    row("dbrow.garden_patrol_11") {
        column(0, 10)
        columnRSCM(1, "npc.100_osman_spring_elemental_5")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2935, 5468, 0))
        column(4, "S7 N7")
    }
    row("dbrow.garden_patrol_12") {
        column(0, 11)
        columnRSCM(1, "npc.100_osman_spring_elemental_6")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2928, 5469, 0))
        column(4, "E7 W7")
    }
    row("dbrow.garden_patrol_13") {
        column(0, 12)
        columnRSCM(1, "npc.100_osman_spring_elemental_7")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2925, 5475, 0))
        column(4, "S11 N11")
    }
    row("dbrow.garden_patrol_14") {
        column(0, 13)
        columnRSCM(1, "npc.100_osman_spring_elemental_8")
        column(2, "SPRING")
        columnCoord(3, CoordGrid(2931, 5477, 0))
        column(4, "S7 N7")
    }
    row("dbrow.garden_patrol_15") {
        column(0, 14)
        columnRSCM(1, "npc.100_osman_autumn_elemental_1")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2898, 5460, 0))
        column(4, "E10 W10")
    }
    row("dbrow.garden_patrol_16") {
        column(0, 15)
        columnRSCM(1, "npc.100_osman_autumn_elemental_2")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2900, 5455, 0))
        column(4, "S5 N5")
    }
    row("dbrow.garden_patrol_17") {
        column(0, 16)
        columnRSCM(1, "npc.100_osman_autumn_elemental_3")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2899, 5449, 0))
        column(4, "E6 W6")
    }
    row("dbrow.garden_patrol_18") {
        column(0, 17)
        columnRSCM(1, "npc.100_osman_autumn_elemental_4")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2903, 5451, 0))
        column(4, "N4 E2 S4 W2")
    }
    row("dbrow.garden_patrol_19") {
        column(0, 18)
        columnRSCM(1, "npc.100_osman_autumn_elemental_5")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2904, 5457, 0))
        column(4, "E13 W13")
    }
    row("dbrow.garden_patrol_20") {
        column(0, 19)
        columnRSCM(1, "npc.100_osman_autumn_elemental_6")
        column(2, "AUTUMN")
        columnCoord(3, CoordGrid(2917, 5455, 0))
        column(4, "W9 E9")
    }
    row("dbrow.garden_patrol_21") {
        column(0, 20)
        columnRSCM(1, "npc.100_osman_summer_elemental_1")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2907, 5488, 0))
        column(4, "S5 N5")
    }
    row("dbrow.garden_patrol_22") {
        column(0, 21)
        columnRSCM(1, "npc.100_osman_summer_elemental_2")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2907, 5495, 0))
        column(4, "S5 N5")
    }
    row("dbrow.garden_patrol_23") {
        column(0, 22)
        columnRSCM(1, "npc.100_osman_summer_elemental_3")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2910, 5493, 0))
        column(4, "S6 N6")
    }
    row("dbrow.garden_patrol_24") {
        column(0, 23)
        columnRSCM(1, "npc.100_osman_summer_elemental_4")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2912, 5485, 0))
        column(4, "E3 S2 E3 N2 W3 S2 W3 N2")
    }
    row("dbrow.garden_patrol_25") {
        column(0, 24)
        columnRSCM(1, "npc.100_osman_summer_elemental_5")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2923, 5490, 0))
        column(4, "S4 W2 E2 N4")
    }
    row("dbrow.garden_patrol_26") {
        column(0, 25)
        columnRSCM(1, "npc.100_osman_summer_elemental_6")
        column(2, "SUMMER")
        columnCoord(3, CoordGrid(2921, 5495, 0))
        column(4, "S4 E2 N4 W2")
    }
}
