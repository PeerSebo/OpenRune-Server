package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun diangoScarfStyles() = dbTable("dbtable.diango_scarf_styles", serverOnly = true) {
    column("item", 0, VarType.OBJ)
    column("name", 1, VarType.STRING)
    column("order", 2, VarType.INT)
    row("dbrow.diango_scarf_rainbow") {
        columnRSCM(0, "obj.pride17_scarf")
        column(1, "Rainbow")
        column(2, 0)
    }
    for ((index, name) in listOf("Bisexual", "Asexual", "Transgender", "Pansexual", "Nonbinary",
        "Genderqueer", "Lesbian", "Gay").withIndex()) {
        row("dbrow.diango_scarf_${name.lowercase()}") {
            columnRSCM(0, "obj.pride23_scarf_${name.lowercase()}")
            column(1, if (name == "Nonbinary") "Non-binary" else name)
            column(2, index + 1)
        }
    }
}
