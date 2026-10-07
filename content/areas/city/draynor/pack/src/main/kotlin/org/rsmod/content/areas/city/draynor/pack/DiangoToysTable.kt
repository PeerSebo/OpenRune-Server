package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun diangoToys() = dbTable("dbtable.diango_toys", serverOnly = true) {
    column("item", 0, VarType.OBJ)
    column("animation", 1, VarType.SEQ)
    column("animation_b", 2, VarType.SEQ)
    column("sound", 3, VarType.SYNTH)
    column("overhead", 4, VarType.STRING)
    column("finish", 5, VarType.SEQ)
    column("finish_b", 6, VarType.SEQ)

    fun toy(item: String, animation: String, sound: String? = null, overhead: String = "",
            animationB: String = animation, finish: String? = null, finishB: String? = finish) {
        row("dbrow.diango_toy_$item") {
            columnRSCM(0, "obj.$item")
            columnRSCM(1, "seq.$animation")
            columnRSCM(2, "seq.$animationB")
            if (sound == null) column(3, -1) else columnRSCM(3, "synth.$sound")
            column(4, overhead)
            if (finish == null) column(5, -1) else columnRSCM(5, "seq.$finish")
            if (finishB == null) column(6, -1) else columnRSCM(6, "seq.$finishB")
        }
    }
    for (colour in listOf("brown", "white", "black", "grey")) {
        toy("horsey_$colour", "human_playhorsey_$colour", overhead = "Just say neigh to gambling!")
    }
    for (cat in listOf("lion", "tiger", "snow_leopard", "amur_leopard")) {
        toy("wwf_toy_$cat", "human_play_wwf_$cat")
    }
    toy("af15_dragon_claws", "dragon_claws")
    toy("spinning_plate", "start_spinning_plate", "PLATE_SPIN",
        animationB = "start_spinning_plate_f", finish = "stop_spinning_plate",
        finishB = "stop_spinning_plate_f")
}
