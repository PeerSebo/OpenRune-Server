package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun foundryOrePack() = dbTable("dbtable.giants_foundry_ore_pack_outcomes", serverOnly = true) {
    column("ore", 0, VarType.OBJ)
    column("weight", 1, VarType.INT)
    for ((key, item, weight) in listOf(
        Triple("coal", "obj.coal", 8164),
        Triple("iron", "obj.iron_ore", 1011),
        Triple("mithril", "obj.mithril_ore", 645),
        Triple("adamantite", "obj.adamantite_ore", 152),
        Triple("runite", "obj.runite_ore", 28),
    )) {
        row("dbrow.giants_foundry_ore_pack_$key") {
            columnRSCM(0, item)
            column(1, weight)
        }
    }
}
