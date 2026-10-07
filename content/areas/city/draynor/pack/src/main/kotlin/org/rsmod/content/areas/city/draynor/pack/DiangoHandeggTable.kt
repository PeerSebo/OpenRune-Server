package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun diangoHandeggs() = dbTable("dbtable.diango_handeggs", serverOnly = true) {
    column("item", 0, VarType.OBJ)
    column("throw", 1, VarType.SEQ)
    column("catch", 2, VarType.SEQ)
    column("projectile", 3, VarType.SPOTANIM)

    fun egg(kind: String, animation: String, projectile: String) {
        row("dbrow.diango_handegg_$kind") {
            columnRSCM(0, "obj.easter18_handegg_$kind")
            columnRSCM(1, "seq.human_throw_egg_$animation")
            columnRSCM(2, "seq.human_catch")
            columnRSCM(3, "spotanim.easter18_handegg_travel_$projectile")
        }
    }
    egg("light", "sara", "sara")
    egg("balance", "guthix", "guth")
    egg("chaos", "zam", "zam")
}
