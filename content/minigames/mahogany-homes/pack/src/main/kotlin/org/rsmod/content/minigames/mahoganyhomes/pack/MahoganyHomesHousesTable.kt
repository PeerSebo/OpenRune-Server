package org.rsmod.content.minigames.mahoganyhomes.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun mahoganyHomesHouses() = dbTable("dbtable.mahogany_homes_houses", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("client", 2, VarType.NPC)
    column("hotspots", 3, VarType.LOC)
    row("dbrow.mahogany_homes_house_bob") {
        column(0, 0)
        column(1, "BOB")
        columnRSCM(2, "npc.con_contract_client_1")
        columnRSCM(3, *(1..8).map { "loc.con_contract_varrock_north_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_jeff") {
        column(0, 1)
        column(1, "JEFF")
        columnRSCM(2, "npc.con_contract_client_2")
        columnRSCM(3, *(1..8).map { "loc.con_contract_varrock_mid_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_sarah") {
        column(0, 2)
        column(1, "SARAH")
        columnRSCM(2, "npc.con_contract_client_3")
        columnRSCM(3, *(1..6).map { "loc.con_contract_varrock_south_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_tau") {
        column(0, 3)
        column(1, "TAU")
        columnRSCM(2, "npc.con_contract_client_4")
        columnRSCM(3, *(1..7).map { "loc.con_contract_falador_southeast_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_larry") {
        column(0, 4)
        column(1, "LARRY")
        columnRSCM(2, "npc.con_contract_client_5")
        columnRSCM(3, *(1..7).map { "loc.con_contract_falador_north_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_norman") {
        column(0, 5)
        column(1, "NORMAN")
        columnRSCM(2, "npc.falador_man4")
        columnRSCM(3, *(1..7).map { "loc.con_contract_falador_south_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_noella") {
        column(0, 6)
        column(1, "NOELLA")
        columnRSCM(2, "npc.con_contract_client_6")
        columnRSCM(3, *(1..8).map { "loc.con_contract_ardy_north_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_ross") {
        column(0, 7)
        column(1, "ROSS")
        columnRSCM(2, "npc.con_contract_client_7")
        columnRSCM(3, *(1..7).map { "loc.con_contract_ardy_west_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_jess") {
        column(0, 8)
        column(1, "JESS")
        columnRSCM(2, "npc.con_contract_client_8")
        columnRSCM(3, *(1..8).map { "loc.con_contract_ardy_south_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_mariah") {
        column(0, 9)
        column(1, "MARIAH")
        columnRSCM(2, "npc.con_contract_client_9")
        columnRSCM(3, *(1..8).map { "loc.con_contract_hosidius_north_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_leela") {
        column(0, 10)
        column(1, "LEELA")
        columnRSCM(2, "npc.con_contract_client_10")
        columnRSCM(3, *(1..7).map { "loc.con_contract_hosidius_east_hotspot$it" }.toTypedArray())
    }
    row("dbrow.mahogany_homes_house_barbara") {
        column(0, 11)
        column(1, "BARBARA")
        columnRSCM(2, "npc.con_contract_client_11")
        columnRSCM(3, *(1..7).map { "loc.con_contract_hosidius_south_hotspot$it" }.toTypedArray())
    }
}
