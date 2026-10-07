package dev.openrune.tables.skills

import dev.openrune.tables.production.productionTable

object Herblore {

    /** Unfinished potions award no XP; the finished-potion row holds the full award. */
    fun unfinishedPotions() =
        productionTable("dbtable.herblore_unfinished", serverOnly = true) {
            row("dbrow.herblore_guam_unfinished") {
                production {
                    input("obj.guam_leaf")
                    statReq("stat.herblore", 3)
                    xpTenths(0)
                    output("obj.guamvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_marrentill_unfinished") {
                production {
                    input("obj.marentill")
                    statReq("stat.herblore", 5)
                    xpTenths(0)
                    output("obj.marrentillvial")
                    category("Unfinished Potions")
                }
            }
            // Unfinished-potion levels come from item param 436, not herb cleaning levels:
            // tarromin cleans at 11 but its unfinished potion requires 12 (cache-verified).
            row("dbrow.herblore_tarromin_unfinished") {
                production {
                    input("obj.tarromin")
                    statReq("stat.herblore", 12)
                    xpTenths(0)
                    output("obj.tarrominvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_harralander_unfinished") {
                production {
                    input("obj.harralander")
                    statReq("stat.herblore", 22)
                    xpTenths(0)
                    output("obj.harralandervial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_ranarr_unfinished") {
                production {
                    input("obj.ranarr_weed")
                    statReq("stat.herblore", 30)
                    xpTenths(0)
                    output("obj.ranarrvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_toadflax_unfinished") {
                production {
                    input("obj.toadflax")
                    statReq("stat.herblore", 34)
                    xpTenths(0)
                    output("obj.toadflaxvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_irit_unfinished") {
                production {
                    input("obj.irit_leaf")
                    statReq("stat.herblore", 45)
                    xpTenths(0)
                    output("obj.iritvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_avantoe_unfinished") {
                production {
                    input("obj.avantoe")
                    statReq("stat.herblore", 50)
                    xpTenths(0)
                    output("obj.avantoevial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_kwuarm_unfinished") {
                production {
                    input("obj.kwuarm")
                    statReq("stat.herblore", 55)
                    xpTenths(0)
                    output("obj.kwuarmvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_snapdragon_unfinished") {
                production {
                    input("obj.snapdragon")
                    statReq("stat.herblore", 63)
                    xpTenths(0)
                    output("obj.snapdragonvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_cadantine_unfinished") {
                production {
                    input("obj.cadantine")
                    statReq("stat.herblore", 66)
                    xpTenths(0)
                    output("obj.cadantinevial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_lantadyme_unfinished") {
                production {
                    input("obj.lantadyme")
                    statReq("stat.herblore", 69)
                    xpTenths(0)
                    output("obj.lantadymevial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_dwarf_weed_unfinished") {
                production {
                    input("obj.dwarf_weed")
                    statReq("stat.herblore", 72)
                    xpTenths(0)
                    output("obj.dwarfweedvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_torstol_unfinished") {
                production {
                    input("obj.torstol")
                    statReq("stat.herblore", 78)
                    xpTenths(0)
                    output("obj.torstolvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_huasca_unfinished") {
                production {
                    input("obj.huasca")
                    statReq("stat.herblore", 58)
                    xpTenths(0)
                    output("obj.huascavial")
                    category("Unfinished Potions")
                }
            }
        }

    fun finishedPotions() =
        productionTable("dbtable.herblore_finished", serverOnly = true) {
            row("dbrow.herblore_attack_potion") {
                production {
                    input("obj.guamvial")
                    input("obj.eye_of_newt")
                    statReq("stat.herblore", 3)
                    xpTenths(250)
                    output("obj.3dose1attack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antipoison") {
                production {
                    input("obj.marrentillvial")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 5)
                    xpTenths(380)
                    output("obj.3doseantipoison")
                    category("Potions")
                }
            }
            row("dbrow.herblore_strength_potion") {
                production {
                    input("obj.tarrominvial")
                    input("obj.limpwurt_root")
                    statReq("stat.herblore", 12)
                    xpTenths(500)
                    output("obj.3dose1strength")
                    category("Potions")
                }
            }
            row("dbrow.herblore_restore_potion") {
                production {
                    input("obj.harralandervial")
                    input("obj.red_spiders_eggs")
                    statReq("stat.herblore", 22)
                    xpTenths(630)
                    output("obj.3dosestatrestore")
                    category("Potions")
                }
            }
            row("dbrow.herblore_energy_potion") {
                production {
                    input("obj.harralandervial")
                    input("obj.chocolate_dust")
                    statReq("stat.herblore", 26)
                    xpTenths(680)
                    output("obj.3dose1energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_prayer_potion") {
                production {
                    input("obj.ranarrvial")
                    input("obj.snape_grass")
                    statReq("stat.herblore", 38)
                    xpTenths(880)
                    output("obj.3doseprayerrestore")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_attack") {
                production {
                    input("obj.iritvial")
                    input("obj.eye_of_newt")
                    statReq("stat.herblore", 45)
                    xpTenths(1000)
                    output("obj.3dose2attack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_superantipoison") {
                production {
                    input("obj.iritvial")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 48)
                    xpTenths(1050)
                    output("obj.3dose2antipoison")
                    category("Potions")
                }
            }
            row("dbrow.herblore_fishing_potion") {
                production {
                    input("obj.avantoevial")
                    input("obj.snape_grass")
                    statReq("stat.herblore", 50)
                    xpTenths(1130)
                    output("obj.3dosefisherspotion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_energy") {
                production {
                    input("obj.avantoevial")
                    input("obj.mortmyremushroom")
                    statReq("stat.herblore", 52)
                    xpTenths(1180)
                    output("obj.3dose2energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_strength") {
                production {
                    input("obj.kwuarmvial")
                    input("obj.limpwurt_root")
                    statReq("stat.herblore", 55)
                    xpTenths(1250)
                    output("obj.3dose2strength")
                    category("Potions")
                }
            }
            row("dbrow.herblore_weapon_poison") {
                production {
                    input("obj.kwuarmvial")
                    input("obj.dragon_scale_dust")
                    statReq("stat.herblore", 60)
                    xpTenths(1380)
                    output("obj.weapon_poison")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_restore") {
                production {
                    input("obj.snapdragonvial")
                    input("obj.red_spiders_eggs")
                    statReq("stat.herblore", 63)
                    xpTenths(1430)
                    output("obj.3dose2restore")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_defence") {
                production {
                    input("obj.cadantinevial")
                    input("obj.white_berries")
                    statReq("stat.herblore", 66)
                    xpTenths(1500)
                    output("obj.3dose2defense")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antifire") {
                production {
                    input("obj.lantadymevial")
                    input("obj.dragon_scale_dust")
                    statReq("stat.herblore", 69)
                    xpTenths(1580)
                    output("obj.3dose1antidragon")
                    category("Potions")
                }
            }
            // Wiki: Super antifire potion uses Antifire(4) + crushed superior dragon bones.
            // Cache antidragon families: 1=normal, 2=extended, 3=super, 4=extended super;
            // 21978 is Super antifire(4), 22209 is Extended super antifire(4).
            row("dbrow.herblore_super_antifire") {
                production {
                    input("obj.4dose1antidragon")
                    input("obj.crushed_dragon_bones")
                    statReq("stat.herblore", 92)
                    xpTenths(1300)
                    output("obj.4dose3antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_ranging_potion") {
                production {
                    input("obj.dwarfweedvial")
                    input("obj.wine_of_zamorak")
                    statReq("stat.herblore", 72)
                    xpTenths(1630)
                    output("obj.3doserangerspotion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_magic_potion") {
                production {
                    input("obj.lantadymevial")
                    input("obj.cactus_potato")
                    statReq("stat.herblore", 76)
                    xpTenths(1730)
                    output("obj.3dose1magic")
                    category("Potions")
                }
            }
            row("dbrow.herblore_zamorak_brew") {
                production {
                    input("obj.torstolvial")
                    input("obj.jangerberries")
                    statReq("stat.herblore", 78)
                    xpTenths(1750)
                    output("obj.3dosepotionofzamorak")
                    category("Potions")
                }
            }
            row("dbrow.herblore_saradomin_brew") {
                production {
                    input("obj.toadflaxvial")
                    input("obj.crushed_bird_nest")
                    statReq("stat.herblore", 81)
                    xpTenths(1800)
                    output("obj.3dosepotionofsaradomin")
                    category("Potions")
                }
            }
            row("dbrow.herblore_defence_potion") {
                production {
                    input("obj.ranarrvial")
                    input("obj.white_berries")
                    statReq("stat.herblore", 30)
                    xpTenths(750)
                    output("obj.3dose1defense")
                    category("Potions")
                }
            }
            row("dbrow.herblore_agility_potion") {
                production {
                    input("obj.toadflaxvial")
                    input("obj.toads_legs")
                    statReq("stat.herblore", 34)
                    xpTenths(800)
                    output("obj.3dose1agility")
                    category("Potions")
                }
            }
            row("dbrow.herblore_combat_potion") {
                production {
                    input("obj.harralandervial")
                    input("obj.ground_desert_goat_horn")
                    statReq("stat.herblore", 36)
                    xpTenths(840)
                    output("obj.3dosecombat")
                    category("Potions")
                }
            }
            row("dbrow.herblore_hunter_potion") {
                production {
                    input("obj.avantoevial")
                    input("obj.huntingbeast_sabreteeth_dust")
                    statReq("stat.herblore", 53)
                    xpTenths(1200)
                    output("obj.3dosehunting")
                    category("Potions")
                }
            }
            row("dbrow.herblore_cadantine_blood_unf") {
                production {
                    input("obj.cadantine")
                    input("obj.vial_blood")
                    statReq("stat.herblore", 80)
                    xpTenths(0)
                    output("obj.cadantine_bloodvial")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_bastion_potion") {
                production {
                    input("obj.cadantine_bloodvial")
                    input("obj.wine_of_zamorak")
                    statReq("stat.herblore", 80)
                    xpTenths(1550)
                    output("obj.3dosebastion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_battlemage_potion") {
                production {
                    input("obj.cadantine_bloodvial")
                    input("obj.cactus_potato")
                    statReq("stat.herblore", 80)
                    xpTenths(1550)
                    output("obj.3dosebattlemage")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_combat_potion") {
                production {
                    input("obj.torstol")
                    input("obj.4dose2attack")
                    input("obj.4dose2strength")
                    input("obj.4dose2defense")
                    statReq("stat.herblore", 90)
                    xpTenths(1500)
                    output("obj.4dose2combat")
                    category("Potions")
                }
            }
            // Wiki Anti-venom: five scales and 30 XP per antidote++ dose.
            row("dbrow.herblore_antivenom_1") {
                production {
                    input("obj.antidote++1")
                    input("obj.snakeboss_scale", 5)
                    statReq("stat.herblore", 87)
                    xpTenths(300)
                    output("obj.antivenom1")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antivenom_2") {
                production {
                    input("obj.antidote++2")
                    input("obj.snakeboss_scale", 10)
                    statReq("stat.herblore", 87)
                    xpTenths(600)
                    output("obj.antivenom2")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antivenom_3") {
                production {
                    input("obj.antidote++3")
                    input("obj.snakeboss_scale", 15)
                    statReq("stat.herblore", 87)
                    xpTenths(900)
                    output("obj.antivenom3")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antivenom_4") {
                production {
                    input("obj.antidote++4")
                    input("obj.snakeboss_scale", 20)
                    statReq("stat.herblore", 87)
                    xpTenths(1200)
                    output("obj.antivenom4")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antivenom_plus") {
                production {
                    input("obj.antivenom4")
                    input("obj.torstol")
                    statReq("stat.herblore", 94)
                    xpTenths(1250)
                    output("obj.antivenom+4")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antivenom_plus_unf") {
                production {
                    input("obj.antivenom4")
                    input("obj.torstolvial")
                    statReq("stat.herblore", 94)
                    xpTenths(1250)
                    output("obj.antivenom+4")
                    category("Potions")
                }
            }
            // Wiki: Extended anti-venom+ requires level 94, one araxyte venom sac per dose and
            // awards 20 XP per dose. FinishedPotionsEvents applies the 3-tick cadence.
            row("dbrow.herblore_extended_antivenom_plus") {
                production {
                    input("obj.antivenom+3")
                    input("obj.araxyte_venom_sack", 3)
                    statReq("stat.herblore", 94)
                    xpTenths(600)
                    output("obj.extended_antivenom+3")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antivenom_plus_1") {
                production {
                    input("obj.antivenom+1")
                    input("obj.araxyte_venom_sack", 1)
                    statReq("stat.herblore", 94)
                    xpTenths(200)
                    output("obj.extended_antivenom+1")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antivenom_plus_2") {
                production {
                    input("obj.antivenom+2")
                    input("obj.araxyte_venom_sack", 2)
                    statReq("stat.herblore", 94)
                    xpTenths(400)
                    output("obj.extended_antivenom+2")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antivenom_plus_4") {
                production {
                    input("obj.antivenom+4")
                    input("obj.araxyte_venom_sack", 4)
                    statReq("stat.herblore", 94)
                    xpTenths(800)
                    output("obj.extended_antivenom+4")
                    category("Potions")
                }
            }
            // Wiki Antidote+/++: coconut-milk unfinished steps pay no XP; roots pay the full award.
            // Brews four doses, unlike vial-of-water potions.
            row("dbrow.herblore_antidote_plus_unf") {
                production {
                    input("obj.vial_coconut_milk")
                    input("obj.toadflax")
                    statReq("stat.herblore", 68)
                    xpTenths(0)
                    output("obj.unfinished_antidote+")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_antidote_plus") {
                production {
                    input("obj.unfinished_antidote+")
                    input("obj.yew_roots")
                    statReq("stat.herblore", 68)
                    xpTenths(1550)
                    output("obj.antidote+4")
                    category("Potions")
                }
            }
            row("dbrow.herblore_antidote_plus_plus_unf") {
                production {
                    input("obj.vial_coconut_milk")
                    input("obj.irit_leaf")
                    statReq("stat.herblore", 79)
                    xpTenths(0)
                    output("obj.unfinished_antidote++")
                    category("Unfinished Potions")
                }
            }
            // Wiki: 177.5 XP, stored in tenths.
            row("dbrow.herblore_antidote_plus_plus") {
                production {
                    input("obj.unfinished_antidote++")
                    input("obj.magic_roots")
                    statReq("stat.herblore", 79)
                    xpTenths(1780)
                    output("obj.antidote++4")
                    category("Potions")
                }
            }
            // Wiki Extended antifire: one shard and 27.5 XP per dose.
            row("dbrow.herblore_extended_antifire_1") {
                production {
                    input("obj.1dose1antidragon")
                    input("obj.lava_shard", 1)
                    statReq("stat.herblore", 84)
                    xpTenths(280)
                    output("obj.1dose2antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antifire_2") {
                production {
                    input("obj.2dose1antidragon")
                    input("obj.lava_shard", 2)
                    statReq("stat.herblore", 84)
                    xpTenths(550)
                    output("obj.2dose2antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antifire_3") {
                production {
                    input("obj.3dose1antidragon")
                    input("obj.lava_shard", 3)
                    statReq("stat.herblore", 84)
                    xpTenths(830)
                    output("obj.3dose2antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_antifire_4") {
                production {
                    input("obj.4dose1antidragon")
                    input("obj.lava_shard", 4)
                    statReq("stat.herblore", 84)
                    xpTenths(1100)
                    output("obj.4dose2antidragon")
                    category("Potions")
                }
            }
            // Wiki Extended super antifire: one shard and 40 XP per dose.
            row("dbrow.herblore_extended_super_antifire_1") {
                production {
                    input("obj.1dose3antidragon")
                    input("obj.lava_shard", 1)
                    statReq("stat.herblore", 98)
                    xpTenths(400)
                    output("obj.1dose4antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_super_antifire_2") {
                production {
                    input("obj.2dose3antidragon")
                    input("obj.lava_shard", 2)
                    statReq("stat.herblore", 98)
                    xpTenths(800)
                    output("obj.2dose4antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_super_antifire_3") {
                production {
                    input("obj.3dose3antidragon")
                    input("obj.lava_shard", 3)
                    statReq("stat.herblore", 98)
                    xpTenths(1200)
                    output("obj.3dose4antidragon")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_super_antifire_4") {
                production {
                    input("obj.4dose3antidragon")
                    input("obj.lava_shard", 4)
                    statReq("stat.herblore", 98)
                    xpTenths(1600)
                    output("obj.4dose4antidragon")
                    category("Potions")
                }
            }
            // Second route (wiki: Extended super antifire - Extended antifire(4) + crushed
            // superior dragon bones, lvl 98, 180 xp).
            row("dbrow.herblore_extended_super_antifire_bones") {
                production {
                    input("obj.4dose2antidragon")
                    input("obj.crushed_dragon_bones")
                    statReq("stat.herblore", 98)
                    xpTenths(1800)
                    output("obj.4dose4antidragon")
                    category("Potions")
                }
            }
            // Wiki Sanfew serum: three per-dose steps with separately craftable intermediate mixtures.
            row("dbrow.herblore_sanfew_step1_1") {
                production {
                    input("obj.1dose2restore")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 65)
                    xpTenths(290)
                    output("obj.sanfew_salve_step_1_1_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step1_2") {
                production {
                    input("obj.2dose2restore")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 65)
                    xpTenths(380)
                    output("obj.sanfew_salve_step_1_2_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step1_3") {
                production {
                    input("obj.3dose2restore")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 65)
                    xpTenths(480)
                    output("obj.sanfew_salve_step_1_3_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step1_4") {
                production {
                    input("obj.4dose2restore")
                    input("obj.unicorn_horn_dust")
                    statReq("stat.herblore", 65)
                    xpTenths(570)
                    output("obj.sanfew_salve_step_1_4_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step2_1") {
                production {
                    input("obj.sanfew_salve_step_1_1_dose")
                    input("obj.snake_weed")
                    statReq("stat.herblore", 65)
                    xpTenths(320)
                    output("obj.sanfew_salve_step_2_1_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step2_2") {
                production {
                    input("obj.sanfew_salve_step_1_2_dose")
                    input("obj.snake_weed")
                    statReq("stat.herblore", 65)
                    xpTenths(420)
                    output("obj.sanfew_salve_step_2_2_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step2_3") {
                production {
                    input("obj.sanfew_salve_step_1_3_dose")
                    input("obj.snake_weed")
                    statReq("stat.herblore", 65)
                    xpTenths(530)
                    output("obj.sanfew_salve_step_2_3_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_step2_4") {
                production {
                    input("obj.sanfew_salve_step_1_4_dose")
                    input("obj.snake_weed")
                    statReq("stat.herblore", 65)
                    xpTenths(630)
                    output("obj.sanfew_salve_step_2_4_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_serum_1") {
                production {
                    input("obj.sanfew_salve_step_2_1_dose")
                    input("obj.nail_beast_nail")
                    statReq("stat.herblore", 65)
                    xpTenths(360)
                    output("obj.sanfew_salve_1_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_serum_2") {
                production {
                    input("obj.sanfew_salve_step_2_2_dose")
                    input("obj.nail_beast_nail")
                    statReq("stat.herblore", 65)
                    xpTenths(480)
                    output("obj.sanfew_salve_2_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_serum_3") {
                production {
                    input("obj.sanfew_salve_step_2_3_dose")
                    input("obj.nail_beast_nail")
                    statReq("stat.herblore", 65)
                    xpTenths(600)
                    output("obj.sanfew_salve_3_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_sanfew_serum_4") {
                production {
                    input("obj.sanfew_salve_step_2_4_dose")
                    input("obj.nail_beast_nail")
                    statReq("stat.herblore", 65)
                    xpTenths(720)
                    output("obj.sanfew_salve_4_dose")
                    category("Potions")
                }
            }
            row("dbrow.herblore_stamina_potion_1") {
                production {
                    input("obj.1dose2energy")
                    input("obj.amylase")
                    statReq("stat.herblore", 77)
                    xpTenths(250)
                    output("obj.1dosestamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_stamina_potion_2") {
                production {
                    input("obj.2dose2energy")
                    input("obj.amylase", 2)
                    statReq("stat.herblore", 77)
                    xpTenths(510)
                    output("obj.2dosestamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_stamina_potion_3") {
                production {
                    input("obj.3dose2energy")
                    input("obj.amylase", 3)
                    statReq("stat.herblore", 77)
                    xpTenths(770)
                    output("obj.3dosestamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_stamina_potion_4") {
                production {
                    input("obj.4dose2energy")
                    input("obj.amylase", 5)
                    statReq("stat.herblore", 77)
                    xpTenths(1020)
                    output("obj.4dosestamina")
                    category("Potions")
                }
            }
            // Wiki: Divine potions award 0.5 XP per dose; xpTenths must therefore be 5/10/15/20
            // for one through four doses (e.g. Divine super combat potion).
            row("dbrow.herblore_divine_bastion_1") {
                production {
                    input("obj.1dosebastion")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 86)
                    xpTenths(5)
                    output("obj.1dosedivinebastion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_bastion_2") {
                production {
                    input("obj.2dosebastion")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 86)
                    xpTenths(10)
                    output("obj.2dosedivinebastion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_bastion_3") {
                production {
                    input("obj.3dosebastion")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 86)
                    xpTenths(15)
                    output("obj.3dosedivinebastion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_bastion_4") {
                production {
                    input("obj.4dosebastion")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 86)
                    xpTenths(20)
                    output("obj.4dosedivinebastion")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_battlemage_1") {
                production {
                    input("obj.1dosebattlemage")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 86)
                    xpTenths(5)
                    output("obj.1dosedivinebattlemage")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_battlemage_2") {
                production {
                    input("obj.2dosebattlemage")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 86)
                    xpTenths(10)
                    output("obj.2dosedivinebattlemage")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_battlemage_3") {
                production {
                    input("obj.3dosebattlemage")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 86)
                    xpTenths(15)
                    output("obj.3dosedivinebattlemage")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_battlemage_4") {
                production {
                    input("obj.4dosebattlemage")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 86)
                    xpTenths(20)
                    output("obj.4dosedivinebattlemage")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_magic_1") {
                production {
                    input("obj.1dose1magic")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 78)
                    xpTenths(5)
                    output("obj.1dosedivinemagic")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_magic_2") {
                production {
                    input("obj.2dose1magic")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 78)
                    xpTenths(10)
                    output("obj.2dosedivinemagic")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_magic_3") {
                production {
                    input("obj.3dose1magic")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 78)
                    xpTenths(15)
                    output("obj.3dosedivinemagic")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_magic_4") {
                production {
                    input("obj.4dose1magic")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 78)
                    xpTenths(20)
                    output("obj.4dosedivinemagic")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_ranging_1") {
                production {
                    input("obj.1doserangerspotion")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 74)
                    xpTenths(5)
                    output("obj.1dosedivinerange")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_ranging_2") {
                production {
                    input("obj.2doserangerspotion")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 74)
                    xpTenths(10)
                    output("obj.2dosedivinerange")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_ranging_3") {
                production {
                    input("obj.3doserangerspotion")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 74)
                    xpTenths(15)
                    output("obj.3dosedivinerange")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_ranging_4") {
                production {
                    input("obj.4doserangerspotion")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 74)
                    xpTenths(20)
                    output("obj.4dosedivinerange")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_attack_1") {
                production {
                    input("obj.1dose2attack")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 70)
                    xpTenths(5)
                    output("obj.1dosedivineattack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_attack_2") {
                production {
                    input("obj.2dose2attack")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 70)
                    xpTenths(10)
                    output("obj.2dosedivineattack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_attack_3") {
                production {
                    input("obj.3dose2attack")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 70)
                    xpTenths(15)
                    output("obj.3dosedivineattack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_attack_4") {
                production {
                    input("obj.4dose2attack")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 70)
                    xpTenths(20)
                    output("obj.4dosedivineattack")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_combat_1") {
                production {
                    input("obj.1dose2combat")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 97)
                    xpTenths(5)
                    output("obj.1dosedivinecombat")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_combat_2") {
                production {
                    input("obj.2dose2combat")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 97)
                    xpTenths(10)
                    output("obj.2dosedivinecombat")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_combat_3") {
                production {
                    input("obj.3dose2combat")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 97)
                    xpTenths(15)
                    output("obj.3dosedivinecombat")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_combat_4") {
                production {
                    input("obj.4dose2combat")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 97)
                    xpTenths(20)
                    output("obj.4dosedivinecombat")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_defence_1") {
                production {
                    input("obj.1dose2defense")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 70)
                    xpTenths(5)
                    output("obj.1dosedivinedefence")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_defence_2") {
                production {
                    input("obj.2dose2defense")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 70)
                    xpTenths(10)
                    output("obj.2dosedivinedefence")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_defence_3") {
                production {
                    input("obj.3dose2defense")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 70)
                    xpTenths(15)
                    output("obj.3dosedivinedefence")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_defence_4") {
                production {
                    input("obj.4dose2defense")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 70)
                    xpTenths(20)
                    output("obj.4dosedivinedefence")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_strength_1") {
                production {
                    input("obj.1dose2strength")
                    input("obj.sote_crystal_dust")
                    statReq("stat.herblore", 70)
                    xpTenths(5)
                    output("obj.1dosedivinestrength")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_strength_2") {
                production {
                    input("obj.2dose2strength")
                    input("obj.sote_crystal_dust", 2)
                    statReq("stat.herblore", 70)
                    xpTenths(10)
                    output("obj.2dosedivinestrength")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_strength_3") {
                production {
                    input("obj.3dose2strength")
                    input("obj.sote_crystal_dust", 3)
                    statReq("stat.herblore", 70)
                    xpTenths(15)
                    output("obj.3dosedivinestrength")
                    category("Potions")
                }
            }
            row("dbrow.herblore_divine_super_strength_4") {
                production {
                    input("obj.4dose2strength")
                    input("obj.sote_crystal_dust", 4)
                    statReq("stat.herblore", 70)
                    xpTenths(20)
                    output("obj.4dosedivinestrength")
                    category("Potions")
                }
            }

            // Relicym's balm uses Rogue's purse and water, producing three doses directly.
            // Source: OSRS wiki Relicym's balm. Deferred quest recipes:
            row("dbrow.herblore_relicyms_balm_unf") {
                production {
                    input("obj.rogues_purse")
                    input("obj.vial_water")
                    statReq("stat.herblore", 8)
                    xpTenths(0)
                    output("obj.rogues_purse_sol")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_relicyms_balm") {
                production {
                    input("obj.rogues_purse_sol")
                    input("obj.snake_weed")
                    statReq("stat.herblore", 8)
                    xpTenths(400)
                    output("obj.relicyms_balm3")
                    category("Potions")
                }
            }
            row("dbrow.herblore_serum_207") {
                production {
                    input("obj.tarrominvial")
                    input("obj.ashes")
                    statReq("stat.herblore", 15)
                    xpTenths(500)
                    output("obj.mort_serum3")
                    category("Potions")
                }
            }
            row("dbrow.herblore_compost_potion") {
                production {
                    input("obj.harralandervial")
                    input("obj.fossil_volcanic_ash")
                    statReq("stat.herblore", 22)
                    xpTenths(600)
                    output("obj.supercompost_potion_3")
                    category("Potions")
                }
            }
            // Blamish oil (wiki: Blamish oil) - single-dose item, not a 1-4 dose family.
            row("dbrow.herblore_blamish_oil") {
                production {
                    input("obj.harralandervial")
                    input("obj.blamish_snail_slime")
                    statReq("stat.herblore", 25)
                    xpTenths(800)
                    output("obj.blamish_oil")
                    category("Potions")
                }
            }
            // Goblin potion (wiki: Goblin potion) - Land of the Goblins content, ungated here
            // since the quest itself is unimplemented (audit doc open question 3).
            row("dbrow.herblore_goblin_potion") {
                production {
                    input("obj.toadflaxvial")
                    input("obj.lotg_pharmakos_berry")
                    statReq("stat.herblore", 47)
                    xpTenths(550)
                    output("obj.lotg_3dosegoblin")
                    category("Potions")
                }
            }
            // Anti-odour salt (wiki: Anti-odour salt) - not vial-based: Elkhorn coral + 5 crab
            // paste, stackable single item.
            row("dbrow.herblore_anti_odour_salt") {
                production {
                    input("obj.coral_elkhorn")
                    input("obj.crab_paste", 5)
                    statReq("stat.herblore", 49)
                    xpTenths(110)
                    output("obj.anti_odour_salt")
                    category("Potions")
                }
            }
            row("dbrow.herblore_goading_potion") {
                production {
                    input("obj.harralandervial")
                    input("obj.aldarium")
                    statReq("stat.herblore", 54)
                    xpTenths(1320)
                    output("obj.3dosegoading")
                    category("Potions")
                }
            }
            // Haemostatic poultice + Haemostatic dressing (wiki: Haemostatic poultice,
            // Haemostatic dressing) - a 2-step chain like antidote+/++, the poultice step is
            // itself categorised "Unfinished Potions" on the wiki.
            row("dbrow.herblore_haemostatic_poultice") {
                production {
                    input("obj.elkhornvial")
                    input("obj.squid_paste")
                    statReq("stat.herblore", 56)
                    xpTenths(270)
                    output("obj.haemostatic_poultice")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_haemostatic_dressing") {
                production {
                    input("obj.haemostatic_poultice")
                    input("obj.cotton_yarn")
                    statReq("stat.herblore", 56)
                    xpTenths(1000)
                    output("obj.3dosehaemostaticdressing")
                    category("Potions")
                }
            }
            // Wiki: Magic essence models only the finishing step. Its fairytale2_ unfinished
            // potion comes from Fairytale II's unimplemented brewing minigame.
            row("dbrow.herblore_magic_essence") {
                production {
                    input("obj.fairytale2_starflowervial")
                    input("obj.fairytale2_ground_gorak_claws")
                    statReq("stat.herblore", 57)
                    xpTenths(1300)
                    output("obj.3dosemagicess")
                    category("Potions")
                }
            }
            row("dbrow.herblore_prayer_regeneration") {
                production {
                    input("obj.huascavial")
                    input("obj.aldarium")
                    statReq("stat.herblore", 58)
                    xpTenths(1320)
                    output("obj.3dose1prayer_regeneration")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_fishing_potion") {
                production {
                    input("obj.pillarvial")
                    input("obj.haddock_eye")
                    statReq("stat.herblore", 62)
                    xpTenths(1405)
                    output("obj.3dose2fisherspotion")
                    category("Potions")
                }
            }
            // Extreme energy potion (wiki: Extreme energy potion) - per-dose chain from Super
            // energy potion + Yellow fin, same shape as the existing extended-antifire rows.
            row("dbrow.herblore_extreme_energy_1") {
                production {
                    input("obj.1dose2energy")
                    input("obj.yellow_fin", 1)
                    statReq("stat.herblore", 66)
                    xpTenths(210)
                    output("obj.1dose3energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extreme_energy_2") {
                production {
                    input("obj.2dose2energy")
                    input("obj.yellow_fin", 2)
                    statReq("stat.herblore", 66)
                    xpTenths(420)
                    output("obj.2dose3energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extreme_energy_3") {
                production {
                    input("obj.3dose2energy")
                    input("obj.yellow_fin", 3)
                    statReq("stat.herblore", 66)
                    xpTenths(630)
                    output("obj.3dose3energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extreme_energy_4") {
                production {
                    input("obj.4dose2energy")
                    input("obj.yellow_fin", 4)
                    statReq("stat.herblore", 66)
                    xpTenths(840)
                    output("obj.4dose3energy")
                    category("Potions")
                }
            }
            row("dbrow.herblore_super_hunter_potion") {
                production {
                    input("obj.pillarvial")
                    input("obj.crab_paste")
                    statReq("stat.herblore", 67)
                    xpTenths(1540)
                    output("obj.3dose2hunting")
                    category("Potions")
                }
            }
            row("dbrow.herblore_weapon_poison_plus_unf") {
                production {
                    input("obj.vial_coconut_milk")
                    input("obj.cactus_spine")
                    statReq("stat.herblore", 73)
                    xpTenths(0)
                    output("obj.unfinished_weapon_poison+")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_weapon_poison_plus") {
                production {
                    input("obj.unfinished_weapon_poison+")
                    input("obj.red_spiders_eggs")
                    statReq("stat.herblore", 73)
                    xpTenths(1900)
                    output("obj.weapon_poison+")
                    category("Potions")
                }
            }
            // Weapon poison++ (wiki: Weapon poison(++)). Live also accepts cave nightshade; that
            // item does not resolve in this cache, so only the (regular) nightshade route is
            // modelled.
            row("dbrow.herblore_weapon_poison_plusplus_unf") {
                production {
                    input("obj.vial_coconut_milk")
                    input("obj.nightshade")
                    statReq("stat.herblore", 82)
                    xpTenths(0)
                    output("obj.unfinished_weapon_poison++")
                    category("Unfinished Potions")
                }
            }
            row("dbrow.herblore_weapon_poison_plusplus") {
                production {
                    input("obj.unfinished_weapon_poison++")
                    input("obj.poisonivy_berries")
                    statReq("stat.herblore", 82)
                    xpTenths(1900)
                    output("obj.weapon_poison++")
                    category("Potions")
                }
            }
            row("dbrow.herblore_surge_potion") {
                production {
                    input("obj.torstolvial")
                    input("obj.demonic_tallow")
                    statReq("stat.herblore", 81)
                    xpTenths(1850)
                    output("obj.3dosesurge")
                    category("Potions")
                }
            }
            row("dbrow.herblore_ancient_brew") {
                production {
                    input("obj.dwarfweedvial")
                    input("obj.nihil_dust")
                    statReq("stat.herblore", 85)
                    xpTenths(1900)
                    output("obj.3doseancientbrew")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_stamina_1") {
                production {
                    input("obj.1dosestamina")
                    input("obj.marlin_scales", 1)
                    statReq("stat.herblore", 85)
                    xpTenths(275)
                    output("obj.1dose2stamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_stamina_2") {
                production {
                    input("obj.2dosestamina")
                    input("obj.marlin_scales", 2)
                    statReq("stat.herblore", 85)
                    xpTenths(550)
                    output("obj.2dose2stamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_stamina_3") {
                production {
                    input("obj.3dosestamina")
                    input("obj.marlin_scales", 3)
                    statReq("stat.herblore", 85)
                    xpTenths(825)
                    output("obj.3dose2stamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_extended_stamina_4") {
                production {
                    input("obj.4dosestamina")
                    input("obj.marlin_scales", 4)
                    statReq("stat.herblore", 85)
                    xpTenths(1100)
                    output("obj.4dose2stamina")
                    category("Potions")
                }
            }
            row("dbrow.herblore_menaphite_remedy") {
                production {
                    input("obj.dwarfweedvial")
                    input("obj.lily_of_the_sands")
                    statReq("stat.herblore", 88)
                    xpTenths(2000)
                    output("obj.3dosestatrenewal")
                    category("Potions")
                }
            }
            row("dbrow.herblore_armadyl_brew") {
                production {
                    input("obj.umbralvial")
                    input("obj.rainbow_crab_paste")
                    statReq("stat.herblore", 89)
                    xpTenths(2050)
                    output("obj.3dosearmadylbrew")
                    category("Potions")
                }
            }
            row("dbrow.herblore_forgotten_brew_1") {
                production {
                    input("obj.1doseancientbrew")
                    input("obj.ancient_essence", 20)
                    statReq("stat.herblore", 91)
                    xpTenths(360)
                    output("obj.1doseforgottenbrew")
                    category("Potions")
                }
            }
            row("dbrow.herblore_forgotten_brew_2") {
                production {
                    input("obj.2doseancientbrew")
                    input("obj.ancient_essence", 40)
                    statReq("stat.herblore", 91)
                    xpTenths(720)
                    output("obj.2doseforgottenbrew")
                    category("Potions")
                }
            }
            row("dbrow.herblore_forgotten_brew_3") {
                production {
                    input("obj.3doseancientbrew")
                    input("obj.ancient_essence", 60)
                    statReq("stat.herblore", 91)
                    xpTenths(1080)
                    output("obj.3doseforgottenbrew")
                    category("Potions")
                }
            }
            row("dbrow.herblore_forgotten_brew_4") {
                production {
                    input("obj.4doseancientbrew")
                    input("obj.ancient_essence", 80)
                    statReq("stat.herblore", 91)
                    xpTenths(1450)
                    output("obj.4doseforgottenbrew")
                    category("Potions")
                }
            }
        }

    fun cleaningHerbs() =
        productionTable("dbtable.herblore_cleaning", serverOnly = true) {
            row("dbrow.herblore_clean_guam") {
                production {
                    input("obj.unidentified_guam")
                    statReq("stat.herblore", 3)
                    xpTenths(20)
                    output("obj.guam_leaf")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_marrentill") {
                production {
                    input("obj.unidentified_marentill")
                    statReq("stat.herblore", 5)
                    xpTenths(30)
                    output("obj.marentill")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_tarromin") {
                production {
                    input("obj.unidentified_tarromin")
                    statReq("stat.herblore", 11)
                    xpTenths(50)
                    output("obj.tarromin")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_harralander") {
                production {
                    input("obj.unidentified_harralander")
                    statReq("stat.herblore", 20)
                    xpTenths(60)
                    output("obj.harralander")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_ranarr") {
                production {
                    input("obj.unidentified_ranarr")
                    statReq("stat.herblore", 25)
                    xpTenths(80)
                    output("obj.ranarr_weed")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_toadflax") {
                production {
                    input("obj.unidentified_toadflax")
                    statReq("stat.herblore", 30)
                    xpTenths(80)
                    output("obj.toadflax")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_irit") {
                production {
                    input("obj.unidentified_irit")
                    statReq("stat.herblore", 40)
                    xpTenths(90)
                    output("obj.irit_leaf")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_avantoe") {
                production {
                    input("obj.unidentified_avantoe")
                    statReq("stat.herblore", 48)
                    xpTenths(100)
                    output("obj.avantoe")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_kwuarm") {
                production {
                    input("obj.unidentified_kwuarm")
                    statReq("stat.herblore", 54)
                    xpTenths(110)
                    output("obj.kwuarm")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_snapdragon") {
                production {
                    input("obj.unidentified_snapdragon")
                    statReq("stat.herblore", 59)
                    xpTenths(120)
                    output("obj.snapdragon")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_cadantine") {
                production {
                    input("obj.unidentified_cadantine")
                    statReq("stat.herblore", 65)
                    xpTenths(130)
                    output("obj.cadantine")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_lantadyme") {
                production {
                    input("obj.unidentified_lantadyme")
                    statReq("stat.herblore", 67)
                    xpTenths(130)
                    output("obj.lantadyme")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_dwarf_weed") {
                production {
                    input("obj.unidentified_dwarf_weed")
                    statReq("stat.herblore", 70)
                    xpTenths(130)
                    output("obj.dwarf_weed")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_torstol") {
                production {
                    input("obj.unidentified_torstol")
                    statReq("stat.herblore", 75)
                    xpTenths(140)
                    output("obj.torstol")
                    category("Herbs")
                }
            }

            row("dbrow.herblore_clean_huasca") {
                production {
                    input("obj.unidentified_huasca")
                    statReq("stat.herblore", 58)
                    xpTenths(118)
                    output("obj.huasca")
                    category("Herbs")
                }
            }
        }

    /**
     * Two-dose potions with roe/caviar; levels and XP are wiki-sourced from Barbarian Training's
     * Herblore table. Roe works through Restore mix; Energy mix onward requires caviar.
     */
    fun barbarianMixes() =
        productionTable("dbtable.herblore_barbarian_mixes", serverOnly = true) {
            row("dbrow.herblore_attack_mix") {
                production {
                    input("obj.2dose1attack")
                    input("obj.brut_roe")
                    statReq("stat.herblore", 4)
                    xpTenths(80)
                    output("obj.brutal_2dose1attack")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_attack_mix_caviar") {
                production {
                    input("obj.2dose1attack")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 4)
                    xpTenths(80)
                    output("obj.brutal_2dose1attack")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_antipoison_mix") {
                production {
                    input("obj.2doseantipoison")
                    input("obj.brut_roe")
                    statReq("stat.herblore", 6)
                    xpTenths(120)
                    output("obj.brutal_2doseantipoison")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_antipoison_mix_caviar") {
                production {
                    input("obj.2doseantipoison")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 6)
                    xpTenths(120)
                    output("obj.brutal_2doseantipoison")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_relicyms_mix_roe") {
                production {
                    input("obj.relicyms_balm2")
                    input("obj.brut_roe")
                    statReq("stat.herblore", 9)
                    xpTenths(140)
                    output("obj.brutal_relicyms_balm2")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_relicyms_mix_caviar") {
                production {
                    input("obj.relicyms_balm2")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 9)
                    xpTenths(140)
                    output("obj.brutal_relicyms_balm2")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_strength_mix") {
                production {
                    input("obj.2dose1strength")
                    input("obj.brut_roe")
                    statReq("stat.herblore", 14)
                    xpTenths(170)
                    output("obj.brutal_2dose1strength")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_strength_mix_caviar") {
                production {
                    input("obj.2dose1strength")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 14)
                    xpTenths(170)
                    output("obj.brutal_2dose1strength")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_stat_restore_mix") {
                production {
                    input("obj.2dosestatrestore")
                    input("obj.brut_roe")
                    statReq("stat.herblore", 24)
                    xpTenths(210)
                    output("obj.brutal_2dosestatrestore")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_restore_mix_caviar") {
                production {
                    input("obj.2dosestatrestore")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 24)
                    xpTenths(210)
                    output("obj.brutal_2dosestatrestore")
                    category("Barbarian Mixes")
                }
            }
            // Wiki: Energy mix onward requires caviar; roe works only through Restore mix.
            row("dbrow.herblore_energy_mix") {
                production {
                    input("obj.2dose1energy")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 29)
                    xpTenths(230)
                    output("obj.brutal_2dose1energy")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_defence_mix") {
                production {
                    input("obj.2dose1defense")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 33)
                    xpTenths(250)
                    output("obj.brutal_2dose1defense")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_agility_mix") {
                production {
                    input("obj.2dose1agility")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 37)
                    xpTenths(270)
                    output("obj.brutal_2dose1agility")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_combat_mix") {
                production {
                    input("obj.2dosecombat")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 40)
                    xpTenths(280)
                    output("obj.brutal_2dosecombat")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_prayer_mix") {
                production {
                    input("obj.2doseprayerrestore")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 42)
                    xpTenths(290)
                    output("obj.brutal_2doseprayerrestore")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_attack_mix") {
                production {
                    input("obj.2dose2attack")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 47)
                    xpTenths(330)
                    output("obj.brutal_2dose2attack")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_antipoison_mix") {
                production {
                    input("obj.2dose2antipoison")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 51)
                    xpTenths(350)
                    output("obj.brutal_2dose2antipoison")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_fishing_mix") {
                production {
                    input("obj.2dosefisherspotion")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 53)
                    xpTenths(380)
                    output("obj.brutal_2dosefisherspotion")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_energy_mix") {
                production {
                    input("obj.2dose2energy")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 56)
                    xpTenths(390)
                    output("obj.brutal_2dose2energy")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_hunting_mix") {
                production {
                    input("obj.2dosehunting")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 58)
                    xpTenths(400)
                    output("obj.brutal_2dose1hunting")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_strength_mix") {
                production {
                    input("obj.2dose2strength")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 59)
                    xpTenths(420)
                    output("obj.brutal_2dose2strength")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_magic_essence_mix") {
                production {
                    input("obj.2dosemagicess")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 61)
                    xpTenths(430)
                    output("obj.brutal_2dosemagicess")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_restore_mix") {
                production {
                    input("obj.2dose2restore")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 67)
                    xpTenths(480)
                    output("obj.brutal_2dose2restore")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_super_defence_mix") {
                production {
                    input("obj.2dose2defense")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 71)
                    xpTenths(500)
                    output("obj.brutal_2dose2defense")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_antidote_plus_mix") {
                production {
                    input("obj.antidote+2")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 74)
                    xpTenths(520)
                    output("obj.brutal_antidote+2")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_antifire_mix") {
                production {
                    input("obj.2dose1antidragon")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 75)
                    xpTenths(530)
                    output("obj.brutal_2dose1antidragon")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_ranging_mix") {
                production {
                    input("obj.2doserangerspotion")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 80)
                    xpTenths(540)
                    output("obj.brutal_2doserangerspotion")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_magic_mix") {
                production {
                    input("obj.2dose1magic")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 83)
                    xpTenths(570)
                    output("obj.brutal_2dose1magic")
                    category("Barbarian Mixes")
                }
            }
            row("dbrow.herblore_zamorak_mix") {
                production {
                    input("obj.2dosepotionofzamorak")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 85)
                    xpTenths(580)
                    output("obj.brutal_2dosepotionofzamorak")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_stamina_mix") {
                production {
                    input("obj.2dosestamina")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 86)
                    xpTenths(600)
                    output("obj.brutal_2dosestamina")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_extended_antifire_mix") {
                production {
                    input("obj.2dose2antidragon")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 91)
                    xpTenths(610)
                    output("obj.brutal_2dose2antidragon")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_ancient_mix") {
                production {
                    input("obj.2doseancientbrew")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 92)
                    xpTenths(630)
                    output("obj.brutal_2doseancientbrew")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_super_antifire_mix") {
                production {
                    input("obj.2dose3antidragon")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 98)
                    xpTenths(700)
                    output("obj.brutal_2dose3antidragon")
                    category("Barbarian Mixes")
                }
            }

            row("dbrow.herblore_extended_super_antifire_mix") {
                production {
                    input("obj.2dose4antidragon")
                    input("obj.brut_caviar")
                    statReq("stat.herblore", 99)
                    xpTenths(780)
                    output("obj.brutal_2dose4antidragon")
                    category("Barbarian Mixes")
                }
            }
        }

    fun swampTar() =
        productionTable("dbtable.herblore_swamp_tar", serverOnly = true) {
            row("dbrow.herblore_guam_tar") {
                production {
                    input("obj.guam_leaf")
                    statReq("stat.herblore", 19)
                    xpTenths(300)
                    output("obj.salamander_tar_green")
                    category("Swamp Tar")
                }
            }
            row("dbrow.herblore_marrentill_tar") {
                production {
                    input("obj.marentill")
                    statReq("stat.herblore", 31)
                    xpTenths(420)
                    output("obj.salamander_tar_orange")
                    category("Swamp Tar")
                }
            }
            row("dbrow.herblore_tarromin_tar") {
                production {
                    input("obj.tarromin")
                    statReq("stat.herblore", 39)
                    xpTenths(550)
                    output("obj.salamander_tar_red")
                    category("Swamp Tar")
                }
            }
            row("dbrow.herblore_harralander_tar") {
                production {
                    input("obj.harralander")
                    statReq("stat.herblore", 44)
                    xpTenths(720)
                    output("obj.salamander_tar_black")
                    category("Swamp Tar")
                }
            }
            row("dbrow.herblore_irit_tar") {
                production {
                    input("obj.irit_leaf")
                    statReq("stat.herblore", 55)
                    xpTenths(850)
                    output("obj.salamander_tar_mountain")
                    category("Swamp Tar")
                }
            }
        }

    fun crushing() =
        productionTable("dbtable.herblore_crushing", serverOnly = true) {
            row("dbrow.herblore_crush_bird_nest") {
                production {
                    input("obj.bird_nest_empty")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.crushed_bird_nest")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_chocolate") {
                production {
                    input("obj.chocolate_bar")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.chocolate_dust")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_unicorn_horn") {
                production {
                    input("obj.unicorn_horn")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.unicorn_horn_dust")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_dragon_scale") {
                production {
                    input("obj.blue_dragon_scale")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.dragon_scale_dust")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_goat_horn") {
                production {
                    input("obj.desert_goat_horn")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.ground_desert_goat_horn")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_superior_dragon_bones") {
                production {
                    input("obj.dragon_bones_superior")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.crushed_dragon_bones")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_kebbit_teeth") {
                production {
                    input("obj.huntingbeast_sabreteeth")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.huntingbeast_sabreteeth_dust")
                    category("Crushing")
                }
            }
            // Wiki: Lava scale shard - grinding a lava scale yields 3-6 shards (higher Herblore
            // raises the average; hard Wilderness Diary +50% - neither modelled yet, see report).
            // The 3-6 roll lives in CrushingEvents; output_amount here stays 1 as a placeholder.
            row("dbrow.herblore_crush_lava_scale") {
                production {
                    input("obj.lava_scale")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.lava_shard")
                    category("Crushing")
                }
            }
            row("dbrow.herblore_crush_crystal_dust") {
                production {
                    input("obj.prif_crystal_shard")
                    statReq("stat.herblore", 1)
                    xpTenths(0)
                    output("obj.sote_crystal_dust", 10)
                    category("Crushing")
                }
            }
        }
}
