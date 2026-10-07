package org.rsmod.content.skills.farming.pack

import dev.openrune.ServerCacheManager
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun farmingLeprechaunSlots() = dbTable("dbtable.farming_leprechaun_slots", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("component", 1, VarType.COMPONENT)
    column("item", 2, VarType.OBJ)
    column("fields", 3, ServerCacheManager.VARBIT)
    column("published_cap", 4, VarType.INT)
    row("dbrow.farming_leprechaun_slots_rake") {
        column(0, 0)
        columnRSCM(1, "component.farming_tools:rake")
        columnRSCM(2, "obj.rake")
        columnRSCM(3, "varbit.farming_tools_rake", "varbit.farming_tools_extrarakes")
        column(4, 100)
    }
    row("dbrow.farming_leprechaun_slots_dibber") {
        column(0, 1)
        columnRSCM(1, "component.farming_tools:dibber")
        columnRSCM(2, "obj.dibber")
        columnRSCM(3, "varbit.farming_tools_dibber", "varbit.farming_tools_extradibbers")
        column(4, 100)
    }
    row("dbrow.farming_leprechaun_slots_spade") {
        column(0, 2)
        columnRSCM(1, "component.farming_tools:spade")
        columnRSCM(2, "obj.spade")
        columnRSCM(3, "varbit.farming_tools_spade", "varbit.farming_tools_extraspades")
        column(4, 100)
    }
    row("dbrow.farming_leprechaun_slots_secateurs") {
        column(0, 3)
        columnRSCM(1, "component.farming_tools:secateurs")
        columnRSCM(2, "obj.secateurs")
        columnRSCM(3, "varbit.farming_tools_secateurs", "varbit.farming_tools_extrasecateurs")
        column(4, 100)
    }
    row("dbrow.farming_leprechaun_slots_trowel") {
        column(0, 4)
        columnRSCM(1, "component.farming_tools:trowel")
        columnRSCM(2, "obj.gardening_trowel")
        columnRSCM(3, "varbit.farming_tools_trowel", "varbit.farming_tools_extratrowels")
        column(4, 100)
    }
    row("dbrow.farming_leprechaun_slots_bucket") {
        column(0, 5)
        columnRSCM(1, "component.farming_tools:bucket")
        columnRSCM(2, "obj.bucket_empty")
        columnRSCM(3, "varbit.farming_tools_buckets", "varbit.farming_tools_extrabuckets", "varbit.farming_tools_extra2buckets")
        column(4, 1000)
    }
    row("dbrow.farming_leprechaun_slots_compost") {
        column(0, 6)
        columnRSCM(1, "component.farming_tools:compost")
        columnRSCM(2, "obj.bucket_compost")
        columnRSCM(3, "varbit.farming_tools_compost", "varbit.farming_tools_extracompost")
        column(4, 1000)
    }
    row("dbrow.farming_leprechaun_slots_supercompost") {
        column(0, 7)
        columnRSCM(1, "component.farming_tools:supercompost")
        columnRSCM(2, "obj.bucket_supercompost")
        columnRSCM(3, "varbit.farming_tools_supercompost", "varbit.farming_tools_extrasupercompost")
        column(4, 1000)
    }
    row("dbrow.farming_leprechaun_slots_ultracompost") {
        column(0, 8)
        columnRSCM(1, "component.farming_tools:ultracompost")
        columnRSCM(2, "obj.bucket_ultracompost")
        columnRSCM(3, "varbit.farming_tools_ultracompost")
        column(4, 1000)
    }
    row("dbrow.farming_leprechaun_slots_plantcure") {
        column(0, 9)
        columnRSCM(1, "component.farming_tools:plantcure")
        columnRSCM(2, "obj.plant_cure")
        columnRSCM(3, "varbit.farming_tools_plantcure")
        column(4, 1000)
    }
}

internal fun farmingCompanionRules() = dbTable("dbtable.farming_companion_rules", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("crop", 1, VarType.STRING)
    column("protects_all", 2, VarType.BOOLEAN)
    column("allotments", 3, VarType.STRING)
    row("dbrow.farming_companion_rules_marigold") {
        column(0, 0)
        column(1, "marigold")
        column(2, 0)
        column(3, "potato", "onion", "tomato")
    }
    row("dbrow.farming_companion_rules_rosemary") {
        column(0, 1)
        column(1, "rosemary")
        column(2, 0)
        column(3, "cabbage")
    }
    row("dbrow.farming_companion_rules_nasturtium") {
        column(0, 2)
        column(1, "nasturtium")
        column(2, 0)
        column(3, "watermelon")
    }
    row("dbrow.farming_companion_rules_white_lily") {
        column(0, 3)
        column(1, "white_lily")
        column(2, 1)
    }
    row("dbrow.farming_companion_rules_scarecrow") {
        column(0, 4)
        column(1, "scarecrow")
        column(2, 0)
        column(3, "sweetcorn")
    }
}

internal fun farmingPatchGroups() = dbTable("dbtable.farming_patch_groups", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("flower", 1, VarType.STRING)
    column("allotments", 2, VarType.STRING)
    row("dbrow.farming_patch_groups_farming_flower_patch_1") {
        column(0, 0)
        column(1, "farming_flower_patch_1")
        column(2, "farming_veg_patch_1", "farming_veg_patch_2")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_2") {
        column(0, 1)
        column(1, "farming_flower_patch_2")
        column(2, "farming_veg_patch_3", "farming_veg_patch_4")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_3") {
        column(0, 2)
        column(1, "farming_flower_patch_3")
        column(2, "farming_veg_patch_5", "farming_veg_patch_6")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_4") {
        column(0, 3)
        column(1, "farming_flower_patch_4")
        column(2, "farming_veg_patch_7", "farming_veg_patch_8")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_5") {
        column(0, 4)
        column(1, "farming_flower_patch_5")
        column(2, "farming_veg_patch_10", "farming_veg_patch_11")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_6") {
        column(0, 5)
        column(1, "farming_flower_patch_6")
        column(2, "farming_veg_patch_12", "farming_veg_patch_13")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_7") {
        column(0, 6)
        column(1, "farming_flower_patch_7")
        column(2, "farming_veg_patch_14", "farming_veg_patch_15")
    }
    row("dbrow.farming_patch_groups_farming_flower_patch_8") {
        column(0, 7)
        column(1, "farming_flower_patch_8")
        column(2, "farming_veg_patch_16", "farming_veg_patch_17")
    }
}

internal fun farmingGardeners() = dbTable("dbtable.farming_gardeners", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("npc", 1, VarType.NPC)
    column("choice", 2, VarType.BOOLEAN)
    column("patches", 3, VarType.STRING)
    row("dbrow.farming_gardeners_elstan") {
        column(0, 0)
        columnRSCM(1, "npc.elstan")
        column(2, 0)
        column(3, "farming_veg_patch_1", "farming_veg_patch_2")
    }
    row("dbrow.farming_gardeners_dantaera") {
        column(0, 1)
        columnRSCM(1, "npc.dantaera")
        column(2, 0)
        column(3, "farming_veg_patch_3", "farming_veg_patch_4")
    }
    row("dbrow.farming_gardeners_kragen") {
        column(0, 2)
        columnRSCM(1, "npc.kragen")
        column(2, 0)
        column(3, "farming_veg_patch_5", "farming_veg_patch_6")
    }
    row("dbrow.farming_gardeners_lyra") {
        column(0, 3)
        columnRSCM(1, "npc.lyra")
        column(2, 0)
        column(3, "farming_veg_patch_7", "farming_veg_patch_8")
    }
    row("dbrow.farming_gardeners_hosidius_allotment_gardener") {
        column(0, 4)
        columnRSCM(1, "npc.hosidius_allotment_gardener")
        column(2, 0)
        column(3, "farming_veg_patch_10", "farming_veg_patch_11")
    }
    row("dbrow.farming_gardeners_prif_gardener") {
        column(0, 5)
        columnRSCM(1, "npc.prif_gardener")
        column(2, 0)
        column(3, "farming_veg_patch_14", "farming_veg_patch_15")
    }
    row("dbrow.farming_gardeners_fortis_gardener") {
        column(0, 6)
        columnRSCM(1, "npc.fortis_gardener")
        column(2, 0)
        column(3, "farming_veg_patch_16", "farming_veg_patch_17")
    }
    row("dbrow.farming_gardeners_farming_gardener_hops_1") {
        column(0, 7)
        columnRSCM(1, "npc.farming_gardener_hops_1")
        column(2, 0)
        column(3, "farming_hops_patch_1")
    }
    row("dbrow.farming_gardeners_farming_gardener_hops_3") {
        column(0, 8)
        columnRSCM(1, "npc.farming_gardener_hops_3")
        column(2, 0)
        column(3, "farming_hops_patch_3")
    }
    row("dbrow.farming_gardeners_farming_gardener_hops_4") {
        column(0, 9)
        columnRSCM(1, "npc.farming_gardener_hops_4")
        column(2, 0)
        column(3, "farming_hops_patch_4")
    }
    row("dbrow.farming_gardeners_farming_gardener_hops_5") {
        column(0, 10)
        columnRSCM(1, "npc.farming_gardener_hops_5")
        column(2, 0)
        column(3, "farming_hops_patch_5")
    }
    row("dbrow.farming_gardeners_farming_gardener_bush_1") {
        column(0, 11)
        columnRSCM(1, "npc.farming_gardener_bush_1")
        column(2, 0)
        column(3, "farming_bush_patch_1")
    }
    row("dbrow.farming_gardeners_farming_gardener_bush_2") {
        column(0, 12)
        columnRSCM(1, "npc.farming_gardener_bush_2")
        column(2, 0)
        column(3, "farming_bush_patch_2")
    }
    row("dbrow.farming_gardeners_farming_gardener_bush_3") {
        column(0, 13)
        columnRSCM(1, "npc.farming_gardener_bush_3")
        column(2, 0)
        column(3, "farming_bush_patch_3")
    }
    row("dbrow.farming_gardeners_farming_gardener_bush_4") {
        column(0, 14)
        columnRSCM(1, "npc.farming_gardener_bush_4")
        column(2, 0)
        column(3, "farming_bush_patch_4")
    }
    row("dbrow.farming_gardeners_farming_gardener_fruit_1") {
        column(0, 15)
        columnRSCM(1, "npc.farming_gardener_fruit_1")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_1")
    }
    row("dbrow.farming_gardeners_farming_gardener_fruit_2") {
        column(0, 16)
        columnRSCM(1, "npc.farming_gardener_fruit_2")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_2")
    }
    row("dbrow.farming_gardeners_garth") {
        column(0, 17)
        columnRSCM(1, "npc.garth")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_3")
    }
    row("dbrow.farming_gardeners_farming_gardener_fruit_4") {
        column(0, 18)
        columnRSCM(1, "npc.farming_gardener_fruit_4")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_4")
    }
    row("dbrow.farming_gardeners_farming_gardener_fruit_tree_5") {
        column(0, 19)
        columnRSCM(1, "npc.farming_gardener_fruit_tree_5")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_5")
    }
    row("dbrow.farming_gardeners_farming_gardener_farmguild_t3") {
        column(0, 20)
        columnRSCM(1, "npc.farming_gardener_farmguild_t3")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_6")
    }
    row("dbrow.farming_gardeners_farming_gardener_fruit_7") {
        column(0, 21)
        columnRSCM(1, "npc.farming_gardener_fruit_7")
        column(2, 0)
        column(3, "farming_fruit_tree_patch_7")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_1") {
        column(0, 22)
        columnRSCM(1, "npc.farming_gardener_tree_1")
        column(2, 0)
        column(3, "farming_tree_patch_1")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_2") {
        column(0, 23)
        columnRSCM(1, "npc.farming_gardener_tree_2")
        column(2, 0)
        column(3, "farming_tree_patch_2")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_3_02") {
        column(0, 24)
        columnRSCM(1, "npc.farming_gardener_tree_3_02")
        column(2, 0)
        column(3, "farming_tree_patch_3")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_4") {
        column(0, 25)
        columnRSCM(1, "npc.farming_gardener_tree_4")
        column(2, 0)
        column(3, "farming_tree_patch_4")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_gnome") {
        column(0, 26)
        columnRSCM(1, "npc.farming_gardener_tree_gnome")
        column(2, 0)
        column(3, "farming_tree_patch_5")
    }
    row("dbrow.farming_gardeners_farming_gardener_farmguild_t2") {
        column(0, 27)
        columnRSCM(1, "npc.farming_gardener_farmguild_t2")
        column(2, 0)
        column(3, "farming_tree_patch_6")
    }
    row("dbrow.farming_gardeners_farming_gardener_tree_7") {
        column(0, 28)
        columnRSCM(1, "npc.farming_gardener_tree_7")
        column(2, 0)
        column(3, "farming_tree_patch_7")
    }
    row("dbrow.farming_gardeners_farming_gardener_farmguild_t1") {
        column(0, 29)
        columnRSCM(1, "npc.farming_gardener_farmguild_t1")
        column(2, 1)
        column(3, "farming_veg_patch_12", "farming_veg_patch_13")
    }
}

internal fun farmingLeprechaunNoting() = dbTable("dbtable.farming_leprechaun_noting", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("item", 1, VarType.OBJ)
    column("allowed", 2, VarType.BOOLEAN)
    row("dbrow.farming_leprechaun_noting_guam_leaf") {
        column(0, 0)
        columnRSCM(1, "obj.guam_leaf")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_marentill") {
        column(0, 1)
        columnRSCM(1, "obj.marentill")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_tarromin") {
        column(0, 2)
        columnRSCM(1, "obj.tarromin")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_harralander") {
        column(0, 3)
        columnRSCM(1, "obj.harralander")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_ranarr_weed") {
        column(0, 4)
        columnRSCM(1, "obj.ranarr_weed")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_irit_leaf") {
        column(0, 5)
        columnRSCM(1, "obj.irit_leaf")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_avantoe") {
        column(0, 6)
        columnRSCM(1, "obj.avantoe")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_kwuarm") {
        column(0, 7)
        columnRSCM(1, "obj.kwuarm")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_cadantine") {
        column(0, 8)
        columnRSCM(1, "obj.cadantine")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_lantadyme") {
        column(0, 9)
        columnRSCM(1, "obj.lantadyme")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_dwarf_weed") {
        column(0, 10)
        columnRSCM(1, "obj.dwarf_weed")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_torstol") {
        column(0, 11)
        columnRSCM(1, "obj.torstol")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_toadflax") {
        column(0, 12)
        columnRSCM(1, "obj.toadflax")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_snapdragon") {
        column(0, 13)
        columnRSCM(1, "obj.snapdragon")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_huasca") {
        column(0, 14)
        columnRSCM(1, "obj.huasca")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_oak_roots") {
        column(0, 15)
        columnRSCM(1, "obj.oak_roots")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_willow_roots") {
        column(0, 16)
        columnRSCM(1, "obj.willow_roots")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_maple_roots") {
        column(0, 17)
        columnRSCM(1, "obj.maple_roots")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_yew_roots") {
        column(0, 18)
        columnRSCM(1, "obj.yew_roots")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_magic_roots") {
        column(0, 19)
        columnRSCM(1, "obj.magic_roots")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_weeds") {
        column(0, 20)
        columnRSCM(1, "obj.weeds")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_willow_branch") {
        column(0, 21)
        columnRSCM(1, "obj.willow_branch")
        column(2, 1)
    }
    row("dbrow.farming_leprechaun_noting_cabbage") {
        column(0, 22)
        columnRSCM(1, "obj.cabbage")
        column(2, 0)
    }
}

internal fun farmingCompostIngredients() = dbTable("dbtable.farming_compost_ingredients", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("item", 1, VarType.OBJ)
    column("fill", 2, VarType.STRING)
    column("before_crops", 3, VarType.BOOLEAN)
    row("dbrow.farming_compost_ingredients_tomato") {
        column(0, 0)
        columnRSCM(1, "obj.tomato")
        column(2, "TOMATOES")
        column(3, 1)
    }
    row("dbrow.farming_compost_ingredients_weeds") {
        column(0, 1)
        columnRSCM(1, "obj.weeds")
        column(2, "COMPOSTABLE")
        column(3, 1)
    }
    row("dbrow.farming_compost_ingredients_cotton_boll") {
        column(0, 2)
        columnRSCM(1, "obj.cotton_boll")
        column(2, "COMPOSTABLE")
        column(3, 0)
    }
    row("dbrow.farming_compost_ingredients_cactus_potato") {
        column(0, 3)
        columnRSCM(1, "obj.cactus_potato")
        column(2, "COMPOSTABLE")
        column(3, 0)
    }
    row("dbrow.farming_compost_ingredients_unidentified_toadflax") {
        column(0, 4)
        columnRSCM(1, "obj.unidentified_toadflax")
        column(2, "SUPERCOMPOSTABLE")
        column(3, 0)
    }
    row("dbrow.farming_compost_ingredients_garden_white_tree_fruit") {
        column(0, 5)
        columnRSCM(1, "obj.garden_white_tree_fruit")
        column(2, "SUPERCOMPOSTABLE")
        column(3, 0)
    }
}

internal fun farmingConsumableDoses() = dbTable("dbtable.farming_consumable_doses", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("family", 1, VarType.STRING)
    column("dose", 2, VarType.INT)
    column("item", 3, VarType.OBJ)
    row("dbrow.farming_consumable_doses_watering_can_0") {
        column(0, 0)
        column(1, "watering_can")
        column(2, 0)
        columnRSCM(3, "obj.watering_can_0")
    }
    row("dbrow.farming_consumable_doses_watering_can_1") {
        column(0, 1)
        column(1, "watering_can")
        column(2, 1)
        columnRSCM(3, "obj.watering_can_1")
    }
    row("dbrow.farming_consumable_doses_watering_can_2") {
        column(0, 2)
        column(1, "watering_can")
        column(2, 2)
        columnRSCM(3, "obj.watering_can_2")
    }
    row("dbrow.farming_consumable_doses_watering_can_3") {
        column(0, 3)
        column(1, "watering_can")
        column(2, 3)
        columnRSCM(3, "obj.watering_can_3")
    }
    row("dbrow.farming_consumable_doses_watering_can_4") {
        column(0, 4)
        column(1, "watering_can")
        column(2, 4)
        columnRSCM(3, "obj.watering_can_4")
    }
    row("dbrow.farming_consumable_doses_watering_can_5") {
        column(0, 5)
        column(1, "watering_can")
        column(2, 5)
        columnRSCM(3, "obj.watering_can_5")
    }
    row("dbrow.farming_consumable_doses_watering_can_6") {
        column(0, 6)
        column(1, "watering_can")
        column(2, 6)
        columnRSCM(3, "obj.watering_can_6")
    }
    row("dbrow.farming_consumable_doses_watering_can_7") {
        column(0, 7)
        column(1, "watering_can")
        column(2, 7)
        columnRSCM(3, "obj.watering_can_7")
    }
    row("dbrow.farming_consumable_doses_watering_can_8") {
        column(0, 8)
        column(1, "watering_can")
        column(2, 8)
        columnRSCM(3, "obj.watering_can_8")
    }
    row("dbrow.farming_consumable_doses_supercompost_potion_0") {
        column(0, 9)
        column(1, "supercompost_potion")
        column(2, 0)
        columnRSCM(3, "obj.vial_empty")
    }
    row("dbrow.farming_consumable_doses_supercompost_potion_1") {
        column(0, 10)
        column(1, "supercompost_potion")
        column(2, 1)
        columnRSCM(3, "obj.supercompost_potion_1")
    }
    row("dbrow.farming_consumable_doses_supercompost_potion_2") {
        column(0, 11)
        column(1, "supercompost_potion")
        column(2, 2)
        columnRSCM(3, "obj.supercompost_potion_2")
    }
    row("dbrow.farming_consumable_doses_supercompost_potion_3") {
        column(0, 12)
        column(1, "supercompost_potion")
        column(2, 3)
        columnRSCM(3, "obj.supercompost_potion_3")
    }
    row("dbrow.farming_consumable_doses_supercompost_potion_4") {
        column(0, 13)
        column(1, "supercompost_potion")
        column(2, 4)
        columnRSCM(3, "obj.supercompost_potion_4")
    }
}

internal fun farmingCompostProducts() = dbTable("dbtable.farming_compost_products", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("product", 1, VarType.STRING)
    column("item", 2, VarType.OBJ)
    column("bin_xp_tenths", 3, VarType.INT)
    column("application_xp_tenths", 4, VarType.INT)
    row("dbrow.farming_compost_products_COMPOST") {
        column(0, 0)
        column(1, "COMPOST")
        columnRSCM(2, "obj.bucket_compost")
        column(3, 45)
        column(4, 180)
    }
    row("dbrow.farming_compost_products_SUPERCOMPOST") {
        column(0, 1)
        column(1, "SUPERCOMPOST")
        columnRSCM(2, "obj.bucket_supercompost")
        column(3, 85)
        column(4, 260)
    }
    row("dbrow.farming_compost_products_ULTRACOMPOST") {
        column(0, 2)
        column(1, "ULTRACOMPOST")
        columnRSCM(2, "obj.bucket_ultracompost")
        column(3, 100)
        column(4, 360)
    }
    row("dbrow.farming_compost_products_ROTTEN_TOMATOES") {
        column(0, 3)
        column(1, "ROTTEN_TOMATOES")
        columnRSCM(2, "obj.rotten_tomato")
        column(3, 45)
    }
}
