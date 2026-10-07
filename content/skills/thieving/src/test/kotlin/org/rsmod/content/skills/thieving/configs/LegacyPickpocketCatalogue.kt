package org.rsmod.content.skills.thieving.configs

import dtx.rs.RSTable
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.content.skills.thieving.drops.PickpocketDropTables
import org.rsmod.game.entity.Player

// Targets already use their single contentGroup for other behavior; pickpocketing binds by NPC ID.
internal data class LegacyPickpocketNpc(
    val npcs: List<String>,
    val displayName: String,
    val levelReq: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val damage: Int,
    val failBark: String = "What do you think you're doing?",
    val pouch: String? = null,
    val loot: (Player) -> RSTable<Player, DropRollItem>? = { null },
    val inArdougne: Boolean = false,
    val prifddinas: Boolean = false,
    val pickpocketOp: Int = 3,
    val blackjack: Boolean = false,
    val stunMovement: Int = 9,
    val stunAction: Int = 8,
    val actionDelay: Int = 2,
    // Direct coin rewards bypass pouches; leave pouch unset for these targets.
    val directCoins: Int? = null,
    /** False for targets that never award coin pouches, even if other pouches are carried. */
    val usesCoinPouches: Boolean = true,
    val koXp: Double = 0.0,
    val alwaysDamagesWithoutIceGloves: Boolean = false,
)

internal object LegacyPickpocketCatalogue {
    val manWoman =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.varrock_man1",
                    "npc.man",
                    "npc.man2",
                    "npc.man3",
                    "npc.man4",
                    "npc.man4_for_musa_point",
                    "npc.al_kharid_man",
                    "npc.falador_man2",
                    "npc.falador_man3",
                    "npc.ardougnian_male1",
                    "npc.karamja_man",
                    "npc.death_man_indoors1",
                    "npc.man_indoor",
                    "npc.zeah_man",
                    "npc.zeah_man2",
                    "npc.zeah_man3",
                    "npc.shayzien_man_1",
                    "npc.shayzien_man_2",
                    "npc.man5",
                    "npc.varrock_woman1",
                    "npc.woman",
                    "npc.woman2",
                    "npc.woman3",
                    "npc.falador_woman2",
                    "npc.ardougnian_female1",
                    "npc.zeah_woman",
                    "npc.zeah_woman2",
                    "npc.zeah_woman3",
                    "npc.zeah_woman_outside",
                    "npc.shayzien_woman_1",
                    "npc.shayzien_woman_2",
                    "npc.woman4",
                ),
            displayName = "man",
            levelReq = 1,
            xp = 8.0,
            low = 180,
            high = 240,
            damage = 1,
            pouch = "obj.pickpocket_coin_pouch_citizen",
        )

    val villager =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.feud_villager_multi_1",
                    "npc.feud_villager_multi_2",
                    "npc.feud_villager_multi_3",
                    "npc.feud_villager_1_1",
                    "npc.feud_villager_1_2",
                    "npc.feud_villager_1_3",
                    "npc.feud_villager_2_1",
                    "npc.feud_villager_2_2",
                    "npc.feud_villager_2_3",
                    "npc.feud_villager_3_1",
                    "npc.feud_villager_3_2",
                    "npc.feud_villager_3_3",
                ),
            displayName = "villager",
            failBark = "What do you think you're doing?",
            levelReq = 30,
            xp = 0.0,
            low = 180,
            high = 240,
            damage = 2,
            directCoins = 5,
            usesCoinPouches = false,
            koXp = 8.0,
            blackjack = true,
        )

    val farmer =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.farmer1",
                    "npc.farmer2",
                    "npc.farmer3",
                    "npc.farmer1_f",
                    "npc.farmer2_f",
                    "npc.farmer3_f",
                    "npc.farmer4",
                    "npc.varlamore_farmer_m_1",
                    "npc.varlamore_farmer_m_2",
                    "npc.varlamore_farmer_m_3",
                    "npc.varlamore_farmer_m_4",
                    "npc.varlamore_farmer_f_1",
                    "npc.varlamore_farmer_f_2",
                    "npc.varlamore_farmer_f_3",
                    "npc.varlamore_farmer_f_4",
                    "npc.kastori_farmer_m_1",
                    "npc.kastori_farmer_m_2",
                    "npc.kastori_farmer_f_1",
                    "npc.kastori_farmer_f_2",
                    "npc.tal_teklan_farmer",
                ),
            displayName = "farmer",
            failBark = "Cor blimey mate, what are you doing in my pockets?",
            levelReq = 10,
            xp = 14.5,
            low = 150,
            high = 240,
            damage = 1,
            loot = { PickpocketDropTables.farmer },
        )

    val hamMember =
        LegacyPickpocketNpc(
            npcs = listOf("npc.favour_male_ham_civilian", "npc.favour_female_ham_civilian"),
            displayName = "H.A.M. member",
            failBark = "What do you think you're doing?",
            levelReq = 15,
            xp = 22.2,
            low = 135,
            high = 239,
            damage = 3,
            loot = { PickpocketDropTables.hamMember },
            pickpocketOp = 1,
        )

    val warrior =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.warrior_woman",
                    "npc.warrior_woman_variant01",
                    "npc.warrior_woman_variant02",
                    "npc.warrior_man",
                    "npc.warrior_man_variant01",
                    "npc.warrior_man_variant02",
                    "npc.al_kharid_warrior",
                ),
            displayName = "warrior",
            levelReq = 25,
            xp = 26.0,
            low = 100,
            high = 240,
            damage = 2,
            pouch = "obj.pickpocket_coin_pouch_warrior",
        )

    val rogue =
        LegacyPickpocketNpc(
            npcs = listOf("npc.rogue"),
            displayName = "rogue",
            levelReq = 32,
            xp = 36.5,
            low = 75,
            high = 240,
            damage = 2,
            loot = { PickpocketDropTables.rogue },
        )

    // TODO: Verify the cave-goblin success curve; it is interpolated from neighboring tiers.
    val caveGoblin =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.dorgesh_male_1",
                    "npc.dorgesh_male_2",
                    "npc.dorgesh_male_3",
                    "npc.dorgesh_male_4",
                    "npc.dorgesh_male_5",
                    "npc.dorgesh_male_6",
                    "npc.dorgesh_male_7",
                    "npc.dorgesh_male_8",
                    "npc.dorgesh_male_9",
                    "npc.dorgesh_female_1",
                    "npc.dorgesh_female_2",
                    "npc.dorgesh_female_3",
                    "npc.dorgesh_female_4",
                    "npc.dorgesh_female_5",
                    "npc.dorgesh_female_6",
                    "npc.dorgesh_female_7",
                    "npc.dorgesh_female_8",
                    "npc.dorgesh_female_9",
                ),
            displayName = "cave goblin",
            failBark = "What do you think you're doing?",
            levelReq = 36,
            xp = 40.0,
            low = 85,
            high = 240,
            damage = 1,
            loot = { PickpocketDropTables.caveGoblin },
        )

    val masterFarmer =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.master_farmer_1",
                    "npc.master_farmer_2",
                    "npc.master_farmer_1_f",
                    "npc.master_farmer_2_f",
                    "npc.varlamore_master_farmer_m_1",
                    "npc.varlamore_master_farmer_m_2",
                    "npc.varlamore_master_farmer_m_3",
                    "npc.varlamore_master_farmer_m_4",
                    "npc.varlamore_master_farmer_f_1",
                    "npc.varlamore_master_farmer_f_2",
                    "npc.varlamore_master_farmer_f_3",
                    "npc.varlamore_master_farmer_f_4",
                    "npc.kastori_master_farmer_m_1",
                    "npc.kastori_master_farmer_m_2",
                    "npc.kastori_master_farmer_f_1",
                    "npc.kastori_master_farmer_f_2",
                    "npc.martin_the_master_farmer",
                ),
            displayName = "Master Farmer",
            usesCoinPouches = false,
            failBark = "Cor blimey mate, what are you doing in my pockets?",
            levelReq = 38,
            xp = 43.0,
            low = 90,
            high = 240,
            damage = 3,
            loot = { player ->
                if (player.farmingLvl >= HIGH_FARMING_LEVEL) {
                    PickpocketDropTables.masterFarmerHighFarming
                } else {
                    PickpocketDropTables.masterFarmer
                }
            },
        )

    val pirate =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.pirate_pickpocketable_1",
                    "npc.pirate_pickpocketable_2",
                    "npc.pirate_pickpocketable_3",
                    "npc.pirate_pickpocketable_4",
                    "npc.pirate_pickpocketable_5",
                ),
            displayName = "pirate",
            failBark = "What do you think you're doing?",
            levelReq = 60,
            xp = 72.0,
            low = 60,
            high = 240,
            damage = 3,
            pouch = "obj.pickpocket_coin_pouch_pirate",
        )

    val guard =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.hos_town_guard_01",
                    "npc.hos_town_guard_02",
                    "npc.hos_town_guard_03",
                    "npc.hos_town_guard_04",
                    "npc.jail_guard_1",
                    "npc.jail_guard_2",
                    "npc.jail_guard_3",
                    "npc.jail_guard_4",
                    "npc.jail_guard_5",
                    "npc.fai_varrock_guard",
                    "npc.fai_varrock_guard_captain",
                    "npc.guard1",
                    "npc.fai_falador_guard1",
                    "npc.fai_falador_guard2",
                    "npc.fai_falador_guard3",
                    "npc.fai_falador_guard4",
                    "npc.fai_falador_guard5",
                    "npc.fai_falador_guard6",
                    "npc.falador_doric_area_guard",
                    "npc.ardougne_guard",
                    "npc.kourend_guard_m1",
                    "npc.kourend_guard_m2",
                    "npc.kourend_guard_m3",
                    "npc.kourend_guard_m4",
                    "npc.kourend_guard_f1",
                    "npc.kourend_guard_f2",
                    "npc.kourend_guard_f3",
                    "npc.kourend_guard_f4",
                    "npc.fai_varrock_guard02",
                    "npc.fai_varrock_guard02_variant01",
                    "npc.fai_varrock_guard02_variant02",
                    "npc.fai_varrock_guard02_f",
                    "npc.fai_varrock_guard02_f_variant01",
                    "npc.fai_varrock_guard02_f_variant02",
                    "npc.fai_varrock_guard_captain02",
                    "npc.guard1_variant01",
                    "npc.guard1_f",
                    "npc.guard1_f_variant01",
                    "npc.ardougne_guard_variant01",
                    "npc.ardougne_guard_f",
                    "npc.ardougne_guard_f_variant01",
                    "npc.fai_falador_guard1_variant01",
                    "npc.fai_falador_guard1_f",
                    "npc.fai_falador_guard1_variant02",
                    "npc.fai_falador_guard2_f",
                    "npc.fai_falador_guard3_f",
                    "npc.fai_falador_guard4_f",
                    "npc.varlamore_guard_m_1",
                    "npc.varlamore_guard_m_2",
                    "npc.varlamore_guard_m_3",
                    "npc.varlamore_guard_m_4",
                    "npc.varlamore_guard_m_5",
                    "npc.varlamore_guard_f_1",
                    "npc.varlamore_guard_f_2",
                    "npc.varlamore_guard_f_3",
                    "npc.varlamore_guard_f_4",
                    "npc.varlamore_guard_f_5",
                    "npc.aldarin_guard_m_1",
                    "npc.aldarin_guard_m_2",
                    "npc.aldarin_guard_m_3",
                    "npc.aldarin_guard_m_4",
                    "npc.aldarin_guard_m_5",
                    "npc.aldarin_guard_f_1",
                    "npc.aldarin_guard_f_2",
                    "npc.aldarin_guard_f_3",
                    "npc.aldarin_guard_f_4",
                    "npc.aldarin_guard_f_5",
                    "npc.auburnvale_guard_m_1",
                    "npc.auburnvale_guard_m_2",
                    "npc.auburnvale_guard_m_3",
                    "npc.auburnvale_guard_m_4",
                    "npc.auburnvale_guard_f_1",
                    "npc.auburnvale_guard_f_2",
                    "npc.auburnvale_guard_f_3",
                    "npc.auburnvale_guard_f_4",
                    "npc.tlati_guard_m_1",
                    "npc.tlati_guard_m_2",
                    "npc.tlati_guard_m_3",
                    "npc.tlati_guard_m_4",
                    "npc.tlati_guard_f_1",
                    "npc.tlati_guard_f_2",
                    "npc.tlati_guard_f_3",
                    "npc.tlati_guard_f_4",
                    "npc.port_roberts_guard_m1",
                    "npc.port_roberts_guard_m2",
                    "npc.port_roberts_guard_f1",
                    "npc.port_roberts_guard_f2",
                ),
            displayName = "guard",
            levelReq = 40,
            xp = 46.8,
            low = 50,
            high = 240,
            damage = 2,
            pouch = "obj.pickpocket_coin_pouch_guard",
            inArdougne = true,
        )

    // TODO: Verify Pollnivnian bandit success curves.
    val beardedBandit =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.feud_arabian_guard_multi",
                    "npc.feud_arabian_guard1_1",
                    "npc.feud_arabian_guard1_2",
                ),
            displayName = "bearded Pollnivnian bandit",
            failBark = "What do you think you're doing?",
            levelReq = 45,
            xp = 65.0,
            low = 60,
            high = 240,
            damage = 5,
            pouch = "obj.pickpocket_coin_pouch_bandit2",
            blackjack = true,
        )

    val wealthyCitizen =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.varlamore_wealthy_citizen_a",
                    "npc.varlamore_wealthy_citizen_b",
                    "npc.varlamore_wealthy_citizen_c",
                    "npc.varlamore_wealthy_citizen_d",
                ),
            displayName = "wealthy citizen",
            levelReq = 50,
            xp = 96.0,
            low = 35,
            high = 200,
            damage = 3,
            loot = { PickpocketDropTables.wealthyCitizen },
            pickpocketOp = 1,
        )

    val desertBandit =
        LegacyPickpocketNpc(
            npcs = listOf("npc.fourdiamonds_sword_bandit_1", "npc.fourdiamonds_sword_bandit_free"),
            displayName = "desert bandit",
            levelReq = 53,
            xp = 79.4,
            low = 50,
            high = 240,
            damage = 3,
            loot = { PickpocketDropTables.desertBandit },
        )

    val knightOfArdougne =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.knight_of_ardougne_west",
                    "npc.knight_of_ardougne_f_west",
                    "npc.knight_of_ardougne",
                    "npc.knight_of_ardougne2",
                    "npc.knight_of_ardougne_west_vis",
                    "npc.knight_of_ardougne_f_west_vis",
                    "npc.knight_of_ardougne_f",
                ),
            displayName = "Knight of Ardougne",
            levelReq = 55,
            xp = 84.3,
            low = 50,
            high = 240,
            damage = 3,
            pouch = "obj.pickpocket_coin_pouch_knight",
            inArdougne = true,
        )

    // TODO: Verify Varlamore knight rates; these mirror Ardougne knights without the diary boost.
    val knightOfVarlamore =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.varlamore_knight_m_1",
                    "npc.varlamore_knight_m_2",
                    "npc.varlamore_knight_m_3",
                    "npc.varlamore_knight_f_1",
                    "npc.varlamore_knight_f_2",
                    "npc.varlamore_knight_f_3",
                ),
            displayName = "Knight of Varlamore",
            levelReq = 55,
            xp = 84.3,
            low = 50,
            high = 240,
            damage = 3,
            pouch = "obj.pickpocket_coin_pouch_knight",
        )

    val pollnivnianBandit =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.feud_arabian_guard2_multi",
                    "npc.feud_arabian_guard2_1",
                    "npc.feud_arabian_guard2_2",
                ),
            displayName = "Pollnivnian bandit",
            failBark = "What do you think you're doing?",
            levelReq = 55,
            xp = 84.3,
            low = 50,
            high = 240,
            damage = 5,
            pouch = "obj.pickpocket_coin_pouch_bandit",
            blackjack = true,
        )

    val yanilleWatchman =
        LegacyPickpocketNpc(
            npcs = listOf("npc.yanille_watchman"),
            displayName = "watchman",
            levelReq = 65,
            xp = 137.5,
            low = 15,
            high = 160,
            damage = 3,
            loot = { PickpocketDropTables.yanilleWatchman },
        )

    // Menaphite Pickpocket exists only on the blackjack-enabled transform.
    val menaphiteThug =
        LegacyPickpocketNpc(
            npcs = listOf("npc.feud_egyptian_doorman_multi", "npc.feud_egyptian_doorman_2"),
            displayName = "Menaphite thug",
            failBark = "What do you think you're doing?",
            levelReq = 65,
            xp = 137.5,
            low = 50,
            high = 160,
            damage = 5,
            pouch = "obj.pickpocket_coin_pouch_menaphite",
            blackjack = true,
        )

    val paladin =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.paladin_west",
                    "npc.paladin_west_f",
                    "npc.paladin",
                    "npc.paladin2",
                    "npc.paladin_west_vis",
                    "npc.paladin_west_f_vis",
                    "npc.paladin_variant01",
                    "npc.paladin_variant02",
                    "npc.paladin_f",
                    "npc.paladin_f_variant01",
                ),
            displayName = "paladin",
            levelReq = 70,
            xp = 131.8,
            low = 40,
            high = 170,
            damage = 3,
            loot = { PickpocketDropTables.paladin },
            inArdougne = true,
        )

    val gnome =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.grim_gnome_incage_1",
                    "npc.gnome",
                    "npc.browclothedgnome",
                    "npc.darkskinned_gnome",
                    "npc.gnomechildgreen",
                    "npc.gnomechildblue",
                    "npc.gnomechild",
                    "npc.gnomefemale",
                    "npc.gnomefemale_dskinned",
                ),
            displayName = "gnome",
            failBark = "What do you think you're doing?",
            levelReq = 75,
            xp = 133.3,
            low = 43,
            high = 175,
            damage = 1,
            loot = { PickpocketDropTables.gnome },
        )

    val hero =
        LegacyPickpocketNpc(
            npcs = listOf("npc.hero", "npc.hero_variant01", "npc.hero_f"),
            displayName = "hero",
            levelReq = 80,
            xp = 163.3,
            low = 39,
            high = 160,
            damage = 3,
            loot = { PickpocketDropTables.hero },
            inArdougne = true,
        )

    val vyre =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.valentin_rasputin",
                    "npc.von_van_von",
                    "npc.vlad_bechstein",
                    "npc.draconis_sanguine",
                    "npc.mort_nightshade",
                    "npc.vampyrus_diaemus",
                    "npc.carnivus_belamorta",
                    "npc.vormar_vakan",
                    "npc.misdrievus_shadum",
                    "npc.vlad_diaemus",
                    "npc.noctillion_lugosi",
                    "npc.alek_constantine",
                    "npc.grigor_rasputin",
                    "npc.haemas_lamescus",
                    "npc.remus_kaninus",
                    "npc.vallessia_dracyula",
                    "npc.violetta_sanguine",
                    "npc.diphylla_bechstein",
                    "npc.episcula_helsing",
                    "npc.vampyressa_van_von",
                    "npc.vallessia_von_pitt",
                    "npc.vonnetta_varnis",
                    "npc.natalidae_shadum",
                    "npc.mortina_daubenton",
                    "npc.lasenna_rasputin",
                    "npc.caninelle_draynar",
                    "npc.valentina_diaemus",
                    "npc.nakasa_jovkai",
                    "npc.crimsonette_van_marr",
                    "npc.pipistrelle_draynar",
                ),
            displayName = "vyre",
            failBark = "What do you think you're doing?",
            levelReq = 82,
            xp = 306.9,
            low = 8,
            high = 128,
            damage = 5,
            loot = { PickpocketDropTables.vyre },
        )

    val elf =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.mourning_town_elf_1",
                    "npc.mourning_town_elf_3",
                    "npc.mourning_town_elf_4",
                    "npc.mourning_town_elf_5",
                    "npc.mourning_town_elf_5_vis",
                ),
            displayName = "elf",
            failBark = "What do you think you're doing?",
            levelReq = 85,
            xp = 353.3,
            low = 6,
            high = 100,
            damage = 5,
            loot = { PickpocketDropTables.elf },
        )

    val prifddinasElf = elf.copy(npcs = PRIFDDINAS_CITIZENS, prifddinas = true)

    val tzhaarHur =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.tzhaar_hur_city1",
                    "npc.tzhaar_hur_city2",
                    "npc.tzhaar_hur_city3",
                    "npc.tzhaar_hur_city4",
                    "npc.tzhaar_hur_city5",
                    "npc.tzhaar_hur_city6",
                ),
            displayName = "TzHaar-Hur",
            failBark = "What do you think you're doing?",
            levelReq = 90,
            xp = 103.4,
            low = -200,
            high = 200,
            damage = 4,
            loot = { PickpocketDropTables.tzhaarHur },
            usesCoinPouches = false,
            alwaysDamagesWithoutIceGloves = true,
        )

    // TODO: Verify Fremennik pickpocket success rates.
    val fremennikCitizen =
        LegacyPickpocketNpc(
            npcs =
                listOf(
                    "npc.viking_man",
                    "npc.viking_man2",
                    "npc.viking_man3",
                    "npc.viking_man4",
                    "npc.viking_man5",
                    "npc.viking_woman2",
                    "npc.viking_woman3",
                    "npc.viking_woman4",
                    "npc.viking_woman_indoors",
                ),
            displayName = "Fremennik citizen",
            failBark = "What do you think you're doing?",
            levelReq = 45,
            xp = 65.0,
            low = 60,
            high = 240,
            damage = 2,
            pouch = "obj.pickpocket_coin_pouch_fremennik",
        )

    // TODO: Verify Digsite workman success endpoints; the four-tick action delay is separate.
    val digsiteWorkman =
        LegacyPickpocketNpc(
            npcs = listOf("npc.digworkman1", "npc.digworkman2"),
            displayName = "digsite workman",
            usesCoinPouches = false,
            loot = { player -> PickpocketDropTables.digsiteWorkman(player.vars["varp.itexamlevel"]) },
            failBark = "What do you think you're doing?",
            levelReq = 25,
            xp = 10.4,
            low = 100,
            high = 240,
            damage = 1,
            actionDelay = 4,
        )

    val all: List<LegacyPickpocketNpc> =
        listOf(
            manWoman.copy(npcs = manWoman.npcs + LegacyPr216PickpocketTargets.manWoman),
            villager,
            farmer,
            hamMember,
            warrior,
            rogue,
            caveGoblin,
            masterFarmer,
            pirate,
            guard.copy(npcs = guard.npcs + LegacyPr216PickpocketTargets.guards),
            guard.copy(npcs = LegacyPr216PickpocketTargets.hamGuards, pickpocketOp = 1),
            beardedBandit,
            wealthyCitizen,
            desertBandit,
            knightOfArdougne,
            knightOfVarlamore,
            pollnivnianBandit,
            yanilleWatchman,
            menaphiteThug,
            paladin,
            gnome,
            hero,
            vyre,
            elf,
            prifddinasElf,
            tzhaarHur,
            fremennikCitizen,
            digsiteWorkman,
        )

    val blackjackTargets: List<LegacyPickpocketNpc> = all.filter(LegacyPickpocketNpc::blackjack)

    val blackjacks: List<String> =
        listOf(
            "obj.blackjack_oak",
            "obj.roguetrader_bj_assault_oak",
            "obj.roguetrader_bj_defend_oak",
            "obj.blackjack_willow",
            "obj.roguetrader_bj_assault_willow",
            "obj.roguetrader_bj_defend_willow",
            "obj.roguetrader_bj_maple",
            "obj.roguetrader_bj_assault_maple",
            "obj.roguetrader_bj_defend_maple",
        )

    val rogueEquipment: List<String> =
        listOf(
            "obj.roguesden_helm",
            "obj.roguesden_body",
            "obj.roguesden_legs",
            "obj.roguesden_gloves",
            "obj.roguesden_boots",
        )

    val thievingCapes: List<String> =
        listOf("obj.skillcape_thieving", "obj.skillcape_thieving_trimmed", "obj.skillcape_max")

    const val GLOVES_OF_SILENCE: String = "obj.hunting_silent_gloves"
    const val DODGY_NECKLACE: String = "obj.dodgy_necklace"
    const val SHADOW_VEIL_VARBIT: String = "varbit.arceuus_shadow_veil_active"

    const val ICE_GLOVES: String = "obj.ice_gloves"

    private const val HIGH_FARMING_LEVEL = 85
}

private val PRIFDDINAS_CITIZENS: List<String> =
    listOf(
        "npc.prif_citizen_anaire",
        "npc.prif_citizen_aranwe",
        "npc.prif_citizen_aredhel",
        "npc.prif_citizen_caranthir",
        "npc.prif_citizen_celebrian",
        "npc.prif_citizen_celegorm",
        "npc.prif_citizen_cirdan",
        "npc.prif_citizen_curufin",
        "npc.prif_citizen_earwen",
        "npc.prif_citizen_edrahil",
        "npc.prif_citizen_elenwe",
        "npc.prif_citizen_elladan",
        "npc.prif_citizen_enel",
        "npc.prif_citizen_enelye",
        "npc.prif_citizen_enerdhil",
        "npc.prif_citizen_erestor",
        "npc.prif_citizen_feanor",
        "npc.prif_citizen_findis",
        "npc.prif_citizen_finduilas",
        "npc.prif_citizen_fingolfin",
        "npc.prif_citizen_fingon",
        "npc.prif_citizen_galathil",
        "npc.prif_citizen_gelmir",
        "npc.prif_citizen_glorfindel",
        "npc.prif_citizen_guilin",
        "npc.prif_citizen_hendor",
        "npc.prif_citizen_idril",
        "npc.prif_citizen_imin",
        "npc.prif_citizen_iminye",
        "npc.prif_citizen_indis",
        "npc.prif_citizen_ingwe",
        "npc.prif_citizen_ingwion",
        "npc.prif_citizen_lenwe",
        "npc.prif_citizen_lindir",
        "npc.prif_citizen_maeglin",
        "npc.prif_citizen_mahtan",
        "npc.prif_citizen_miriel",
        "npc.prif_citizen_mithrellas",
        "npc.prif_citizen_nellas",
        "npc.prif_citizen_nerdanel",
        "npc.prif_citizen_nimloth",
        "npc.prif_citizen_oropher",
        "npc.prif_citizen_orophin",
        "npc.prif_citizen_saeros",
        "npc.prif_citizen_salgant",
        "npc.prif_citizen_tatie",
        "npc.prif_citizen_thingol",
        "npc.prif_citizen_turgon",
        "npc.prif_citizen_vaire",
    )

internal object LegacyPr216PickpocketTargets {
    val manWoman: List<String> =
        listOf(
            "npc.falador_man1",
            "npc.falador_doric_area_man1",
            "npc.falador_doric_area_man2",
            "npc.falador_doric_area_man3",
            "npc.falador_doric_area_man4",
            "npc.rimmington_hengel",
            "npc.rimmington_anja",
            "npc.varlamore_citizen_poor_m_1",
            "npc.varlamore_citizen_poor_m_2",
            "npc.varlamore_citizen_poor_m_3",
            "npc.varlamore_citizen_poor_m_4",
            "npc.varlamore_citizen_poor_m_5",
            "npc.varlamore_citizen_poor_f_1",
            "npc.varlamore_citizen_poor_f_2",
            "npc.varlamore_citizen_poor_f_3",
            "npc.varlamore_citizen_poor_f_4",
            "npc.varlamore_citizen_poor_f_5",
            "npc.varlamore_citizen_normal_m_1",
            "npc.varlamore_citizen_normal_m_2",
            "npc.varlamore_citizen_normal_m_3",
            "npc.varlamore_citizen_normal_m_4",
            "npc.varlamore_citizen_normal_m_5",
            "npc.varlamore_citizen_normal_f_1",
            "npc.varlamore_citizen_normal_f_2",
            "npc.varlamore_citizen_normal_f_3",
            "npc.varlamore_citizen_normal_f_4",
            "npc.varlamore_citizen_normal_f_5",
            "npc.varlamore_citizen_rich_m_1",
            "npc.varlamore_citizen_rich_m_2",
            "npc.varlamore_citizen_rich_m_3",
            "npc.varlamore_citizen_rich_m_4",
            "npc.varlamore_citizen_rich_m_5",
            "npc.varlamore_citizen_rich_f_1",
            "npc.varlamore_citizen_rich_f_2",
            "npc.varlamore_citizen_rich_f_3",
            "npc.varlamore_citizen_rich_f_4",
            "npc.varlamore_citizen_rich_f_5",
            "npc.aldarin_citizen_poor_m_1",
            "npc.aldarin_citizen_poor_m_2",
            "npc.aldarin_citizen_poor_m_3",
            "npc.aldarin_citizen_poor_f_1",
            "npc.aldarin_citizen_poor_f_2",
            "npc.aldarin_citizen_poor_f_3",
            "npc.aldarin_citizen_normal_m_1",
            "npc.aldarin_citizen_normal_m_2",
            "npc.aldarin_citizen_normal_m_3",
            "npc.aldarin_citizen_normal_m_4",
            "npc.aldarin_citizen_normal_f_1",
            "npc.aldarin_citizen_normal_f_2",
            "npc.aldarin_citizen_normal_f_3",
            "npc.aldarin_citizen_normal_f_4",
            "npc.aldarin_citizen_normal_f_5",
            "npc.aldarin_citizen_rich_m_1",
            "npc.aldarin_citizen_rich_m_2",
            "npc.aldarin_citizen_rich_f_1",
            "npc.aldarin_citizen_rich_f_2",
            "npc.auburn_citizen_normal_m_1",
            "npc.auburn_citizen_normal_m_2",
            "npc.auburn_citizen_normal_m_3",
            "npc.auburn_citizen_normal_f_1",
            "npc.auburn_citizen_normal_f_2",
            "npc.auburn_citizen_normal_f_3",
            "npc.auburn_citizen_rich_m_1",
            "npc.auburn_citizen_rich_f_1",
            "npc.kastori_citizen_normal_m_1",
            "npc.kastori_citizen_normal_m_2",
            "npc.kastori_citizen_normal_m_3",
            "npc.kastori_citizen_normal_f_1",
            "npc.kastori_citizen_normal_f_2",
            "npc.kastori_citizen_normal_f_3",
            "npc.kastori_citizen_rich_m_1",
            "npc.kastori_citizen_rich_f_1",
            "npc.tal_teklan_citizen_normal_m_1",
            "npc.tal_teklan_citizen_normal_m_2",
            "npc.tal_teklan_citizen_normal_m_3",
            "npc.tal_teklan_citizen_normal_f_1",
            "npc.tal_teklan_citizen_normal_f_2",
            "npc.tal_teklan_citizen_normal_f_3",
            "npc.tal_teklan_citizen_rich_m_1",
            "npc.tal_teklan_citizen_rich_f_1",
        )

    val guards: List<String> =
        listOf(
            "npc.kourend_guard_m1_big",
            "npc.kourend_guard_m2_big",
            "npc.kourend_guard_m3_big",
            "npc.kourend_guard_m4_big",
            "npc.kourend_guard_f1_big",
            "npc.kourend_guard_f2_big",
            "npc.kourend_guard_f3_big",
            "npc.kourend_guard_f4_big",
        )

    val hamGuards: List<String> =
        listOf(
            "npc.dttd_ham_guard_1_postquest",
            "npc.dttd_ham_guard_2_postquest",
            "npc.dttd_ham_guard_3_postquest",
            "npc.dttd_ham_guard_4_postquest",
            "npc.dttd_ham_guard_5_postquest",
        )
}
