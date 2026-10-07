package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun foundryNativeCatalogue() = dbTable("dbtable.giants_foundry_native_catalogue", serverOnly = true) {
    column("words", 0, VarType.ENUM)
    column("forte", 1, VarType.ENUM)
    column("blade", 2, VarType.ENUM)
    column("tip", 3, VarType.ENUM)
    column("rewards", 4, VarType.ENUM)
    column("mould_name_param", 5, VarType.INT)
    column("mould_level_param", 6, VarType.INT)
    column("mould_width_param", 7, VarType.INT)
    column("mould_weight_param", 8, VarType.INT)
    column("mould_profile_param", 9, VarType.INT)
    column("reward_item_param", 10, VarType.INT)
    column("reward_mould_param", 11, VarType.INT)
    column("reward_price_param", 12, VarType.INT)

    // These native enums and struct params have no gameval names in revision 240.
    row("dbrow.giants_foundry_native_catalogue") {
        column(0, 4372)
        column(1, 4373)
        column(2, 4374)
        column(3, 4375)
        column(4, 4379)
        column(5, 1621)
        column(6, 1622)
        column(7, 1625)
        column(8, 1626)
        column(9, 1627)
        column(10, 1636)
        column(11, 1637)
        column(12, 1638)
    }
}

internal fun foundryMetals() = dbTable("dbtable.giants_foundry_metals", serverOnly = true) {
    column("tier", 0, VarType.INT)
    column("key", 1, VarType.STRING)
    column("bar", 2, VarType.OBJ)
    column("count_varbit", 3, VarType.INT)

    val metals = listOf(
        Triple("bronze", "obj.bronze_bar", "varbit.giants_foundry_bronze_count"),
        Triple("iron", "obj.iron_bar", "varbit.giants_foundry_iron_count"),
        Triple("steel", "obj.steel_bar", "varbit.giants_foundry_steel_count"),
        Triple("mithril", "obj.mithril_bar", "varbit.giants_foundry_mithril_count"),
        Triple("adamant", "obj.adamantite_bar", "varbit.giants_foundry_adamant_count"),
        Triple("rune", "obj.runite_bar", "varbit.giants_foundry_rune_count"),
    )
    metals.forEachIndexed { index, (key, bar, count) ->
        row("dbrow.giants_foundry_metal_$key") {
            column(0, index + 1)
            column(1, key)
            columnRSCM(2, bar)
            columnRSCM(3, count)
        }
    }
}

internal fun foundryTemperature() = dbTable("dbtable.giants_foundry_temperature", serverOnly = true) {
    column("steps", 0, VarType.INT)
    row("dbrow.giants_foundry_temperature") {
        column(0, 7, 8, 9, 11, 13, 15, 17, 19, 21, 24, 27, 30, 33, 37, 41, 45, 49, 53, 57, 62, 67, 72, 77, 83, 89)
    }
}
