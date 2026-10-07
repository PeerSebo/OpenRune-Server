package org.rsmod.content.skills.fletching.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack

class FletchingPluginPack : PluginPack() {
    override fun dbTables() = listOf(
        dbTable("dbtable.fletching_cape_rewards", serverOnly = true) {
            column("items", 0, VarType.OBJ)
            column("daily_uses", 1, VarType.INT)
            row("dbrow.fletching_cape_rewards") {
                columnRSCM(0, "obj.xbows_grapple_tip_bolt_mithril_rope", "obj.xbows_crossbow_bronze")
                column(1, 3)
            }
        },
    )
}
