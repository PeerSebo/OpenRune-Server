package org.rsmod.content.minigames.mahoganyhomes.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun mahoganyHomesRewards() = dbTable("dbtable.mahogany_homes_rewards", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("item", 1, VarType.OBJ)
    column("points", 2, VarType.INT)
    row("dbrow.mahogany_homes_reward_construction_supply_crate") {
        column(0, 0)
        columnRSCM(1, "obj.construction_supply_crate")
        column(2, 25)
    }
    row("dbrow.mahogany_homes_reward_wearable_saw") {
        column(0, 1)
        columnRSCM(1, "obj.wearable_saw")
        column(2, 500)
    }
    row("dbrow.mahogany_homes_reward_plank_sack") {
        column(0, 2)
        columnRSCM(1, "obj.plank_sack")
        column(2, 350)
    }
    row("dbrow.mahogany_homes_reward_hosidius_blueprints") {
        column(0, 3)
        columnRSCM(1, "obj.hosidius_blueprints")
        column(2, 2000)
    }
    row("dbrow.mahogany_homes_reward_construction_hat") {
        column(0, 4)
        columnRSCM(1, "obj.construction_hat")
        column(2, 400)
    }
    row("dbrow.mahogany_homes_reward_construction_shirt") {
        column(0, 5)
        columnRSCM(1, "obj.construction_shirt")
        column(2, 800)
    }
    row("dbrow.mahogany_homes_reward_construction_trousers") {
        column(0, 6)
        columnRSCM(1, "obj.construction_trousers")
        column(2, 600)
    }
    row("dbrow.mahogany_homes_reward_construction_boots") {
        column(0, 7)
        columnRSCM(1, "obj.construction_boots")
        column(2, 200)
    }
}
