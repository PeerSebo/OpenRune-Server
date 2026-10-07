package dev.openrune.tables.skills

import dev.openrune.definition.util.VarType
import dev.openrune.tables.production.productionTable

/**
 * Read xp_tenths and the anvil/alternate tenths columns; legacy whole-XP columns discard
 * fractional awards.
 */
object Smithing {

    const val COL_SMITH_XP = 7
    const val COL_SMELT_XP_ALTERNATE = 8
    const val COL_PREFIX = 9

    // Column 10 of smithing_bars is the shared `xp_tenths` column: the production builder
    // allocates the first index the extras below leave free, which is 10.

    /** Canonical per-bar anvil XP in tenths (125 = 12.5xp) - smithing_bars. */
    const val COL_SMITH_XP_TENTHS = 11

    /**
     * Alternate smelt XP in tenths - smithing_bars. Gold with goldsmith gauntlets/smithing cape
     * (562 = 56.2xp) and blurite via Superheat Item (95 = 9.5xp).
     */
    const val COL_XP_ALTERNATE_TENTHS = 12

    const val COL_SHORT_NAME = 7

    // Readers use xp_tenths; the legacy xp column truncates fractional awards.
    fun cannonBalls() =
        productionTable(
            "dbtable.smithing_cannon_balls",
            serverOnly = true,
            defaultCategory = "Smelt",
        ) {
            row("dbrow.bronze_cannon_ball") {
                production {
                    input("obj.bronze_bar")
                    statReq("stat.smithing", 5)
                    xpTenths(90)
                    output("obj.bronze_cannonball")
                }
            }
            row("dbrow.iron_cannon_ball") {
                production {
                    input("obj.iron_bar")
                    statReq("stat.smithing", 20)
                    xpTenths(170)
                    output("obj.iron_cannonball")
                }
            }
            row("dbrow.steel_cannon_ball") {
                production {
                    input("obj.steel_bar")
                    statReq("stat.smithing", 35)
                    xpTenths(256)
                    output("obj.mcannonball")
                }
            }
            row("dbrow.mithril_cannon_ball") {
                production {
                    input("obj.mithril_bar")
                    statReq("stat.smithing", 55)
                    xpTenths(340)
                    output("obj.mithril_cannonball")
                }
            }
            row("dbrow.adamantite_cannon_ball") {
                production {
                    input("obj.adamantite_bar")
                    statReq("stat.smithing", 75)
                    xpTenths(424)
                    output("obj.adamant_cannonball")
                }
            }
            row("dbrow.runite_cannon_ball") {
                production {
                    input("obj.runite_bar")
                    statReq("stat.smithing", 90)
                    xpTenths(505)
                    output("obj.rune_cannonball")
                }
            }
        }

    fun dragonForge() =
        productionTable(
            "dbtable.smithing_dragon_forge",
            serverOnly = true,
            defaultCategory = "Forge",
        ) {
            row("dbrow.dragon_keel_parts") {
                production {
                    input("obj.dragon_sheet", 2)
                    statReq("stat.smithing", 94)
                    xpTenths(7000)
                    output("obj.sailing_boat_keel_part_dragon", 1)
                }
            }
            row("dbrow.dragon_sq_shield") {
                production {
                    input("obj.dragonshield_a", 1)
                    input("obj.dragonshield_b", 1)
                    statReq("stat.smithing", 60)
                    xpTenths(750)
                    output("obj.dragon_sq_shield", 1)
                }
            }
            row("dbrow.dragon_key") {
                production {
                    input("obj.dragonkin_key_frem", 1)
                    input("obj.dragonkin_key_mory", 1)
                    input("obj.dragonkin_key_zeah", 1)
                    input("obj.dragonkin_key_karam", 1)
                    statReq("stat.smithing", 70)
                    xpTenths(0)
                    output("obj.dragonkin_key", 1)
                }
            }
            row("dbrow.dragon_kiteshield") {
                production {
                    input("obj.dragon_sq_shield", 1)
                    input("obj.dragon_slice", 1)
                    input("obj.dragon_shard", 1)
                    statReq("stat.smithing", 75)
                    xpTenths(10000)
                    output("obj.dragon_kiteshield", 1)
                }
            }
            row("dbrow.dragon_nails") {
                production {
                    input("obj.dragon_sheet", 1)
                    statReq("stat.smithing", 92)
                    xpTenths(3500)
                    output("obj.nails_dragon", 15)
                }
            }
            row("dbrow.dragon_platebody") {
                production {
                    input("obj.dragon_chainbody", 1)
                    input("obj.dragon_lump", 1)
                    input("obj.dragon_shard", 1)
                    statReq("stat.smithing", 90)
                    xpTenths(20000)
                    output("obj.dragon_platebody", 1)
                }
            }
            row("dbrow.large_dragon_keel_parts") {
                production {
                    input("obj.sailing_boat_keel_part_dragon", 2)
                    statReq("stat.smithing", 94)
                    xpTenths(5000)
                    output("obj.sailing_boat_large_keel_part_dragon", 1)
                }
            }
        }

    fun bars() =
        productionTable(
            "dbtable.smithing_bars",
            serverOnly = true,
            defaultCategory = "Smelt",
            extraColumns = {
                column("smithXp", COL_SMITH_XP, VarType.INT)
                column("smithXpAlternate", COL_SMELT_XP_ALTERNATE, VarType.INT)
                column("prefix", COL_PREFIX, VarType.STRING)
                column("smith_xp_tenths", COL_SMITH_XP_TENTHS, VarType.INT)
                column("xp_alternate_tenths", COL_XP_ALTERNATE_TENTHS, VarType.INT)
            },
        ) {
            row("dbrow.bronze") {
                production {
                    input("obj.tin_ore", 1)
                    input("obj.copper_ore", 1)
                    statReq("stat.smithing", 1)
                    xpTenths(62)
                    output("obj.bronze_bar")
                }
                column(COL_SMITH_XP, 12)
                column(COL_PREFIX, "bronze")
                column(COL_SMITH_XP_TENTHS, 125)
            }
            row("dbrow.blurite") {
                production {
                    input("obj.blurite_ore", 1)
                    statReq("stat.smithing", 13)
                    xpTenths(80)
                    output("obj.blurite_bar")
                }
                column(COL_SMITH_XP, 17)
                column(COL_SMELT_XP_ALTERNATE, 9)
                column(COL_PREFIX, "blurite")
                column(COL_SMITH_XP_TENTHS, 175)
                column(COL_XP_ALTERNATE_TENTHS, 95)
            }
            row("dbrow.iron") {
                production {
                    input("obj.iron_ore", 1)
                    statReq("stat.smithing", 15)
                    xpTenths(125)
                    output("obj.iron_bar")
                }
                column(COL_SMITH_XP, 25)
                column(COL_PREFIX, "iron")
                column(COL_SMITH_XP_TENTHS, 250)
            }
            row("dbrow.silver") {
                production {
                    input("obj.silver_ore", 1)
                    statReq("stat.smithing", 20)
                    xpTenths(137)
                    output("obj.silver_bar")
                }
                column(COL_SMITH_XP, 50)
                column(COL_PREFIX, "silver")
                column(COL_SMITH_XP_TENTHS, 500)
            }
            row("dbrow.elemental") {
                production {
                    input("obj.elemental_workshop_ore", 1)
                    input("obj.coal", 4)
                    statReq("stat.smithing", 20)
                    xpTenths(75)
                    output("obj.elemental_workshop_bar")
                }
                column(COL_SMITH_XP, 0)
                column(COL_PREFIX, "elemental")
                column(COL_SMITH_XP_TENTHS, 0)
            }
            row("dbrow.lead") {
                production {
                    input("obj.lead_ore", 2)
                    statReq("stat.smithing", 25)
                    xpTenths(155)
                    output("obj.lead_bar")
                }
                column(COL_SMITH_XP, 0)
                column(COL_PREFIX, "lead")
                column(COL_SMITH_XP_TENTHS, 0)
            }
            row("dbrow.steel") {
                production {
                    input("obj.iron_ore", 1)
                    input("obj.coal", 2)
                    statReq("stat.smithing", 30)
                    xpTenths(175)
                    output("obj.steel_bar")
                }
                column(COL_SMITH_XP, 37)
                column(COL_PREFIX, "steel")
                column(COL_SMITH_XP_TENTHS, 375)
            }
            row("dbrow.gold") {
                production {
                    input("obj.gold_ore", 1)
                    statReq("stat.smithing", 40)
                    xpTenths(225)
                    output("obj.gold_bar")
                }
                column(COL_SMITH_XP, 90)
                column(COL_SMELT_XP_ALTERNATE, 56)
                column(COL_PREFIX, "gold")
                column(COL_SMITH_XP_TENTHS, 900)
                column(COL_XP_ALTERNATE_TENTHS, 562)
            }
            row("dbrow.lovakite") {
                production {
                    input("obj.lovakite_ore", 1)
                    input("obj.coal", 2)
                    statReq("stat.smithing", 45)
                    xpTenths(200)
                    output("obj.lovakite_bar")
                }
                // Flat 60/bar for shayzien armour is unverified (research doc open question 6).
                column(COL_SMITH_XP, 60)
                column(COL_PREFIX, "shayzien")
                column(COL_SMITH_XP_TENTHS, 600)
            }
            row("dbrow.mithril") {
                production {
                    input("obj.mithril_ore", 1)
                    input("obj.coal", 4)
                    statReq("stat.smithing", 50)
                    xpTenths(300)
                    output("obj.mithril_bar")
                }
                column(COL_SMITH_XP, 50)
                column(COL_PREFIX, "mithril")
                column(COL_SMITH_XP_TENTHS, 500)
            }
            row("dbrow.adamantite") {
                production {
                    input("obj.adamantite_ore", 1)
                    input("obj.coal", 6)
                    statReq("stat.smithing", 70)
                    xpTenths(375)
                    output("obj.adamantite_bar")
                }
                column(COL_SMITH_XP, 62)
                column(COL_PREFIX, "adamant")
                column(COL_SMITH_XP_TENTHS, 625)
            }
            row("dbrow.cupronickel") {
                production {
                    input("obj.nickel_ore", 1)
                    input("obj.copper_ore", 2)
                    statReq("stat.smithing", 74)
                    xpTenths(420)
                    output("obj.cupronickel_bar")
                }
                column(COL_SMITH_XP, 0)
                column(COL_PREFIX, "cupronickel")
                column(COL_SMITH_XP_TENTHS, 0)
            }
            row("dbrow.runite") {
                production {
                    input("obj.runite_ore", 1)
                    input("obj.coal", 8)
                    statReq("stat.smithing", 85)
                    xpTenths(500)
                    output("obj.runite_bar")
                }
                column(COL_SMITH_XP, 75)
                column(COL_PREFIX, "rune")
                column(COL_SMITH_XP_TENTHS, 750)
            }
        }

    fun crystalSinging() =
        productionTable(
            "dbtable.smithing_crystal_singing",
            serverOnly = true,
            defaultCategory = "Sing",
            extraColumns = { column("shortName", COL_SHORT_NAME, VarType.STRING) },
        ) {
            row("dbrow.crystal_celestial_signet") {
                production {
                    input("obj.prif_crystal_shard", 100)
                    input("obj.star_dust", 1000)
                    input("obj.celestial_ring", 1)
                    input("obj.elven_signet", 1)
                    statReq("stat.smithing", 70)
                    xpTenths(50000)
                    output("obj.celestial_signet")
                }
                column(COL_SHORT_NAME, "ring")
            }
            row("dbrow.crystal_helm") {
                production {
                    input("obj.prif_crystal_shard", 50)
                    input("obj.prif_armour_seed", 1)
                    statReq("stat.smithing", 70)
                    xpTenths(25000)
                    output("obj.crystal_helmet")
                }
                column(COL_SHORT_NAME, "helmet")
            }
            row("dbrow.crystal_legs") {
                production {
                    input("obj.prif_crystal_shard", 100)
                    input("obj.prif_armour_seed", 2)
                    statReq("stat.smithing", 72)
                    xpTenths(50000)
                    output("obj.crystal_platelegs")
                }
                column(COL_SHORT_NAME, "platelegs")
            }
            row("dbrow.crystal_body") {
                production {
                    input("obj.prif_crystal_shard", 150)
                    input("obj.prif_armour_seed", 3)
                    statReq("stat.smithing", 74)
                    xpTenths(75000)
                    output("obj.crystal_chestplate")
                }
                column(COL_SHORT_NAME, "platebody")
            }
            row("dbrow.crystal_axe") {
                production {
                    input("obj.prif_crystal_shard", 120)
                    input("obj.prif_tool_seed", 1)
                    input("obj.dragon_axe", 1)
                    statReq("stat.smithing", 76)
                    xpTenths(60000)
                    output("obj.crystal_axe")
                }
                column(COL_SHORT_NAME, "axe")
            }
            row("dbrow.crystal_felling_axe") {
                production {
                    input("obj.prif_crystal_shard", 120)
                    input("obj.prif_tool_seed", 1)
                    input("obj.dragon_axe_2h", 1)
                    statReq("stat.smithing", 76)
                    xpTenths(60000)
                    output("obj.crystal_axe_2h")
                }
                column(COL_SHORT_NAME, "axe")
            }
            row("dbrow.crystal_harpoon") {
                production {
                    input("obj.prif_crystal_shard", 120)
                    input("obj.prif_tool_seed", 1)
                    input("obj.dragon_harpoon", 1)
                    statReq("stat.smithing", 76)
                    xpTenths(60000)
                    output("obj.crystal_harpoon")
                }
                column(COL_SHORT_NAME, "harpoon")
            }
            row("dbrow.crystal_pickaxe") {
                production {
                    input("obj.prif_crystal_shard", 120)
                    input("obj.prif_tool_seed", 1)
                    input("obj.dragon_pickaxe", 1)
                    statReq("stat.smithing", 76)
                    xpTenths(60000)
                    output("obj.crystal_pickaxe")
                }
                column(COL_SHORT_NAME, "pickaxe")
            }
            row("dbrow.crystal_bow") {
                production {
                    input("obj.prif_crystal_shard", 40)
                    input("obj.crystal_seed_old", 1)
                    statReq("stat.smithing", 78)
                    xpTenths(20000)
                    output("obj.crystal_bow")
                }
                column(COL_SHORT_NAME, "bow")
            }
            row("dbrow.crystal_halberd") {
                production {
                    input("obj.prif_crystal_shard", 40)
                    input("obj.crystal_seed_old", 1)
                    statReq("stat.smithing", 78)
                    xpTenths(20000)
                    output("obj.crystal_halberd")
                }
                column(COL_SHORT_NAME, "halberd")
            }
            row("dbrow.crystal_shield") {
                production {
                    input("obj.prif_crystal_shard", 40)
                    input("obj.crystal_seed_old", 1)
                    statReq("stat.smithing", 78)
                    xpTenths(20000)
                    output("obj.crystal_shield")
                }
                column(COL_SHORT_NAME, "shield")
            }
            row("dbrow.enhanced_crystal_key") {
                production {
                    input("obj.prif_crystal_shard", 10)
                    input("obj.crystal_key", 1)
                    statReq("stat.smithing", 80)
                    xpTenths(5000)
                    output("obj.prif_crystal_key")
                }
                column(COL_SHORT_NAME, "key")
            }
            row("dbrow.eternal_teleport_crystal") {
                production {
                    input("obj.prif_crystal_shard", 100)
                    input("obj.prif_teleport_seed", 1)
                    statReq("stat.smithing", 80)
                    xpTenths(50000)
                    output("obj.prif_teleport_crystal")
                }
            }
            row("dbrow.blade_of_saeldor") {
                production {
                    input("obj.prif_crystal_shard", 100)
                    input("obj.prif_weapon_seed_enhanced", 1)
                    statReq("stat.smithing", 82)
                    xpTenths(50000)
                    output("obj.blade_of_saeldor")
                }
                column(COL_SHORT_NAME, "saeldor")
            }
            row("dbrow.bow_of_faerdhinen") {
                production {
                    input("obj.prif_crystal_shard", 100)
                    input("obj.prif_weapon_seed_enhanced", 1)
                    statReq("stat.smithing", 82)
                    xpTenths(50000)
                    output("obj.bow_of_faerdhinen")
                }
                column(COL_SHORT_NAME, "bow")
            }
            row("dbrow.blade_of_saeldor_charged") {
                production {
                    input("obj.prif_crystal_shard", 1000)
                    input("obj.blade_of_saeldor_inactive", 1)
                    statReq("stat.smithing", 82)
                    xpTenths(0)
                    output("obj.blade_of_saeldor_infinite")
                }
                column(COL_SHORT_NAME, "blade")
            }
            row("dbrow.bow_of_faerdhinen_charged") {
                production {
                    input("obj.prif_crystal_shard", 2000)
                    input("obj.bow_of_faerdhinen_inactive", 1)
                    statReq("stat.smithing", 82)
                    xpTenths(0)
                    output("obj.bow_of_faerdhinen_infinite")
                }
                column(COL_SHORT_NAME, "bow")
            }
        }
}
