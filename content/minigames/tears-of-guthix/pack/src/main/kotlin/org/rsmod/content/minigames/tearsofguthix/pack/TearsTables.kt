package org.rsmod.content.minigames.tearsofguthix.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

internal fun tearsRules() = dbTable("dbtable.tears_rules", serverOnly = true) {
    column("stream_ticks", 0, VarType.INT)
    column("weekly_days", 1, VarType.INT)
    column("xp_requirement", 2, VarType.INT)
    column("optional_ticks", 3, VarType.INT)
    column("entry", 4, VarType.COORDGRID)
    column("return", 5, VarType.COORDGRID)
    column("bounds", 6, VarType.INT)
    column("approach_ticks", 7, VarType.INT)
    column("entry_route", 8, VarType.COORDGRID)
    column("tail_pause_ticks", 9, VarType.INT)
    column("crossing_timeout_ticks", 10, VarType.INT)
    column("arrival_pause_ticks", 11, VarType.INT)
    row("dbrow.tears_rules") {
        column(0, 16)
        column(1, 7)
        column(2, 100000)
        column(3, 132)
        column(4, CoordGrid(3251, 9516, 2).packed)
        column(5, CoordGrid(3251, 9516, 2).packed)
        column(6, 3251, 9515, 3260, 9519, 2)
        column(7, 12)
        column(8, *listOf(
            CoordGrid(3251, 9516, 2), CoordGrid(3252, 9516, 2), CoordGrid(3253, 9516, 2),
            CoordGrid(3253, 9517, 2), CoordGrid(3254, 9517, 2), CoordGrid(3255, 9517, 2),
            CoordGrid(3256, 9517, 2),
        ).map { it.packed }.toTypedArray())
        column(9, 1)
        column(10, 60)
        column(11, 1)
    }
}

internal fun tearsWalls() = dbTable("dbtable.tears_walls", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("coords", 1, VarType.COORDGRID)
    column("stand", 2, VarType.COORDGRID)
    column("angle", 3, VarType.INT)
    column("blue", 4, VarType.LOC)
    column("green", 5, VarType.LOC)
    column("dry", 6, VarType.LOC)
    val wallCoords = listOf(
        CoordGrid(3261, 9516, 2), CoordGrid(3261, 9517, 2), CoordGrid(3261, 9518, 2),
        CoordGrid(3257, 9514, 2), CoordGrid(3258, 9514, 2), CoordGrid(3259, 9514, 2),
        CoordGrid(3257, 9520, 2), CoordGrid(3258, 9520, 2), CoordGrid(3259, 9520, 2),
    )
    wallCoords.forEachIndexed { index, coord ->
        row("dbrow.tears_wall_$index") {
            val angle = when (index / 3) { 0 -> 0; 1 -> 1; else -> 3 }
            val stand = when (angle) {
                0 -> coord.translateX(-1)
                1 -> coord.translateZ(1)
                else -> coord.translateZ(-1)
            }
            column(0, index)
            column(1, coord.packed)
            column(2, stand.packed)
            column(3, angle)
            val suffix = if (index in listOf(0, 2, 3, 5, 7)) "r" else "l"
            columnRSCM(4, "loc.tog_weeping_wall_good_$suffix")
            columnRSCM(5, "loc.tog_weeping_wall_bad_$suffix")
            columnRSCM(6, "loc.tog_weeping_wall_off_$suffix")
        }
    }
}

internal fun tearsSkills() = dbTable("dbtable.tears_skills", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("stat", 1, VarType.STAT)
    column("quest", 2, VarType.STRING)
    column("house", 3, VarType.INT)
    val skills = listOf(
        "attack", "strength", "ranged", "magic", "defence", "hitpoints", "prayer",
        "agility", "herblore", "thieving", "crafting", "runecrafting", "mining", "smithing",
        "fishing", "cooking", "firemaking", "woodcutting", "fletching", "slayer", "farming",
        "construction", "hunter", "sailing",
    )
    skills.forEachIndexed { index, skill ->
        row("dbrow.tears_skill_$skill") {
            column(0, index)
            columnRSCM(1, "stat.$skill")
            column(2, when (skill) {
                "herblore" -> "quest_druidicritual"
                "runecrafting" -> "quest_runemysteries"
                "sailing" -> "quest_pandemonium"
                else -> ""
            })
            column(3, if (skill == "construction") 1 else 0)
        }
    }
}
