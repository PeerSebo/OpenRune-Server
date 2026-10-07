package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun falconryRulesTable() = dbTable("dbtable.hunter_falconry_rules", serverOnly = true) {
    column("fees", 0, VarType.INT)
    column("flight", 1, VarType.INT)
    column("timeouts", 2, VarType.INT)
    column("bounds", 3, VarType.INT)
    row("dbrow.hunter_falconry_rules") {
        column(0, 500, 500000)
        column(1, 10, 3, 2)
        column(2, 50, 10)
        column(3, 2364, 3573, 2394, 3620, 0)
    }
}

internal fun hunterFalconryTable() = dbTable("dbtable.hunter_falconry", serverOnly = true) {
    column("code", 0, VarType.INT)
    column("prey", 1, VarType.NPC)
    column("falcon", 2, VarType.NPC)
    column("level", 3, VarType.INT)
    column("xp", 4, VarType.INT)
    column("loot", 5, VarType.OBJ)
    column("chances", 6, VarType.ENUM)
    row("dbrow.hunter_falconry_spotted") {
        column(0, 1)
        columnRSCM(1, "npc.huntingbeast_speedy")
        columnRSCM(2, "npc.hunting_falcon_onspeedy")
        column(3, 43)
        column(4, 1040)
        columnRSCM(5, "obj.bones", "obj.huntingbeast_speedy_fur")
        columnRSCM(6, "enum.hunter_falconry_spotted_chances")
    }
    row("dbrow.hunter_falconry_dark") {
        column(0, 2)
        columnRSCM(1, "npc.huntingbeast_silent")
        columnRSCM(2, "npc.hunting_falcon_onsilent")
        column(3, 57)
        column(4, 1320)
        columnRSCM(5, "obj.bones", "obj.huntingbeast_silent_fur")
        columnRSCM(6, "enum.hunter_falconry_dark_chances")
    }
    row("dbrow.hunter_falconry_dashing") {
        column(0, 3)
        columnRSCM(1, "npc.huntingbeast_speedy2")
        columnRSCM(2, "npc.hunting_falcon_onspeedy2")
        column(3, 69)
        column(4, 1560)
        columnRSCM(5, "obj.bones", "obj.huntingbeast_speedy2_fur", "obj.huntingbeast_speedy2_meat")
        columnRSCM(6, "enum.hunter_falconry_dashing_chances")
    }
}

internal fun hunterBirdhouseTable() = dbTable("dbtable.hunter_birdhouses", serverOnly = true) {
    column("code", 0, VarType.INT)
    column("house", 1, VarType.OBJ)
    column("built", 2, VarType.LOC)
    column("seeded", 3, VarType.LOC)
    column("finished", 4, VarType.LOC)
    column("level", 5, VarType.INT)
    column("xp", 6, VarType.INT)
    column("logs", 7, VarType.OBJ)
    column("crafting_level", 8, VarType.INT)
    column("crafting_xp", 9, VarType.INT)
    column("built_state", 10, VarType.INT)
    row("dbrow.hunter_birdhouse_normal") {
        column(0, 1)
        columnRSCM(1, "obj.birdhouse_normal")
        columnRSCM(2, "loc.birdhouse_normal_built")
        columnRSCM(3, "loc.birdhouse_normal_full")
        columnRSCM(4, "loc.birdhouse_normal_bird")
        column(5, 5)
        column(6, 1120)
        columnRSCM(7, "obj.logs")
        column(8, 5)
        column(9, 150)
        column(10, 1)
    }
    row("dbrow.hunter_birdhouse_oak") {
        column(0, 2)
        columnRSCM(1, "obj.birdhouse_oak")
        columnRSCM(2, "loc.birdhouse_oak_built")
        columnRSCM(3, "loc.birdhouse_oak_full")
        columnRSCM(4, "loc.birdhouse_oak_bird")
        column(5, 14)
        column(6, 1680)
        columnRSCM(7, "obj.oak_logs")
        column(8, 15)
        column(9, 200)
        column(10, 4)
    }
    row("dbrow.hunter_birdhouse_willow") {
        column(0, 3)
        columnRSCM(1, "obj.birdhouse_willow")
        columnRSCM(2, "loc.birdhouse_willow_built")
        columnRSCM(3, "loc.birdhouse_willow_full")
        columnRSCM(4, "loc.birdhouse_willow_bird")
        column(5, 24)
        column(6, 2240)
        columnRSCM(7, "obj.willow_logs")
        column(8, 25)
        column(9, 250)
        column(10, 7)
    }
    row("dbrow.hunter_birdhouse_teak") {
        column(0, 4)
        columnRSCM(1, "obj.birdhouse_teak")
        columnRSCM(2, "loc.birdhouse_teak_built")
        columnRSCM(3, "loc.birdhouse_teak_full")
        columnRSCM(4, "loc.birdhouse_teak_bird")
        column(5, 34)
        column(6, 2800)
        columnRSCM(7, "obj.teak_logs")
        column(8, 35)
        column(9, 300)
        column(10, 10)
    }
    row("dbrow.hunter_birdhouse_maple") {
        column(0, 5)
        columnRSCM(1, "obj.birdhouse_maple")
        columnRSCM(2, "loc.birdhouse_maple_built")
        columnRSCM(3, "loc.birdhouse_maple_full")
        columnRSCM(4, "loc.birdhouse_maple_bird")
        column(5, 44)
        column(6, 3690)
        columnRSCM(7, "obj.maple_logs")
        column(8, 45)
        column(9, 350)
        column(10, 13)
    }
    row("dbrow.hunter_birdhouse_mahogany") {
        column(0, 6)
        columnRSCM(1, "obj.birdhouse_mahogany")
        columnRSCM(2, "loc.birdhouse_mahogany_built")
        columnRSCM(3, "loc.birdhouse_mahogany_full")
        columnRSCM(4, "loc.birdhouse_mahogany_bird")
        column(5, 49)
        column(6, 4800)
        columnRSCM(7, "obj.mahogany_logs")
        column(8, 50)
        column(9, 400)
        column(10, 16)
    }
    row("dbrow.hunter_birdhouse_yew") {
        column(0, 7)
        columnRSCM(1, "obj.birdhouse_yew")
        columnRSCM(2, "loc.birdhouse_yew_built")
        columnRSCM(3, "loc.birdhouse_yew_full")
        columnRSCM(4, "loc.birdhouse_yew_bird")
        column(5, 59)
        column(6, 6120)
        columnRSCM(7, "obj.yew_logs")
        column(8, 60)
        column(9, 450)
        column(10, 19)
    }
    row("dbrow.hunter_birdhouse_magic") {
        column(0, 8)
        columnRSCM(1, "obj.birdhouse_magic")
        columnRSCM(2, "loc.birdhouse_magic_built")
        columnRSCM(3, "loc.birdhouse_magic_full")
        columnRSCM(4, "loc.birdhouse_magic_bird")
        column(5, 74)
        column(6, 9690)
        columnRSCM(7, "obj.magic_logs")
        column(8, 75)
        column(9, 500)
        column(10, 22)
    }
    row("dbrow.hunter_birdhouse_redwood") {
        column(0, 9)
        columnRSCM(1, "obj.birdhouse_redwood")
        columnRSCM(2, "loc.birdhouse_redwood_built")
        columnRSCM(3, "loc.birdhouse_redwood_full")
        columnRSCM(4, "loc.birdhouse_redwood_bird")
        column(5, 89)
        column(6, 12000)
        columnRSCM(7, "obj.redwood_logs")
        column(8, 90)
        column(9, 550)
        column(10, 25)
    }
}
