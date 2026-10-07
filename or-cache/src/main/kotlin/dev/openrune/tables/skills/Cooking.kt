package dev.openrune.tables.skills

import dev.openrune.definition.util.VarType
import dev.openrune.tables.production.productionTable

/**
 * Curves use a 256 denominator: gauntlets, Lumbridge, range, then base fallback. Gauntlets
 * replace curves; Hosidius adds a flat bonus. Derive stop-burn levels from curves. Source:
 */
object Cooking {

    const val COL_BURNT = 7

    const val COL_LOW_BASE = 8
    const val COL_HIGH_BASE = 9

    const val COL_LOW_RANGE = 10
    const val COL_HIGH_RANGE = 11

    /**
     * Presence of this pair determines gauntlet eligibility: lobster, swordfish, cooked kyatt,
     * monkfish, shark and anglerfish. Other foods fall through to the surface's curve.
     */
    const val COL_LOW_GAUNTLET = 12
    const val COL_HIGH_GAUNTLET = 13

    const val COL_LOW_LUMBRIDGE = 14
    const val COL_HIGH_LUMBRIDGE = 15

    const val COL_VAT_OFFSET = 7
    const val COL_BARREL_OFFSET = 8

    fun ales() =
        productionTable(
            "dbtable.cooking_ales",
            serverOnly = true,
            defaultCategory = "Brew",
            extraColumns = {
                column("vat_offset", COL_VAT_OFFSET, VarType.INT)
                column("barrel_offset", COL_BARREL_OFFSET, VarType.INT)
            },
        ) {
            row("dbrow.ale_dwarven_stout") {
                production {
                    input("obj.hammerstone_hops", 4)
                    statReq("stat.cooking", 19)
                    xpTenths(2150)
                    output("obj.dwarven_stout")
                    output("obj.mature_dwarven_stout")
                }
                column(COL_VAT_OFFSET, 4)
                column(COL_BARREL_OFFSET, 4)
            }
            row("dbrow.ale_asgarnian_ale") {
                production {
                    input("obj.hammerstone_hops", 4)
                    statReq("stat.cooking", 24)
                    xpTenths(2480)
                    output("obj.asgarnian_ale")
                    output("obj.mature_asgarnian_ale")
                }
                column(COL_VAT_OFFSET, 10)
                column(COL_BARREL_OFFSET, 6)
            }
            row("dbrow.ale_greenmans_ale") {
                production {
                    input("obj.hammerstone_hops", 4)
                    statReq("stat.cooking", 29)
                    xpTenths(2810)
                    output("obj.greenmans_ale")
                    output("obj.mature_greenmans_ale")
                }
                column(COL_VAT_OFFSET, 16)
                column(COL_BARREL_OFFSET, 8)
            }
            row("dbrow.ale_wizards_mind_bomb") {
                production {
                    input("obj.yanillian_hops", 4)
                    statReq("stat.cooking", 34)
                    xpTenths(3140)
                    output("obj.wizards_mind_bomb")
                    output("obj.mature_wizards_mind_bomb")
                }
                column(COL_VAT_OFFSET, 22)
                column(COL_BARREL_OFFSET, 10)
            }
            row("dbrow.ale_dragon_bitter") {
                production {
                    input("obj.krandorian_hops", 4)
                    statReq("stat.cooking", 39)
                    xpTenths(3470)
                    output("obj.dragon_bitter")
                    output("obj.mature_dragon_bitter")
                }
                column(COL_VAT_OFFSET, 28)
                column(COL_BARREL_OFFSET, 12)
            }
            row("dbrow.ale_moonlight_mead") {
                production {
                    input("obj.bittercap_mushroom", 4)
                    statReq("stat.cooking", 44)
                    xpTenths(3800)
                    output("obj.moonlight_mead")
                    output("obj.mature_moonlight_mead")
                }
                column(COL_VAT_OFFSET, 34)
                column(COL_BARREL_OFFSET, 14)
            }
            row("dbrow.ale_axemans_folly") {
                production {
                    input("obj.oak_roots", 1)
                    statReq("stat.cooking", 49)
                    xpTenths(4130)
                    output("obj.axemans_folly")
                    output("obj.mature_axemans_folly")
                }
                column(COL_VAT_OFFSET, 40)
                column(COL_BARREL_OFFSET, 16)
            }
            row("dbrow.ale_chefs_delight") {
                production {
                    input("obj.chocolate_dust", 2)
                    statReq("stat.cooking", 54)
                    xpTenths(4460)
                    output("obj.chefs_delight")
                    output("obj.mature_chefs_delight")
                }
                column(COL_VAT_OFFSET, 46)
                column(COL_BARREL_OFFSET, 18)
            }
            row("dbrow.ale_slayers_respite") {
                production {
                    input("obj.wildblood_hops", 4)
                    statReq("stat.cooking", 59)
                    xpTenths(4790)
                    output("obj.slayers_respite")
                    output("obj.mature_slayers_respite")
                }
                column(COL_VAT_OFFSET, 52)
                column(COL_BARREL_OFFSET, 20)
            }
            row("dbrow.ale_cider") {
                production {
                    input("obj.apple_mush", 4)
                    statReq("stat.cooking", 14)
                    xpTenths(1820)
                    output("obj.cider")
                    output("obj.mature_cider")
                }
                column(COL_VAT_OFFSET, 58)
                column(COL_BARREL_OFFSET, 22)
            }
        }

    fun foods() =
        productionTable(
            "dbtable.cooking_foods",
            serverOnly = true,
            extraColumns = {
                column("burnt", COL_BURNT, VarType.OBJ)
                column("low_base", COL_LOW_BASE, VarType.INT)
                column("high_base", COL_HIGH_BASE, VarType.INT)
                column("low_range", COL_LOW_RANGE, VarType.INT)
                column("high_range", COL_HIGH_RANGE, VarType.INT)
                column("low_gauntlet", COL_LOW_GAUNTLET, VarType.INT)
                column("high_gauntlet", COL_HIGH_GAUNTLET, VarType.INT)
                column("low_lumbridge", COL_LOW_LUMBRIDGE, VarType.INT)
                column("high_lumbridge", COL_HIGH_LUMBRIDGE, VarType.INT)
            },
        ) {
            row("dbrow.cooking_shrimp") {
                production {
                    input("obj.raw_shrimp")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.shrimp")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_shrimp")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            row("dbrow.cooking_anchovies") {
                production {
                    input("obj.raw_anchovies")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.anchovies")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish1")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            row("dbrow.cooking_sardine") {
                production {
                    input("obj.raw_sardine")
                    statReq("stat.cooking", 1)
                    xpTenths(400)
                    output("obj.sardine")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish1")
                column(COL_LOW_BASE, 118)
                column(COL_HIGH_BASE, 492)
                column(COL_LOW_LUMBRIDGE, 128)
                column(COL_HIGH_LUMBRIDGE, 512)
            }
            row("dbrow.cooking_herring") {
                production {
                    input("obj.raw_herring")
                    statReq("stat.cooking", 5)
                    xpTenths(500)
                    output("obj.herring")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish2")
                column(COL_LOW_BASE, 108)
                column(COL_HIGH_BASE, 472)
                column(COL_LOW_LUMBRIDGE, 118)
                column(COL_HIGH_LUMBRIDGE, 492)
            }
            row("dbrow.cooking_mackerel") {
                production {
                    input("obj.raw_mackerel")
                    statReq("stat.cooking", 10)
                    xpTenths(600)
                    output("obj.mackerel")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish3")
                column(COL_LOW_BASE, 98)
                column(COL_HIGH_BASE, 452)
                column(COL_LOW_LUMBRIDGE, 108)
                column(COL_HIGH_LUMBRIDGE, 472)
            }
            row("dbrow.cooking_trout") {
                production {
                    input("obj.raw_trout")
                    statReq("stat.cooking", 15)
                    xpTenths(700)
                    output("obj.trout")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish3")
                column(COL_LOW_BASE, 88)
                column(COL_HIGH_BASE, 432)
                column(COL_LOW_LUMBRIDGE, 98)
                column(COL_HIGH_LUMBRIDGE, 452)
            }
            // Wiki (cooking-audit.md section 1): cod has distinct fire/range curves;
            // Lumbridge uses the range curve +10/+20.
            row("dbrow.cooking_cod") {
                production {
                    input("obj.raw_cod")
                    statReq("stat.cooking", 18)
                    xpTenths(750)
                    output("obj.cod")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish3")
                column(COL_LOW_BASE, 83)
                column(COL_HIGH_BASE, 422)
                column(COL_LOW_RANGE, 88)
                column(COL_HIGH_RANGE, 432)
                column(COL_LOW_LUMBRIDGE, 98)
                column(COL_HIGH_LUMBRIDGE, 452)
            }
            row("dbrow.cooking_pike") {
                production {
                    input("obj.raw_pike")
                    statReq("stat.cooking", 20)
                    xpTenths(800)
                    output("obj.pike")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish3")
                column(COL_LOW_BASE, 78)
                column(COL_HIGH_BASE, 412)
                column(COL_LOW_LUMBRIDGE, 88)
                column(COL_HIGH_LUMBRIDGE, 432)
            }
            // UNVERIFIED: cooking-audit.md section 2 says Lumbridge adds +10/+10, but lists
            // 78/412 (+10/+20). This row follows the stated +10/+10 rule (78/402); verify the discrepancy.
            row("dbrow.cooking_salmon") {
                production {
                    input("obj.raw_salmon")
                    statReq("stat.cooking", 25)
                    xpTenths(900)
                    output("obj.salmon")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish3")
                column(COL_LOW_BASE, 68)
                column(COL_HIGH_BASE, 392)
                column(COL_LOW_LUMBRIDGE, 78)
                column(COL_HIGH_LUMBRIDGE, 402)
            }
            row("dbrow.cooking_tuna") {
                production {
                    input("obj.raw_tuna")
                    statReq("stat.cooking", 30)
                    xpTenths(1000)
                    output("obj.tuna")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish4")
                column(COL_LOW_BASE, 58)
                column(COL_HIGH_BASE, 372)
                column(COL_LOW_LUMBRIDGE, 68)
                column(COL_HIGH_LUMBRIDGE, 392)
            }
            // Wiki: lobster's fire and range charts are identical (38/332), so no COL_LOW_RANGE
            // override - the gauntlet curve (55/368) applies on either surface per CookingFood.
            row("dbrow.cooking_lobster") {
                production {
                    input("obj.raw_lobster")
                    statReq("stat.cooking", 40)
                    xpTenths(1200)
                    output("obj.lobster")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_lobster")
                column(COL_LOW_BASE, 38)
                column(COL_HIGH_BASE, 332)
                column(COL_LOW_GAUNTLET, 55)
                column(COL_HIGH_GAUNTLET, 368)
                column(COL_LOW_LUMBRIDGE, 48)
                column(COL_HIGH_LUMBRIDGE, 352)
            }
            row("dbrow.cooking_bass") {
                production {
                    input("obj.raw_bass")
                    statReq("stat.cooking", 43)
                    xpTenths(1300)
                    output("obj.bass")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burntfish5")
                column(COL_LOW_BASE, 33)
                column(COL_HIGH_BASE, 312)
                column(COL_LOW_LUMBRIDGE, 43)
                column(COL_HIGH_LUMBRIDGE, 332)
            }
            // Wiki: swordfish fire 18/292, range and gauntlets 30/310 (audit doc section 1).
            // Keep the explicit gauntlet pair so CookingFood.gauntletAffected remains true.
            row("dbrow.cooking_swordfish") {
                production {
                    input("obj.raw_swordfish")
                    statReq("stat.cooking", 45)
                    xpTenths(1400)
                    output("obj.swordfish")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_swordfish")
                column(COL_LOW_BASE, 18)
                column(COL_HIGH_BASE, 292)
                column(COL_LOW_RANGE, 30)
                column(COL_HIGH_RANGE, 310)
                column(COL_LOW_GAUNTLET, 30)
                column(COL_HIGH_GAUNTLET, 310)
                column(COL_LOW_LUMBRIDGE, 40)
                column(COL_HIGH_LUMBRIDGE, 330)
            }
            row("dbrow.cooking_monkfish") {
                production {
                    input("obj.raw_monkfish")
                    statReq("stat.cooking", 62)
                    xpTenths(1500)
                    output("obj.monkfish")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_monkfish")
                column(COL_LOW_BASE, 11)
                column(COL_HIGH_BASE, 275)
                column(COL_LOW_RANGE, 13)
                column(COL_HIGH_RANGE, 280)
                column(COL_LOW_GAUNTLET, 24)
                column(COL_HIGH_GAUNTLET, 290)
                column(COL_LOW_LUMBRIDGE, 23)
                column(COL_HIGH_LUMBRIDGE, 300)
            }
            row("dbrow.cooking_shark") {
                production {
                    input("obj.raw_shark")
                    statReq("stat.cooking", 80)
                    xpTenths(2100)
                    output("obj.shark")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_shark")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 202)
                column(COL_LOW_RANGE, 1)
                column(COL_HIGH_RANGE, 232)
                column(COL_LOW_GAUNTLET, 15)
                column(COL_HIGH_GAUNTLET, 270)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 252)
            }
            // Wiki: manta ray is not gauntlet-affected (audit doc section 2).
            row("dbrow.cooking_manta_ray") {
                production {
                    input("obj.raw_mantaray")
                    statReq("stat.cooking", 91)
                    xpTenths(2163)
                    output("obj.mantaray")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_mantaray")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 202)
                column(COL_LOW_RANGE, 1)
                column(COL_HIGH_RANGE, 222)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 242)
            }
            // Wiki: sea turtle is NOT gauntlet-affected either (same section 2 correction).
            row("dbrow.cooking_sea_turtle") {
                production {
                    input("obj.raw_seaturtle")
                    statReq("stat.cooking", 82)
                    xpTenths(2113)
                    output("obj.seaturtle")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_seaturtle")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 202)
                column(COL_LOW_RANGE, 1)
                column(COL_HIGH_RANGE, 222)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 242)
            }
            row("dbrow.cooking_dark_crab") {
                production {
                    input("obj.raw_dark_crab")
                    statReq("stat.cooking", 90)
                    xpTenths(2150)
                    output("obj.dark_crab")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_dark_crab")
                column(COL_LOW_BASE, 10)
                column(COL_HIGH_BASE, 222)
                column(COL_LOW_LUMBRIDGE, 20)
                column(COL_HIGH_LUMBRIDGE, 242)
            }
            row("dbrow.cooking_anglerfish") {
                production {
                    input("obj.raw_anglerfish")
                    statReq("stat.cooking", 84)
                    xpTenths(2300)
                    output("obj.anglerfish")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_anglerfish")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 200)
                column(COL_LOW_RANGE, 1)
                column(COL_HIGH_RANGE, 220)
                column(COL_LOW_GAUNTLET, 12)
                column(COL_HIGH_GAUNTLET, 260)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 240)
            }
            row("dbrow.cooking_chicken") {
                production {
                    input("obj.raw_chicken")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_chicken")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_chicken")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            // Brutus drops raw t-bone steak, not raw beef. Level/XP and the shared fire/range
            // success chart: https://oldschool.runescape.wiki/w/Cooked_t-bone_steak (oldid 15151154).
            // The 92/423 pair reproduces every published level 13-50 sample with our standard
            // interpolation: 134/256 at 13, 255/256 at 49, then guaranteed success at 50.
            row("dbrow.cooking_tbone_steak") {
                production {
                    input("obj.raw_tbone_steak")
                    statReq("stat.cooking", 13)
                    xpTenths(900)
                    output("obj.tbone_steak")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.tbone_steak_burned")
                column(COL_LOW_BASE, 92)
                column(COL_HIGH_BASE, 423)
            }
            row("dbrow.cooking_meat") {
                production {
                    input("obj.raw_beef")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            // Wiki lists this tier ("shrimp/anchovies/chicken/beef/rabbit") by output family;
            // bear meat shares beef's cooked_meat output so it takes the same curve.
            row("dbrow.cooking_bear_meat") {
                production {
                    input("obj.raw_bear_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            row("dbrow.cooking_rat_meat") {
                production {
                    input("obj.raw_rat_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            row("dbrow.cooking_rabbit") {
                production {
                    input("obj.raw_rabbit")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_rabbit")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            row("dbrow.cooking_cave_eel") {
                production {
                    input("obj.raw_cave_eel")
                    statReq("stat.cooking", 38)
                    xpTenths(1150)
                    output("obj.cave_eel")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_cave_eel")
                column(COL_LOW_BASE, 38)
                column(COL_HIGH_BASE, 332)
                column(COL_LOW_LUMBRIDGE, 48)
                column(COL_HIGH_LUMBRIDGE, 352)
            }
            // UNVERIFIED (audit doc open question 1): lava eel has no published chart. Use
            // swordfish's fire curve, the nearest documented Cooking level, without a Lumbridge bonus.
            row("dbrow.cooking_lava_eel") {
                production {
                    input("obj.raw_lava_eel")
                    statReq("stat.cooking", 53)
                    xpTenths(300)
                    output("obj.lava_eel")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_eel")
                column(COL_LOW_BASE, 18)
                column(COL_HIGH_BASE, 292)
            }
            // Wiki: karambwan chart 70/255 (audit doc section 1).
            row("dbrow.cooking_karambwan") {
                production {
                    input("obj.tbwt_raw_karambwan")
                    statReq("stat.cooking", 30)
                    xpTenths(1900)
                    output("obj.tbwt_cooked_karambwan")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.tbwt_burnt_karambwan")
                column(COL_LOW_BASE, 70)
                column(COL_HIGH_BASE, 255)
                column(COL_LOW_LUMBRIDGE, 80)
                column(COL_HIGH_LUMBRIDGE, 275)
            }
            // Wiki: bread dough cannot be cooked on a fire (audit doc section 3) - the range-only
            // gate lives in CookingEvents.RANGE_ONLY_RAWS.
            row("dbrow.cooking_bread") {
                production {
                    input("obj.bread_dough")
                    statReq("stat.cooking", 1)
                    xpTenths(400)
                    output("obj.bread")
                    category("Bread")
                }
                columnRSCM(COL_BURNT, "obj.burnt_bread")
                column(COL_LOW_BASE, 118)
                column(COL_HIGH_BASE, 492)
                column(COL_LOW_LUMBRIDGE, 128)
                column(COL_HIGH_LUMBRIDGE, 512)
            }
            row("dbrow.cooking_redberry_pie") {
                production {
                    input("obj.uncooked_redberry_pie")
                    statReq("stat.cooking", 10)
                    xpTenths(780)
                    output("obj.redberry_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 98)
                column(COL_HIGH_BASE, 452)
                column(COL_LOW_LUMBRIDGE, 108)
                column(COL_HIGH_LUMBRIDGE, 472)
            }
            row("dbrow.cooking_meat_pie") {
                production {
                    input("obj.uncooked_meat_pie")
                    statReq("stat.cooking", 20)
                    xpTenths(1100)
                    output("obj.meat_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 78)
                column(COL_HIGH_BASE, 412)
                column(COL_LOW_LUMBRIDGE, 88)
                column(COL_HIGH_LUMBRIDGE, 432)
            }
            row("dbrow.cooking_apple_pie") {
                production {
                    input("obj.uncooked_apple_pie")
                    statReq("stat.cooking", 30)
                    xpTenths(1300)
                    output("obj.apple_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 58)
                column(COL_HIGH_BASE, 372)
                column(COL_LOW_LUMBRIDGE, 68)
                column(COL_HIGH_LUMBRIDGE, 392)
            }
            row("dbrow.cooking_pizza") {
                production {
                    input("obj.uncooked_pizza")
                    statReq("stat.cooking", 35)
                    xpTenths(1430)
                    output("obj.plain_pizza")
                    category("Pizza")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pizza")
                column(COL_LOW_BASE, 48)
                column(COL_HIGH_BASE, 352)
                column(COL_LOW_LUMBRIDGE, 58)
                column(COL_HIGH_LUMBRIDGE, 372)
            }
            row("dbrow.cooking_mud_pie") {
                production {
                    input("obj.uncooked_mud_pie")
                    statReq("stat.cooking", 29)
                    xpTenths(1280)
                    output("obj.mud_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 58)
                column(COL_HIGH_BASE, 372)
                column(COL_LOW_LUMBRIDGE, 68)
                column(COL_HIGH_LUMBRIDGE, 392)
            }
            row("dbrow.cooking_garden_pie") {
                production {
                    input("obj.uncooked_garden_pie")
                    statReq("stat.cooking", 34)
                    xpTenths(1380)
                    output("obj.garden_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 48)
                column(COL_HIGH_BASE, 352)
                column(COL_LOW_LUMBRIDGE, 58)
                column(COL_HIGH_LUMBRIDGE, 372)
            }
            row("dbrow.cooking_fish_pie") {
                production {
                    input("obj.uncooked_fish_pie")
                    statReq("stat.cooking", 47)
                    xpTenths(1640)
                    output("obj.fish_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 38)
                column(COL_HIGH_BASE, 332)
                column(COL_LOW_LUMBRIDGE, 48)
                column(COL_HIGH_LUMBRIDGE, 352)
            }
            // Wiki chart 20/300 (audit doc section 1). Lumbridge's published 108/462 conflicts
            // with the base curve; +10/+20 is provisional pending verification (open question 3).
            row("dbrow.cooking_botanical_pie") {
                production {
                    input("obj.uncooked_botanical_pie")
                    statReq("stat.cooking", 52)
                    xpTenths(1800)
                    output("obj.botanical_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 20)
                column(COL_HIGH_BASE, 300)
                column(COL_LOW_LUMBRIDGE, 30)
                column(COL_HIGH_LUMBRIDGE, 320)
            }
            // Same wiki-Lumbridge-looks-wrong situation as botanical pie above (140/450) -
            // uniform +10/+20 used instead.
            row("dbrow.cooking_mushroom_pie") {
                production {
                    input("obj.uncooked_mushroom_pie")
                    statReq("stat.cooking", 60)
                    xpTenths(2000)
                    output("obj.mushroom_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 17)
                column(COL_HIGH_BASE, 285)
                column(COL_LOW_LUMBRIDGE, 27)
                column(COL_HIGH_LUMBRIDGE, 305)
            }
            row("dbrow.cooking_admiral_pie") {
                production {
                    input("obj.uncooked_admiral_pie")
                    statReq("stat.cooking", 70)
                    xpTenths(2100)
                    output("obj.admiral_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 15)
                column(COL_HIGH_BASE, 270)
                column(COL_LOW_LUMBRIDGE, 25)
                column(COL_HIGH_LUMBRIDGE, 290)
            }
            row("dbrow.cooking_dragonfruit_pie") {
                production {
                    input("obj.uncooked_dragonfruit_pie")
                    statReq("stat.cooking", 73)
                    xpTenths(2200)
                    output("obj.dragonfruit_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 8)
                column(COL_HIGH_BASE, 250)
                column(COL_LOW_LUMBRIDGE, 18)
                column(COL_HIGH_LUMBRIDGE, 270)
            }
            row("dbrow.cooking_wild_pie") {
                production {
                    input("obj.uncooked_wild_pie")
                    statReq("stat.cooking", 85)
                    xpTenths(2400)
                    output("obj.wild_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 222)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 242)
            }
            row("dbrow.cooking_summer_pie") {
                production {
                    input("obj.uncooked_summer_pie")
                    statReq("stat.cooking", 95)
                    xpTenths(2600)
                    output("obj.summer_pie")
                    category("Pie")
                }
                columnRSCM(COL_BURNT, "obj.burnt_pie")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 212)
                column(COL_LOW_LUMBRIDGE, 11)
                column(COL_HIGH_LUMBRIDGE, 232)
            }
            row("dbrow.cooking_stew") {
                production {
                    input("obj.uncooked_stew")
                    statReq("stat.cooking", 25)
                    xpTenths(1170)
                    output("obj.stew")
                    category("Stew")
                }
                columnRSCM(COL_BURNT, "obj.burnt_stew")
                column(COL_LOW_BASE, 68)
                column(COL_HIGH_BASE, 392)
                column(COL_LOW_LUMBRIDGE, 78)
                column(COL_HIGH_LUMBRIDGE, 412)
            }
            row("dbrow.cooking_cake") {
                production {
                    input("obj.uncooked_cake")
                    statReq("stat.cooking", 40)
                    xpTenths(1800)
                    output("obj.cake")
                    category("Cake")
                }
                columnRSCM(COL_BURNT, "obj.burnt_cake")
                column(COL_LOW_BASE, 38)
                column(COL_HIGH_BASE, 332)
                column(COL_LOW_LUMBRIDGE, 48)
                column(COL_HIGH_LUMBRIDGE, 352)
            }
            // Wiki Baked potato: range-only; CookingEvents.RANGE_ONLY_RAWS enforces the restriction.
            row("dbrow.cooking_baked_potato") {
                production {
                    input("obj.potato")
                    statReq("stat.cooking", 7)
                    xpTenths(150)
                    output("obj.potato_baked")
                    category("Vegetable")
                }
                columnRSCM(COL_BURNT, "obj.potato_burnt")
                column(COL_LOW_BASE, 108)
                column(COL_HIGH_BASE, 472)
                column(COL_LOW_LUMBRIDGE, 118)
                column(COL_HIGH_LUMBRIDGE, 492)
            }
            row("dbrow.cooking_sweetcorn") {
                production {
                    input("obj.sweetcorn")
                    statReq("stat.cooking", 28)
                    xpTenths(1040)
                    output("obj.sweetcorn_cooked")
                    category("Vegetable")
                }
                columnRSCM(COL_BURNT, "obj.sweetcorn_burnt")
                column(COL_LOW_BASE, 78)
                column(COL_HIGH_BASE, 412)
                column(COL_LOW_LUMBRIDGE, 88)
                column(COL_HIGH_LUMBRIDGE, 432)
            }
            // Wiki: Karambwanji burns to ashes; there is no distinct burnt item. The 200/400
            // curve needs verification (audit doc section 1). Tai Bwo Wannai Trio progress gating is pending.
            row("dbrow.cooking_karambwanji") {
                production {
                    input("obj.tbwt_raw_karambwanji")
                    statReq("stat.cooking", 1)
                    xpTenths(100)
                    output("obj.tbwt_cooked_karambwanji")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.ashes")
                column(COL_LOW_BASE, 200)
                column(COL_HIGH_BASE, 400)
                column(COL_LOW_LUMBRIDGE, 210)
                column(COL_HIGH_LUMBRIDGE, 420)
            }
            row("dbrow.cooking_slimy_eel") {
                production {
                    input("obj.mort_slimey_eel")
                    statReq("stat.cooking", 28)
                    xpTenths(950)
                    output("obj.mort_slimey_eel_cooked")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.burnt_eel")
                column(COL_LOW_BASE, 63)
                column(COL_HIGH_BASE, 382)
                column(COL_LOW_LUMBRIDGE, 73)
                column(COL_HIGH_LUMBRIDGE, 402)
            }
            // UNRESOLVED: cooking-audit.md section 4 gives giant carp a level-10 98/452 curve,
            // but the LIVE cache has only raw/cooked giant carp and no burnt item. This row retains
            // guaranteed success (256/256, burnt == cooked) until the failure outcome is confirmed.
            row("dbrow.cooking_giant_carp") {
                production {
                    input("obj.raw_giant_carp")
                    statReq("stat.cooking", 1)
                    xpTenths(0)
                    output("obj.giant_carp")
                    category("Fish")
                }
                columnRSCM(COL_BURNT, "obj.giant_carp")
                column(COL_LOW_BASE, 256)
                column(COL_HIGH_BASE, 256)
            }
            row("dbrow.cooking_yak_meat") {
                production {
                    input("obj.yak_meat_raw")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            // Wiki: Raw boar meat - lvl 1, 30 xp, cooked/burnt meat; also sinew-eligible on a
            // range (CookingEvents.SINEW_RAWS).
            row("dbrow.cooking_boar_meat") {
                production {
                    input("obj.raw_boar_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            // Wiki: Raw impaler meat - "behaves like normal meat", cook or sinew.
            row("dbrow.cooking_impaler_meat") {
                production {
                    input("obj.raw_impaler_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.cooked_meat")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_meat")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
                column(COL_LOW_LUMBRIDGE, 138)
                column(COL_HIGH_LUMBRIDGE, 532)
            }
            // UNVERIFIED (audit doc section 1): giant crab uses pike's curve, the nearest
            // documented Cooking level. No Lumbridge override until a chart or capture confirms it.
            row("dbrow.cooking_giant_crab_meat") {
                production {
                    input("obj.hundred_pirate_giant_crab_meat")
                    statReq("stat.cooking", 21)
                    xpTenths(1000)
                    output("obj.hundred_pirate_giant_crab_meat_5")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.hundred_pirate_burned_giant_crab_meat")
                column(COL_LOW_BASE, 78)
                column(COL_HIGH_BASE, 412)
            }
            // Wiki: Scrambled egg - RANGE ONLY (audit doc section 3), chart 90/438. The
            // range-only gate lives in CookingEvents.RANGE_ONLY_RAWS.
            row("dbrow.cooking_scrambled_egg") {
                production {
                    input("obj.bowl_egg_raw")
                    statReq("stat.cooking", 13)
                    xpTenths(500)
                    output("obj.bowl_egg_scrambled")
                    category("Egg")
                }
                columnRSCM(COL_BURNT, "obj.bowl_egg_burnt")
                column(COL_LOW_BASE, 90)
                column(COL_HIGH_BASE, 438)
                column(COL_LOW_LUMBRIDGE, 100)
                column(COL_HIGH_LUMBRIDGE, 458)
            }
            row("dbrow.cooking_fried_mushrooms") {
                production {
                    input("obj.bowl_mushroom_raw")
                    statReq("stat.cooking", 46)
                    xpTenths(600)
                    output("obj.bowl_mushroom_fried")
                    category("Vegetable")
                }
                columnRSCM(COL_BURNT, "obj.bowl_mushroom_burnt")
                column(COL_LOW_BASE, 16)
                column(COL_HIGH_BASE, 282)
                column(COL_LOW_LUMBRIDGE, 26)
                column(COL_HIGH_LUMBRIDGE, 302)
            }
            row("dbrow.cooking_fried_onions") {
                production {
                    input("obj.bowl_onion")
                    statReq("stat.cooking", 42)
                    xpTenths(600)
                    output("obj.bowl_onion_fried")
                    category("Vegetable")
                }
                columnRSCM(COL_BURNT, "obj.bowl_onion_burnt")
                column(COL_LOW_BASE, 36)
                column(COL_HIGH_BASE, 322)
                column(COL_LOW_LUMBRIDGE, 46)
                column(COL_HIGH_LUMBRIDGE, 342)
            }
            row("dbrow.cooking_curry") {
                production {
                    input("obj.uncooked_curry")
                    statReq("stat.cooking", 60)
                    xpTenths(2800)
                    output("obj.curry")
                    category("Stew")
                }
                columnRSCM(COL_BURNT, "obj.burnt_curry")
                column(COL_LOW_BASE, 38)
                column(COL_HIGH_BASE, 332)
                column(COL_LOW_LUMBRIDGE, 48)
                column(COL_HIGH_LUMBRIDGE, 352)
            }
            row("dbrow.cooking_seaweed") {
                production {
                    input("obj.seaweed")
                    statReq("stat.cooking", 1)
                    xpTenths(0)
                    output("obj.soda_ash")
                    category("Seaweed")
                }
                columnRSCM(COL_BURNT, "obj.soda_ash")
                column(COL_LOW_BASE, 256)
                column(COL_HIGH_BASE, 256)
            }
            row("dbrow.cooking_edible_seaweed") {
                production {
                    input("obj.edible_seaweed")
                    statReq("stat.cooking", 1)
                    xpTenths(0)
                    output("obj.soda_ash")
                    category("Seaweed")
                }
                columnRSCM(COL_BURNT, "obj.soda_ash")
                column(COL_LOW_BASE, 256)
                column(COL_HIGH_BASE, 256)
            }
            // UNKNOWN (audit doc section 1: "swamp weed" has no published chart either) - kept
            // on the same guaranteed-success model as its seaweed siblings since it feeds the
            // same soda-ash conversion and nothing in the doc suggests otherwise.
            row("dbrow.cooking_swamp_weed") {
                production {
                    input("obj.dorgesh_swamp_weed")
                    statReq("stat.cooking", 1)
                    xpTenths(0)
                    output("obj.soda_ash")
                    category("Seaweed")
                }
                columnRSCM(COL_BURNT, "obj.soda_ash")
                column(COL_LOW_BASE, 256)
                column(COL_HIGH_BASE, 256)
            }
            // Wiki Soda ash: giant seaweed yields six ash on success, one on failure; no gauntlet bonus.
            // The "burnt" product represents the single ash. The 0/500 curve guarantees success at 51
            // without a Lumbridge bonus.
            row("dbrow.cooking_giant_seaweed") {
                production {
                    input("obj.giant_seaweed")
                    statReq("stat.cooking", 1)
                    xpTenths(0)
                    output("obj.soda_ash", 6)
                    category("Seaweed")
                }
                columnRSCM(COL_BURNT, "obj.soda_ash")
                column(COL_LOW_BASE, 0)
                column(COL_HIGH_BASE, 500)
            }

            // Unverified food families (audit doc section 8).
            // Snail, chompy, big-game and antelope levels/XP/curves below are inferred from similar
            // recipes. No published chart or capture confirmed them; do not treat them as ground truth.

            // INFERRED: snail curves reuse level-1 meat; XP guesses increase thin < lean < fat.
            // All three tiers share the cache's sole burnt-snail item.
            row("dbrow.cooking_snail_thin") {
                production {
                    input("obj.snail_corpse1")
                    statReq("stat.cooking", 1)
                    xpTenths(400)
                    output("obj.snail_corpse_cooked1")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_snail")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            row("dbrow.cooking_snail_lean") {
                production {
                    input("obj.snail_corpse2")
                    statReq("stat.cooking", 1)
                    xpTenths(600)
                    output("obj.snail_corpse_cooked2")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_snail")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            row("dbrow.cooking_snail_fat") {
                production {
                    input("obj.snail_corpse3")
                    statReq("stat.cooking", 1)
                    xpTenths(800)
                    output("obj.snail_corpse_cooked3")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_snail")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            // Cache-verified: raw_chompy (2876) -> cooked_chompy (2878) / ruined_chompy (2880).
            // chompy_cooked/chompy_burned (7228/7226) belong to the separate spit_skewered_chompy
            // (7230) chain deferred in audit section 8. Level and XP here remain inferred.
            row("dbrow.cooking_chompy") {
                production {
                    input("obj.raw_chompy")
                    statReq("stat.cooking", 1)
                    xpTenths(100)
                    output("obj.cooked_chompy")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.ruined_chompy")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            // UNVERIFIED: kyatt/larupia/graahk use level-1 meat curves by analogy to other hunted
            // meat. Cache confirms their shared burnt_largebeast (29157), distinct from burnt_kebbit
            // and burnt_antelope; no success chart was found.
            row("dbrow.cooking_kyatt") {
                production {
                    input("obj.hunting_kyatt_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.kyatt_cooked")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_largebeast")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            row("dbrow.cooking_larupia") {
                production {
                    input("obj.hunting_larupia_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.larupia_cooked")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_largebeast")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            row("dbrow.cooking_graahk") {
                production {
                    input("obj.hunting_graahk_meat")
                    statReq("stat.cooking", 1)
                    xpTenths(300)
                    output("obj.graahk_cooked")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_largebeast")
                column(COL_LOW_BASE, 128)
                column(COL_HIGH_BASE, 512)
            }
            // UNVERIFIED: sunlight/moonlight antelope levels, XP and curves are estimates based
            // on similarly tiered fish. Cache confirms both use burnt_antelope (29159); no chart or
            // capture confirms the recipe values.
            row("dbrow.cooking_antelope_sunlight") {
                production {
                    input("obj.hunting_antelopesun_meat")
                    statReq("stat.cooking", 61)
                    xpTenths(1450)
                    output("obj.antelopesun_cooked")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_antelope")
                column(COL_LOW_BASE, 11)
                column(COL_HIGH_BASE, 275)
            }
            row("dbrow.cooking_antelope_moonlight") {
                production {
                    input("obj.hunting_antelopemoon_meat")
                    statReq("stat.cooking", 78)
                    xpTenths(1950)
                    output("obj.antelopemoon_cooked")
                    category("Meat")
                }
                columnRSCM(COL_BURNT, "obj.burnt_antelope")
                column(COL_LOW_BASE, 1)
                column(COL_HIGH_BASE, 202)
            }
        }
}
