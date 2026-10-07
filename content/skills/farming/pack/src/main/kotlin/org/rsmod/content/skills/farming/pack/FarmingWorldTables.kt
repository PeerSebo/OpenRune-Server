package org.rsmod.content.skills.farming.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun farmingPatchTypes() = dbTable("dbtable.farming_patch_types", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("seed_family", 2, VarType.INT)
    column("waterable", 3, VarType.BOOLEAN)
    column("cure", 4, VarType.STRING)
    column("check_health", 5, VarType.BOOLEAN)
    column("bears_produce", 6, VarType.BOOLEAN)
    column("empty", 7, VarType.LOC)
    column("weed_cycle_minutes", 8, VarType.INT)
    val families = listOf(
        PatchFamily("ALLOTMENT", 1, true, "CURE", false, false, "loc.veg_patch_weeded", 10),
        PatchFamily("FLOWER", 2, true, "CURE", false, false, "loc.flower_patch_weeded", 5),
        PatchFamily("HERB", 3, false, "CURE", false, false, "loc.herb_patch_weeded", 20),
        PatchFamily("HOPS", 4, true, "CURE", false, false, "loc.hop_patch_weeded", 10),
        PatchFamily("BUSH", 5, false, "PRUNE", true, true, "loc.bush_patch_weeded", 20),
        PatchFamily("TREE", 6, false, "PRUNE", true, false, "loc.tree_patch_weeded", 40),
        PatchFamily("FRUIT_TREE", 7, false, "PRUNE", true, true, "loc.fruit_tree_patch_weeded", 160),
    )
    for ((order, family) in families.withIndex()) {
        row("dbrow.farming_patch_type_${family.key.lowercase()}") {
            column(0, order)
            column(1, family.key)
            column(2, family.seedFamily)
            column(3, if (family.waterable) 1 else 0)
            column(4, family.cure)
            column(5, if (family.checkHealth) 1 else 0)
            column(6, if (family.bearsProduce) 1 else 0)
            columnRSCM(7, family.empty)
            column(8, family.weedCycleMinutes)
        }
    }
}

internal fun farmingPatches() = dbTable("dbtable.farming_patches", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("loc_name", 1, VarType.STRING)
    column("loc", 2, VarType.LOC)
    column("family", 3, VarType.STRING)
    val patches = listOf(
        "loc.farming_veg_patch_1" to "ALLOTMENT",
        "loc.farming_veg_patch_2" to "ALLOTMENT",
        "loc.farming_veg_patch_3" to "ALLOTMENT",
        "loc.farming_veg_patch_4" to "ALLOTMENT",
        "loc.farming_veg_patch_5" to "ALLOTMENT",
        "loc.farming_veg_patch_6" to "ALLOTMENT",
        "loc.farming_veg_patch_7" to "ALLOTMENT",
        "loc.farming_veg_patch_8" to "ALLOTMENT",
        "loc.farming_veg_patch_9" to "ALLOTMENT",
        "loc.farming_veg_patch_10" to "ALLOTMENT",
        "loc.farming_veg_patch_11" to "ALLOTMENT",
        "loc.farming_veg_patch_12" to "ALLOTMENT",
        "loc.farming_veg_patch_13" to "ALLOTMENT",
        "loc.farming_veg_patch_14" to "ALLOTMENT",
        "loc.farming_veg_patch_15" to "ALLOTMENT",
        "loc.farming_veg_patch_16" to "ALLOTMENT",
        "loc.farming_veg_patch_17" to "ALLOTMENT",
        "loc.farming_herb_patch_1" to "HERB",
        "loc.farming_herb_patch_2" to "HERB",
        "loc.farming_herb_patch_3" to "HERB",
        "loc.farming_herb_patch_4" to "HERB",
        "loc.farming_herb_patch_5" to "HERB",
        "loc.farming_herb_patch_6" to "HERB",
        "loc.farming_herb_patch_7" to "HERB",
        "loc.farming_herb_patch_8" to "HERB",
        "loc.farming_flower_patch_1" to "FLOWER",
        "loc.farming_flower_patch_2" to "FLOWER",
        "loc.farming_flower_patch_3" to "FLOWER",
        "loc.farming_flower_patch_4" to "FLOWER",
        "loc.farming_flower_patch_5" to "FLOWER",
        "loc.farming_flower_patch_6" to "FLOWER",
        "loc.farming_flower_patch_7" to "FLOWER",
        "loc.farming_flower_patch_8" to "FLOWER",
        "loc.farming_flower_patch_9" to "FLOWER",
        "loc.farming_hops_patch_1" to "HOPS",
        "loc.farming_hops_patch_2" to "HOPS",
        "loc.farming_hops_patch_3" to "HOPS",
        "loc.farming_hops_patch_4" to "HOPS",
        "loc.farming_hops_patch_5" to "HOPS",
        "loc.farming_hops_patch_keldagrim" to "HOPS",
        "loc.farming_bush_patch_1" to "BUSH",
        "loc.farming_bush_patch_2" to "BUSH",
        "loc.farming_bush_patch_3" to "BUSH",
        "loc.farming_bush_patch_4" to "BUSH",
        "loc.farming_bush_patch_5" to "BUSH",
        "loc.farming_tree_patch_1" to "TREE",
        "loc.farming_tree_patch_2" to "TREE",
        "loc.farming_tree_patch_3" to "TREE",
        "loc.farming_tree_patch_4" to "TREE",
        "loc.farming_tree_patch_5" to "TREE",
        "loc.farming_tree_patch_6" to "TREE",
        "loc.farming_tree_patch_7" to "TREE",
        "loc.farming_fruit_tree_patch_1" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_2" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_3" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_4" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_5" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_6" to "FRUIT_TREE",
        "loc.farming_fruit_tree_patch_7" to "FRUIT_TREE",
    )
    for ((order, patch) in patches.withIndex()) {
        row("dbrow.${patch.first.removePrefix("loc.")}") {
            column(0, order)
            column(1, patch.first)
            columnRSCM(2, patch.first)
            column(3, patch.second)
        }
    }
}

internal fun farmingBinTypes() = dbTable("dbtable.farming_bin_types", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("loc_prefix", 2, VarType.STRING)
    column("capacity", 3, VarType.INT)
    val families = listOf(
        Triple("STANDARD", "loc.compost_bin_", 15),
        Triple("MORYTANIA", "loc.compost_bin_dark_", 15),
        Triple("FARMING_GUILD", "loc.compost_bigbin_", 30),
    )
    for ((order, family) in families.withIndex()) {
        row("dbrow.farming_bin_type_${family.first.lowercase()}") {
            column(0, order)
            column(1, family.first)
            column(2, family.second)
            column(3, family.third)
        }
    }
}

internal fun farmingBins() = dbTable("dbtable.farming_bins", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("loc_name", 1, VarType.STRING)
    column("loc", 2, VarType.LOC)
    column("family", 3, VarType.STRING)
    val bins = listOf(
        "loc.farming_compost_bin_1" to "STANDARD",
        "loc.farming_compost_bin_2" to "STANDARD",
        "loc.farming_compost_bin_3" to "MORYTANIA",
        "loc.farming_compost_bin_4" to "STANDARD",
        "loc.farming_compost_bin_5" to "STANDARD",
        "loc.farming_compost_bin_6" to "STANDARD",
        "loc.farming_compost_bin_7" to "STANDARD",
        "loc.farming_compost_bigbin_guild" to "FARMING_GUILD",
    )
    for ((order, bin) in bins.withIndex()) {
        row("dbrow.${bin.first.removePrefix("loc.")}") {
            column(0, order)
            column(1, bin.first)
            columnRSCM(2, bin.first)
            column(3, bin.second)
        }
    }
}

internal fun farmingCompostTypes() = dbTable("dbtable.farming_compost_types", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("extra_lives", 2, VarType.INT)
    column("disease_divisor", 3, VarType.INT)
    val compost = listOf(
        Triple("NONE", 0, 1),
        Triple("COMPOST", 1, 2),
        Triple("SUPERCOMPOST", 2, 5),
        Triple("ULTRACOMPOST", 3, 10),
    )
    for ((order, type) in compost.withIndex()) {
        row("dbrow.farming_compost_type_${type.first.lowercase()}") {
            column(0, order)
            column(1, type.first)
            column(2, type.second)
            column(3, type.third)
        }
    }
}

private data class PatchFamily(
    val key: String,
    val seedFamily: Int,
    val waterable: Boolean,
    val cure: String,
    val checkHealth: Boolean,
    val bearsProduce: Boolean,
    val empty: String,
    val weedCycleMinutes: Int,
)
