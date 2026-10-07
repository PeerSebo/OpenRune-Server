package org.rsmod.content.skills.runecrafting.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun ouraniaRates() = dbTable("dbtable.ourania_rates", serverOnly = true) {
    column("level", 0, VarType.INT)
    column("runes", 1, VarType.OBJ)
    column("weights", 2, VarType.INT)

    val runes = listOf(
        "obj.airrune",
        "obj.mindrune",
        "obj.waterrune",
        "obj.earthrune",
        "obj.firerune",
        "obj.bodyrune",
        "obj.cosmicrune",
        "obj.chaosrune",
        "obj.astralrune",
        "obj.naturerune",
        "obj.lawrune",
        "obj.deathrune",
        "obj.bloodrune",
        "obj.soulrune",
    )
    val bands = listOf(
        1 to intArrayOf(5000, 2500, 1200, 600, 300, 150, 85, 60, 45, 30, 15, 8, 5, 2),
        10 to intArrayOf(1500, 1800, 2100, 2400, 1200, 600, 175, 80, 60, 40, 24, 12, 6, 3),
        20 to intArrayOf(1200, 1300, 1350, 1400, 1500, 1600, 800, 420, 210, 110, 55, 32, 15, 8),
        30 to intArrayOf(700, 800, 900, 1100, 1200, 1300, 2000, 1000, 500, 250, 130, 60, 40, 20),
        40 to intArrayOf(600, 650, 700, 750, 800, 1000, 1500, 2000, 1000, 500, 260, 120, 80, 40),
        50 to intArrayOf(500, 550, 600, 650, 700, 750, 1000, 1100, 1500, 1350, 700, 350, 170, 80),
        60 to intArrayOf(450, 500, 550, 600, 700, 750, 950, 1050, 1400, 1550, 800, 400, 200, 100),
        70 to intArrayOf(300, 300, 300, 400, 400, 500, 700, 900, 1200, 1500, 1800, 1000, 500, 200),
        80 to intArrayOf(200, 200, 300, 400, 500, 600, 700, 800, 1050, 1350, 1450, 1450, 600, 400),
        90 to intArrayOf(100, 100, 200, 300, 400, 500, 600, 700, 1000, 1350, 1450, 1650, 1000, 650),
        99 to intArrayOf(100, 100, 200, 300, 300, 400, 500, 600, 950, 1350, 1450, 1550, 1300, 900),
    )
    for ((level, weights) in bands) {
        row("dbrow.ourania_rates_$level") {
            column(0, level)
            columnRSCM(1, *runes.toTypedArray())
            column(2, *weights.toTypedArray())
        }
    }
}
