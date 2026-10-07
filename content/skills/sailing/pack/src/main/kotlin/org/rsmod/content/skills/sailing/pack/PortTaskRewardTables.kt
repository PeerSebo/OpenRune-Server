package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun portRewardAnchors() = dbTable("dbtable.sailing_port_reward_anchors", serverOnly = true) {
    column("dock", 0, VarType.DBROW)
    column("position", 1, VarType.COORDGRID)
    column("cache_derived", 2, VarType.BOOLEAN)

    fun anchor(key: String, x: Int, z: Int, cacheDerived: Boolean) {
        row("dbrow.sailing_reward_anchor_$key") {
            columnRSCM(0, "dbrow.sailing_dock_$key")
            columnCoord(1, CoordGrid(x, z))
            column(2, cacheDerived)
        }
    }
    anchor("port_sarim", 3024, 3204, true)
    anchor("the_pandemonium", 3051, 2974, true)
    anchor("lands_end", 1510, 3410, true)
    anchor("musa_point", 2946, 3149, true)
    anchor("catherby", 2805, 3428, true)
    anchor("port_piscarilius", 1819, 3685, true)
    anchor("brimhaven", 2763, 3227, true)
    anchor("port_khazard", 2662, 3155, true)
    anchor("civitas_illa_fortis", 1779, 3125, true)
    anchor("aldarin", 1446, 2965, true)
    anchor("port_roberts", 1883, 3296, true)
    anchor("prifddinas", 2173, 3334, true)
    anchor("rimmington", 2915, 3226, false)
    anchor("hosidius", 1780, 3480, false)
    anchor("ardougne", 2673, 3271, false)
    anchor("witchaven", 2718, 3302, false)
    anchor("entrana", 2834, 3335, false)
    anchor("corsair_cove", 2574, 2838, false)
    anchor("cairn_isle", 2795, 2953, false)
    anchor("sunset_coast", 1550, 2980, false)
    anchor("ruins_of_unkah", 3150, 2860, false)
    anchor("void_knights_outpost", 2660, 2650, false)
    anchor("rellekka", 2630, 3697, false)
    anchor("etceteria", 2590, 3870, false)
    anchor("port_tyras", 2140, 3120, false)
    anchor("jatizso", 2400, 3800, false)
    anchor("neitiznot", 2330, 3800, false)
    anchor("piscatoris", 2340, 3690, false)
    anchor("lunar_isle", 2100, 3900, false)
}

internal fun portBountyRewards() = dbTable("dbtable.sailing_bounty_rewards", serverOnly = true) {
    column("target", 0, VarType.NPC)
    column("xp", 1, VarType.INT)

    fun bounty(key: String, xp: Int) {
        row("dbrow.sailing_bounty_reward_$key") {
            columnRSCM(0, "npc.sailing_$key")
            column(1, xp)
        }
    }
    bounty("bull_shark", 3_465)
    bounty("sea_mogre", 3_465)
    bounty("sea_mogre_f", 3_465)
    bounty("osprey", 3_465)
    bounty("tern", 3_465)
    bounty("eagle_ray", 3_465)
    bounty("hammerhead_shark", 8_800)
    bounty("butterfly_ray", 8_800)
    bounty("frigatebird", 11_825)
    bounty("stingray", 11_825)
    bounty("albatross", 14_575)
    bounty("pygmy_kraken", 14_575)
    bounty("tiger_shark", 19_910)
    bounty("narwhal", 19_910)
    bounty("manta_ray", 25_135)
    bounty("spined_kraken", 30_965)
    bounty("armoured_kraken", 40_370)
    bounty("great_white_shark", 40_370)
    bounty("orca", 47_080)
    bounty("vampyre_kraken", 47_080)
    bounty("veiled_kraken", 47_080)
}

internal fun portCourierRewards() = dbTable("dbtable.sailing_courier_rewards", serverOnly = true) {
    column("task", 0, VarType.DBROW)
    column("xp", 1, VarType.INT)
    column("coins", 2, VarType.INT)

    row("dbrow.sailing_courier_reward_intro") {
        columnRSCM(0, "dbrow.port_task_sailing_intro")
        column(1, 100)
        column(2, 0)
    }
}
