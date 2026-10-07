package org.rsmod.content.minigames.wintertodt.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

internal fun winterRules() = dbTable("dbtable.winter_rules", serverOnly = true) {
    column("settings", 0, VarType.INT)
    column("bounds", 1, VarType.INT)
    column("entry", 2, VarType.COORDGRID)
    column("exit", 3, VarType.COORDGRID)
    column("storm", 4, VarType.COORDGRID)
    row("dbrow.winter_rules") {
        column(0, 50, 3500, 100, 2, 10, 100, 4, 8000, 500, 3)
        column(1, 1608, 3967, 1653, 4030, 0)
        column(2, CoordGrid(1630, 3968, 0).packed)
        column(3, CoordGrid(1630, 3963, 0).packed)
        column(4, CoordGrid(1627, 4004, 0).packed)
    }
}

internal fun winterActions() = dbTable("dbtable.winter_actions", serverOnly = true) {
    column("name", 0, VarType.STRING)
    column("stat", 1, VarType.STAT)
    column("xp_tenths", 2, VarType.INT)
    column("points", 3, VarType.INT)
    column("ticks", 4, VarType.INT)
    val actions = listOf(
        Action("light", "firemaking", 60, 25, 3),
        Action("root", "firemaking", 30, 10, 3),
        Action("kindling", "firemaking", 38, 25, 3),
        Action("repair", "construction", 40, 25, 3),
        Action("heal", "herblore", 0, 75, 3),
        Action("chop", "woodcutting", 3, 0, 3),
        Action("fletch", "fletching", 6, 0, 2),
        Action("potion", "herblore", 1, 0, 3),
        Action("victory", "firemaking", 1000, 0, 0),
    )
    for ((name, stat, xp, points, ticks) in actions) row("dbrow.winter_action_$name") {
        column(0, name)
        columnRSCM(1, "stat.$stat")
        column(2, xp)
        column(3, points)
        column(4, ticks)
    }
}

private data class Action(val name: String, val stat: String, val xp: Int, val points: Int, val ticks: Int)

internal fun winterCamps() = dbTable("dbtable.winter_camps", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("brazier", 1, VarType.COORDGRID)
    column("pyromancer", 2, VarType.COORDGRID)
    val camps = listOf(
        Triple("nw", CoordGrid(1620, 4015, 0), CoordGrid(1619, 4018, 0)),
        Triple("ne", CoordGrid(1638, 4015, 0), CoordGrid(1641, 4018, 0)),
        Triple("sw", CoordGrid(1620, 3997, 0), CoordGrid(1619, 3996, 0)),
        Triple("se", CoordGrid(1638, 3997, 0), CoordGrid(1641, 3996, 0)),
    )
    camps.forEachIndexed { i, (name, brazier, pyro) -> row("dbrow.winter_camp_$name") {
        column(0, i)
        column(1, brazier.packed)
        column(2, pyro.packed)
    } }
}

internal fun winterRewards() = dbTable("dbtable.winter_rewards", serverOnly = true) {
    column("category", 0, VarType.INT)
    column("stat", 1, VarType.STAT)
    column("item", 2, VarType.OBJ)
    column("quantity", 3, VarType.INT)
    column("minimum_level", 4, VarType.INT)
    row("dbrow.winter_reward_banana") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.banana_tree_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_0") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.oak_logs")
        column(3, 10, 20)
        column(4, 1)
    }
    row("dbrow.winter_reward_1") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.willow_logs")
        column(3, 10, 20)
        column(4, 1)
    }
    row("dbrow.winter_reward_2") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.teak_logs")
        column(3, 10, 20)
        column(4, 1)
    }
    row("dbrow.winter_reward_3") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.maple_logs")
        column(3, 10, 20)
        column(4, 1)
    }
    row("dbrow.winter_reward_4") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.mahogany_logs")
        column(3, 10, 20)
        column(4, 25)
    }
    row("dbrow.winter_reward_5") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.yew_logs")
        column(3, 10, 20)
        column(4, 36)
    }
    row("dbrow.winter_reward_6") {
        column(0, 0)
        columnRSCM(1, "stat.woodcutting")
        columnRSCM(2, "obj.magic_logs")
        column(3, 10, 20)
        column(4, 50)
    }
    row("dbrow.winter_reward_7") {
        column(0, 1)
        columnRSCM(1, "stat.crafting")
        columnRSCM(2, "obj.uncut_sapphire")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_8") {
        column(0, 1)
        columnRSCM(1, "stat.crafting")
        columnRSCM(2, "obj.uncut_emerald")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_9") {
        column(0, 1)
        columnRSCM(1, "stat.crafting")
        columnRSCM(2, "obj.uncut_ruby")
        column(3, 2, 4)
        column(4, 1)
    }
    row("dbrow.winter_reward_10") {
        column(0, 1)
        columnRSCM(1, "stat.crafting")
        columnRSCM(2, "obj.uncut_diamond")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_11") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.blankrune_high")
        column(3, 20, 70)
        column(4, 1)
    }
    row("dbrow.winter_reward_12") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.limestone")
        column(3, 3, 7)
        column(4, 1)
    }
    row("dbrow.winter_reward_13") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.silver_ore")
        column(3, 10, 12)
        column(4, 1)
    }
    row("dbrow.winter_reward_14") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.iron_ore")
        column(3, 5, 15)
        column(4, 1)
    }
    row("dbrow.winter_reward_15") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.coal")
        column(3, 10, 14)
        column(4, 1)
    }
    row("dbrow.winter_reward_16") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.gold_ore")
        column(3, 8, 11)
        column(4, 1)
    }
    row("dbrow.winter_reward_17") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.mithril_ore")
        column(3, 3, 5)
        column(4, 14)
    }
    row("dbrow.winter_reward_18") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.adamantite_ore")
        column(3, 2, 3)
        column(4, 39)
    }
    row("dbrow.winter_reward_19") {
        column(0, 2)
        columnRSCM(1, "stat.mining")
        columnRSCM(2, "obj.runite_ore")
        column(3, 1, 2)
        column(4, 60)
    }
    row("dbrow.winter_reward_20") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_guam")
        column(3, 3, 6)
        column(4, 1)
    }
    row("dbrow.winter_reward_21") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_marentill")
        column(3, 3, 6)
        column(4, 1)
    }
    row("dbrow.winter_reward_22") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_tarromin")
        column(3, 3, 6)
        column(4, 1)
    }
    row("dbrow.winter_reward_23") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_harralander")
        column(3, 3, 6)
        column(4, 1)
    }
    row("dbrow.winter_reward_24") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_ranarr")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_25") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_irit")
        column(3, 3, 5)
        column(4, 1)
    }
    row("dbrow.winter_reward_26") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_avantoe")
        column(3, 3, 5)
        column(4, 1)
    }
    row("dbrow.winter_reward_27") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_kwuarm")
        column(3, 2, 4)
        column(4, 1)
    }
    row("dbrow.winter_reward_28") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_cadantine")
        column(3, 2, 4)
        column(4, 14)
    }
    row("dbrow.winter_reward_29") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_lantadyme")
        column(3, 2, 4)
        column(4, 34)
    }
    row("dbrow.winter_reward_30") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_dwarf_weed")
        column(3, 2, 4)
        column(4, 50)
    }
    row("dbrow.winter_reward_31") {
        column(0, 3)
        columnRSCM(1, "stat.herblore")
        columnRSCM(2, "obj.unidentified_torstol")
        column(3, 1, 3)
        column(4, 64)
    }
    row("dbrow.winter_reward_32") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.acorn")
        column(3, 1, 1)
        column(4, 1)
    }
    row("dbrow.winter_reward_33") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.willow_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_34") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.maple_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_35") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.teak_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_36") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.mahogany_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_37") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.yew_seed")
        column(3, 1, 2)
        column(4, 1)
    }
    row("dbrow.winter_reward_38") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.ranarr_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_39") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.tarromin_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_40") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.harralander_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_41") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.toadflax_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_42") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.irit_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_43") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.avantoe_seed")
        column(3, 1, 3)
        column(4, 1)
    }
    row("dbrow.winter_reward_44") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.kwuarm_seed")
        column(3, 1, 3)
        column(4, 12)
    }
    row("dbrow.winter_reward_45") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.snapdragon_seed")
        column(3, 1, 3)
        column(4, 21)
    }
    row("dbrow.winter_reward_46") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.cadantine_seed")
        column(3, 1, 3)
        column(4, 50)
    }
    row("dbrow.winter_reward_47") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.lantadyme_seed")
        column(3, 1, 3)
        column(4, 67)
    }
    row("dbrow.winter_reward_48") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.dwarf_weed_seed")
        column(3, 1, 3)
        column(4, 71)
    }
    row("dbrow.winter_reward_49") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.watermelon_seed")
        column(3, 3, 7)
        column(4, 7)
    }
    row("dbrow.winter_reward_50") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.snape_grass_seed")
        column(3, 3, 7)
        column(4, 11)
    }
    row("dbrow.winter_reward_51") {
        column(0, 4)
        columnRSCM(1, "stat.farming")
        columnRSCM(2, "obj.spirit_tree_seed")
        column(3, 1, 1)
        column(4, 80)
    }
    row("dbrow.winter_reward_52") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_anchovies")
        column(3, 6, 11)
        column(4, 1)
    }
    row("dbrow.winter_reward_53") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_trout")
        column(3, 6, 11)
        column(4, 1)
    }
    row("dbrow.winter_reward_54") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_salmon")
        column(3, 6, 11)
        column(4, 1)
    }
    row("dbrow.winter_reward_55") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_tuna")
        column(3, 6, 11)
        column(4, 1)
    }
    row("dbrow.winter_reward_56") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_lobster")
        column(3, 6, 11)
        column(4, 15)
    }
    row("dbrow.winter_reward_57") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_swordfish")
        column(3, 6, 11)
        column(4, 34)
    }
    row("dbrow.winter_reward_58") {
        column(0, 5)
        columnRSCM(1, "stat.fishing")
        columnRSCM(2, "obj.raw_shark")
        column(3, 6, 11)
        column(4, 43)
    }
    row("dbrow.winter_reward_59") {
        column(0, 6)
        columnRSCM(1, "stat.firemaking")
        columnRSCM(2, "obj.coins")
        column(3, 2000, 4999)
        column(4, 1)
    }
    row("dbrow.winter_reward_60") {
        column(0, 7)
        columnRSCM(1, "stat.firemaking")
        columnRSCM(2, "obj.hosidius_saltpetre")
        column(3, 3, 5)
        column(4, 1)
    }
    row("dbrow.winter_reward_61") {
        column(0, 8)
        columnRSCM(1, "stat.firemaking")
        columnRSCM(2, "obj.lovakengj_dynamite_fused")
        column(3, 3, 5)
        column(4, 1)
    }
}

internal fun winterWarmItems() = dbTable("dbtable.winter_warm_items", serverOnly = true) {
    column("items", 0, VarType.OBJ)
    row("dbrow.winter_warm_items") {
        columnRSCM(0,
            "obj.ale_gods",
            "obj.aluft_gnome_scarf",
            "obj.antisanta_boots",
            "obj.antisanta_gloves",
            "obj.antisanta_jacket",
            "obj.antisanta_mask",
            "obj.antisanta_pants",
            "obj.bear_slippers",
            "obj.black_santa_hat",
            "obj.br_infernal_cape",
            "obj.bunnyfeet",
            "obj.clue_hunter_boots",
            "obj.clue_hunter_cloak",
            "obj.clue_hunter_garb",
            "obj.clue_hunter_gloves",
            "obj.clue_hunter_trousers",
            "obj.cow_slippers",
            "obj.cow_slippers_recol_1",
            "obj.cow_slippers_recol_2",
            "obj.cow_slippers_recol_3",
            "obj.cow_slippers_recol_4",
            "obj.demon_slippers",
            "obj.easter07_chicken_feet",
            "obj.easter07_chicken_head",
            "obj.easter07_chicken_legs",
            "obj.easter07_chicken_wings",
            "obj.easter16_onsie_bottom",
            "obj.easter16_onsie_gloves",
            "obj.easter16_onsie_top",
            "obj.easter19_bunny_mask",
            "obj.evil_chicken_feet",
            "obj.evil_chicken_head",
            "obj.evil_chicken_legs",
            "obj.evil_chicken_wings",
            "obj.fire_battlestaff",
            "obj.forestry_lumberjack_hat",
            "obj.frog_slippers",
            "obj.holy_moleys",
            "obj.hunting_camoflauge_robe_desert",
            "obj.hunting_camoflauge_robe_desert_worn",
            "obj.hunting_camoflauge_robe_jungle",
            "obj.hunting_camoflauge_robe_jungle_worn",
            "obj.hunting_camoflauge_robe_polar",
            "obj.hunting_camoflauge_robe_polar_worn",
            "obj.hunting_camoflauge_robe_wood",
            "obj.hunting_camoflauge_robe_wood_worn",
            "obj.hunting_hat_jaguar",
            "obj.hunting_hat_leopard",
            "obj.hunting_hat_tiger",
            "obj.hunting_silent_gloves",
            "obj.hunting_torso_jaguar",
            "obj.hunting_torso_leopard",
            "obj.hunting_torso_tiger",
            "obj.hunting_trousers_desert",
            "obj.hunting_trousers_desert_worn",
            "obj.hunting_trousers_jaguar",
            "obj.hunting_trousers_jungle",
            "obj.hunting_trousers_jungle_worn",
            "obj.hunting_trousers_leopard",
            "obj.hunting_trousers_polar",
            "obj.hunting_trousers_polar_worn",
            "obj.hunting_trousers_tiger",
            "obj.hunting_trousers_wood",
            "obj.hunting_trousers_wood_worn",
            "obj.infernal_axe",
            "obj.infernal_axe_empty",
            "obj.infernal_cape",
            "obj.infernal_cape_dummy",
            "obj.infernal_harpoon",
            "obj.infernal_harpoon_empty",
            "obj.infernal_pickaxe",
            "obj.infernal_pickaxe_empty",
            "obj.inverted_santa_hat",
            "obj.lava_battlestaff",
            "obj.lava_battlestaff_pretty",
            "obj.league_6_slayer_helm1",
            "obj.league_6_slayer_helm1_i",
            "obj.league_6_slayer_helm2",
            "obj.league_6_slayer_helm2_i",
            "obj.mdaughter_bear_helmet",
            "obj.mole_slippers",
            "obj.mysterymask02",
            "obj.mysterymask02_reward",
            "obj.mystic_fire_staff",
            "obj.mystic_lava_staff",
            "obj.mystic_lava_staff_pretty",
            "obj.mystic_smoke_battlestaff",
            "obj.mystic_steam_battlestaff",
            "obj.mystic_steam_battlestaff_pretty",
            "obj.osb10_bob_slippers",
            "obj.osb10_dragon_candle",
            "obj.osb10_jad_slippers",
            "obj.osb10_jumper_blue",
            "obj.osb10_jumper_green",
            "obj.osb10_jumper_purple",
            "obj.osb10_jumper_red",
            "obj.osb10_jumper_white",
            "obj.osb10_jumper_yellow",
            "obj.pride17_scarf",
            "obj.pride23_jumper",
            "obj.pride24_cape",
            "obj.pvpa_league_6_slayer_helm1_i",
            "obj.pvpa_league_6_slayer_helm2_i",
            "obj.pvpa_slayer_helm_i",
            "obj.pvpa_slayer_helm_i_araxyte",
            "obj.pvpa_slayer_helm_i_black",
            "obj.pvpa_slayer_helm_i_green",
            "obj.pvpa_slayer_helm_i_hooded",
            "obj.pvpa_slayer_helm_i_hydra",
            "obj.pvpa_slayer_helm_i_jad",
            "obj.pvpa_slayer_helm_i_purple",
            "obj.pvpa_slayer_helm_i_red",
            "obj.pvpa_slayer_helm_i_turquoise",
            "obj.pvpa_slayer_helm_i_twisted",
            "obj.pvpa_slayer_helm_i_verzik",
            "obj.pvpa_slayer_helm_i_zuk",
            "obj.pyromancer_boots",
            "obj.pyromancer_bottom",
            "obj.pyromancer_gloves",
            "obj.pyromancer_hood",
            "obj.pyromancer_top",
            "obj.ramble_lumberjack_hat",
            "obj.ring_of_elements",
            "obj.ring_of_elements_charged",
            "obj.santa_boots",
            "obj.santa_gloves",
            "obj.santa_hat",
            "obj.santa_jacket",
            "obj.santa_mask",
            "obj.santa_pants",
            "obj.skillcape_firemaking",
            "obj.skillcape_firemaking_hood",
            "obj.skillcape_firemaking_trimmed",
            "obj.skillcape_max",
            "obj.skillcape_max_anma",
            "obj.skillcape_max_ardy",
            "obj.skillcape_max_assembler",
            "obj.skillcape_max_assembler_masori",
            "obj.skillcape_max_dizanas",
            "obj.skillcape_max_firecape",
            "obj.skillcape_max_firecape_dummy",
            "obj.skillcape_max_guthix",
            "obj.skillcape_max_guthix2",
            "obj.skillcape_max_hood_firecape",
            "obj.skillcape_max_hood_infernalcape",
            "obj.skillcape_max_infernalcape",
            "obj.skillcape_max_infernalcape_dummy",
            "obj.skillcape_max_mythical",
            "obj.skillcape_max_saradomin",
            "obj.skillcape_max_saradomin2",
            "obj.skillcape_max_worn",
            "obj.skillcape_max_zamorak",
            "obj.skillcape_max_zamorak2",
            "obj.slayer_buglan_on",
            "obj.slayer_earmuffs",
            "obj.slayer_helm",
            "obj.slayer_helm_araxyte",
            "obj.slayer_helm_black",
            "obj.slayer_helm_green",
            "obj.slayer_helm_hooded",
            "obj.slayer_helm_hydra",
            "obj.slayer_helm_i",
            "obj.slayer_helm_i_araxyte",
            "obj.slayer_helm_i_black",
            "obj.slayer_helm_i_green",
            "obj.slayer_helm_i_hooded",
            "obj.slayer_helm_i_hydra",
            "obj.slayer_helm_i_jad",
            "obj.slayer_helm_i_purple",
            "obj.slayer_helm_i_red",
            "obj.slayer_helm_i_turquoise",
            "obj.slayer_helm_i_twisted",
            "obj.slayer_helm_i_verzik",
            "obj.slayer_helm_i_zuk",
            "obj.slayer_helm_jad",
            "obj.slayer_helm_purple",
            "obj.slayer_helm_red",
            "obj.slayer_helm_turquoise",
            "obj.slayer_helm_twisted",
            "obj.slayer_helm_verzik",
            "obj.slayer_helm_zuk",
            "obj.smoke_battlestaff",
            "obj.staff_of_fire",
            "obj.steam_battlestaff",
            "obj.steam_battlestaff_pretty",
            "obj.sw_league_6_slayer_helm1_i",
            "obj.sw_league_6_slayer_helm2_i",
            "obj.sw_slayer_helm_i",
            "obj.sw_slayer_helm_i_araxyte",
            "obj.sw_slayer_helm_i_black",
            "obj.sw_slayer_helm_i_green",
            "obj.sw_slayer_helm_i_hooded",
            "obj.sw_slayer_helm_i_hydra",
            "obj.sw_slayer_helm_i_jad",
            "obj.sw_slayer_helm_i_purple",
            "obj.sw_slayer_helm_i_red",
            "obj.sw_slayer_helm_i_turquoise",
            "obj.sw_slayer_helm_i_twisted",
            "obj.sw_slayer_helm_i_verzik",
            "obj.sw_slayer_helm_i_zuk",
            "obj.tiara_elemental",
            "obj.tiara_fire",
            "obj.trailblazer_axe",
            "obj.trailblazer_axe_empty",
            "obj.trailblazer_harpoon",
            "obj.trailblazer_harpoon_empty",
            "obj.trailblazer_pickaxe",
            "obj.trailblazer_pickaxe_empty",
            "obj.trailblazer_reloaded_axe",
            "obj.trailblazer_reloaded_axe_empty",
            "obj.trailblazer_reloaded_harpoon",
            "obj.trailblazer_reloaded_harpoon_empty",
            "obj.trailblazer_reloaded_pickaxe",
            "obj.trailblazer_reloaded_pickaxe_empty",
            "obj.twinflame_staff",
            "obj.tzhaar_cape_fire",
            "obj.tzhaar_cape_fire_dummy",
            "obj.tzhaar_cape_obsidian",
            "obj.tzhaar_cape_obsidian_r",
            "obj.vikinggloves",
            "obj.win05_hat1",
            "obj.win05_hat2",
            "obj.win05_hat3",
            "obj.win05_hat4",
            "obj.win05_scarf1",
            "obj.win05_scarf2",
            "obj.win05_scarf3",
            "obj.win05_scarf4",
            "obj.wint_torch",
            "obj.wint_torch_offhand",
            "obj.wolf_cloak",
            "obj.wolf_mask",
            "obj.wolfengloves_crimson",
            "obj.wolfengloves_grey",
            "obj.wolfengloves_ocean",
            "obj.wolfengloves_purple",
            "obj.wolfengloves_tangerine",
            "obj.xm21_festive_hat",
            "obj.xm21_festive_slippers",
            "obj.xmas22_goggles",
            "obj.xmas22_jumper",
            "obj.xmas22_santa_crown",
            "obj.xmas23_helmet",
            "obj.xmas23_jumper",
            "obj.xmas24_festive_scarf",
            "obj.zep_bomber_cap",
            "obj.zep_bomber_cap_goggles",
            "obj.zep_bomber_jacket",
        )
    }
}

internal fun winterUniques() = dbTable("dbtable.winter_uniques", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("items", 1, VarType.OBJ)
    column("chance", 2, VarType.INT)
    column("quantity", 3, VarType.INT)
    column("cap", 4, VarType.INT)
    column("replacement", 5, VarType.OBJ)
    column("replacement_quantity", 6, VarType.INT)
    row("dbrow.winter_unique_0") {
        column(0, 0)
        columnRSCM(1, "obj.phoenixpet")
        column(2, 5000)
        column(3, 1, 1)
        column(4, 0)
        columnRSCM(5, "obj.phoenixpet")
        column(6, 1, 1)
    }
    row("dbrow.winter_unique_1") {
        column(0, 1)
        columnRSCM(1, "obj.dragon_axe")
        column(2, 10000)
        column(3, 1, 1)
        column(4, 0)
        columnRSCM(5, "obj.dragon_axe")
        column(6, 1, 1)
    }
    row("dbrow.winter_unique_2") {
        column(0, 2)
        columnRSCM(1, "obj.tome_of_fire_uncharged")
        column(2, 1000)
        column(3, 1, 1)
        column(4, 0)
        columnRSCM(5, "obj.tome_of_fire_uncharged")
        column(6, 1, 1)
    }
    row("dbrow.winter_unique_3") {
        column(0, 3)
        columnRSCM(1, "obj.pyromancer_gloves")
        column(2, 150)
        column(3, 1, 1)
        column(4, 3)
        columnRSCM(5, "obj.magic_tree_seed")
        column(6, 1, 1)
    }
    row("dbrow.winter_unique_4") {
        column(0, 4)
        columnRSCM(1, "obj.wint_torch")
        column(2, 150)
        column(3, 1, 1)
        column(4, 3)
        columnRSCM(5, "obj.torstol_seed")
        column(6, 2, 3)
    }
    row("dbrow.winter_unique_5") {
        column(0, 5)
        columnRSCM(1, "obj.pyromancer_top", "obj.pyromancer_hood", "obj.pyromancer_bottom", "obj.pyromancer_boots")
        column(2, 150)
        column(3, 1, 1)
        column(4, 0)
        columnRSCM(5, "obj.pyromancer_top")
        column(6, 1, 1)
    }
    row("dbrow.winter_unique_6") {
        column(0, 6)
        columnRSCM(1, "obj.wint_burnt_page")
        column(2, 45)
        column(3, 7, 29)
        column(4, 0)
        columnRSCM(5, "obj.wint_burnt_page")
        column(6, 7, 29)
    }
}
