package org.rsmod.content.minigames.blastfurnace.core

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

// Stock varbits are player-scoped, with four 8-bit counts per varp (dump/varbits/VarbitDefinitions.json).
// Perfect-gold stock has no smelting recipe.

internal var Player.blastFurnaceCopperOre by intVarBit("varbit.blast_furnace_copper_ore")
internal var Player.blastFurnaceTinOre by intVarBit("varbit.blast_furnace_tin_ore")
internal var Player.blastFurnaceIronOre by intVarBit("varbit.blast_furnace_iron_ore")
internal var Player.blastFurnaceMithrilOre by intVarBit("varbit.blast_furnace_mithril_ore")
internal var Player.blastFurnaceAdamantiteOre by intVarBit("varbit.blast_furnace_adamantite_ore")
internal var Player.blastFurnaceRuniteOre by intVarBit("varbit.blast_furnace_runite_ore")
internal var Player.blastFurnaceSilverOre by intVarBit("varbit.blast_furnace_silver_ore")
internal var Player.blastFurnaceGoldOre by intVarBit("varbit.blast_furnace_gold_ore")
internal var Player.blastFurnaceCoal by intVarBit("varbit.blast_furnace_coal")
internal var Player.blastFurnacePerfectGoldOre by intVarBit("varbit.blast_furnace_perfect_gold_ore")
internal var Player.blastFurnaceLeadOre by intVarBit("varbit.blast_furnace_lead_ore")
internal var Player.blastFurnaceNickelOre by intVarBit("varbit.blast_furnace_nickel_ore")
internal var Player.blastFurnaceCatalyst by intVarBit("varbit.blast_furnace_catalyst")

// Dispenser states: 0 empty, 1 pouring, 2 hot, 3 cooled.
internal var Player.blastFurnaceBarsHot by intVarBit("varbit.blast_furnace_bars_hot")

internal var Player.blastFurnaceCofferBalance by intVarBit("varbit.blast_furnace_coffer")

// This flag also selects the coffer's empty/full multiloc; update it with the balance.
internal var Player.blastFurnaceCofferHasCoins by boolVarBit("varbit.blast_furnace_coinsincoffer")

internal var Player.blastFurnaceBronzeBars by intVarBit("varbit.blast_furnace_bronze_bars")
internal var Player.blastFurnaceIronBars by intVarBit("varbit.blast_furnace_iron_bars")
internal var Player.blastFurnaceSteelBars by intVarBit("varbit.blast_furnace_steel_bars")
internal var Player.blastFurnaceMithrilBars by intVarBit("varbit.blast_furnace_mithril_bars")
internal var Player.blastFurnaceAdamantiteBars by intVarBit("varbit.blast_furnace_adamantite_bars")
internal var Player.blastFurnaceRuniteBars by intVarBit("varbit.blast_furnace_runite_bars")
internal var Player.blastFurnaceGoldBars by intVarBit("varbit.blast_furnace_gold_bars")
internal var Player.blastFurnaceSilverBars by intVarBit("varbit.blast_furnace_silver_bars")
internal var Player.blastFurnacePerfectGoldBars by intVarBit("varbit.blast_furnace_perfect_gold_bars")
internal var Player.blastFurnaceLeadBars by intVarBit("varbit.blast_furnace_lead_bars")
internal var Player.blastFurnaceCupronickelBars by intVarBit("varbit.blast_furnace_cupronickel_bars")

internal fun Player.blastFurnaceOreCount(oreInternal: String): Int =
    when (oreInternal) {
        "obj.copper_ore" -> blastFurnaceCopperOre
        "obj.tin_ore" -> blastFurnaceTinOre
        "obj.iron_ore" -> blastFurnaceIronOre
        "obj.mithril_ore" -> blastFurnaceMithrilOre
        "obj.adamantite_ore" -> blastFurnaceAdamantiteOre
        "obj.runite_ore" -> blastFurnaceRuniteOre
        "obj.silver_ore" -> blastFurnaceSilverOre
        "obj.gold_ore" -> blastFurnaceGoldOre
        "obj.coal" -> blastFurnaceCoal
        "obj.perfect_gold_ore" -> blastFurnacePerfectGoldOre
        "obj.lead_ore" -> blastFurnaceLeadOre
        "obj.nickel_ore" -> blastFurnaceNickelOre
        "obj.smithing_catalyst" -> blastFurnaceCatalyst
        else -> 0
    }

internal fun Player.setBlastFurnaceOreCount(oreInternal: String, value: Int) {
    when (oreInternal) {
        "obj.copper_ore" -> blastFurnaceCopperOre = value
        "obj.tin_ore" -> blastFurnaceTinOre = value
        "obj.iron_ore" -> blastFurnaceIronOre = value
        "obj.mithril_ore" -> blastFurnaceMithrilOre = value
        "obj.adamantite_ore" -> blastFurnaceAdamantiteOre = value
        "obj.runite_ore" -> blastFurnaceRuniteOre = value
        "obj.silver_ore" -> blastFurnaceSilverOre = value
        "obj.gold_ore" -> blastFurnaceGoldOre = value
        "obj.coal" -> blastFurnaceCoal = value
        "obj.perfect_gold_ore" -> blastFurnacePerfectGoldOre = value
        "obj.lead_ore" -> blastFurnaceLeadOre = value
        "obj.nickel_ore" -> blastFurnaceNickelOre = value
        "obj.smithing_catalyst" -> blastFurnaceCatalyst = value
    }
}

internal fun Player.blastFurnaceBarCount(barInternal: String): Int =
    when (barInternal) {
        "obj.bronze_bar" -> blastFurnaceBronzeBars
        "obj.iron_bar" -> blastFurnaceIronBars
        "obj.steel_bar" -> blastFurnaceSteelBars
        "obj.mithril_bar" -> blastFurnaceMithrilBars
        "obj.adamantite_bar" -> blastFurnaceAdamantiteBars
        "obj.runite_bar" -> blastFurnaceRuniteBars
        "obj.gold_bar" -> blastFurnaceGoldBars
        "obj.silver_bar" -> blastFurnaceSilverBars
        "obj.perfect_gold_bar" -> blastFurnacePerfectGoldBars
        "obj.lead_bar" -> blastFurnaceLeadBars
        "obj.cupronickel_bar" -> blastFurnaceCupronickelBars
        else -> 0
    }

internal fun Player.setBlastFurnaceBarCount(barInternal: String, value: Int) {
    when (barInternal) {
        "obj.bronze_bar" -> blastFurnaceBronzeBars = value
        "obj.iron_bar" -> blastFurnaceIronBars = value
        "obj.steel_bar" -> blastFurnaceSteelBars = value
        "obj.mithril_bar" -> blastFurnaceMithrilBars = value
        "obj.adamantite_bar" -> blastFurnaceAdamantiteBars = value
        "obj.runite_bar" -> blastFurnaceRuniteBars = value
        "obj.gold_bar" -> blastFurnaceGoldBars = value
        "obj.silver_bar" -> blastFurnaceSilverBars = value
        "obj.perfect_gold_bar" -> blastFurnacePerfectGoldBars = value
        "obj.lead_bar" -> blastFurnaceLeadBars = value
        "obj.cupronickel_bar" -> blastFurnaceCupronickelBars = value
    }
}
