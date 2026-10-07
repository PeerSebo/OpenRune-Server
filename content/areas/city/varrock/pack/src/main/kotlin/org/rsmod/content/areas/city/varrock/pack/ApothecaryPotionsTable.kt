package org.rsmod.content.areas.city.varrock.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun apothecaryPotions() = dbTable("dbtable.apothecary_potions", serverOnly = true) {
    column("key", 0, VarType.STRING)
    column("product", 1, VarType.OBJ)
    column("title", 2, VarType.STRING)
    column("items", 3, VarType.OBJ)
    column("counts", 4, VarType.INT)
    column("names", 5, VarType.STRING)
    row("dbrow.apothecary_strength") {
        column(0, "strength")
        columnRSCM(1, "obj.strength4")
        column(2, "Strength potion")
        columnRSCM(3, "obj.limpwurt_root", "obj.red_spiders_eggs", "obj.coins")
        column(4, 1, 1, 5)
        column(5, "limpwurt root", "red spiders' eggs", "coins")
    }
    row("dbrow.apothecary_energy") {
        column(0, "energy")
        columnRSCM(1, "obj.4dose1energy")
        column(2, "Energy potion")
        columnRSCM(3, "obj.limpwurt_root", "obj.chocolate_dust")
        column(4, 2, 1)
        column(5, "limpwurt roots", "chocolate dust")
    }
    row("dbrow.apothecary_antipoison") {
        column(0, "antipoison")
        columnRSCM(1, "obj.4doseantipoison")
        column(2, "Antipoison potion")
        columnRSCM(3, "obj.limpwurt_root", "obj.cadavaberries", "obj.coins")
        column(4, 1, 1, 5)
        column(5, "limpwurt root", "cadavaberries", "coins")
    }
}
