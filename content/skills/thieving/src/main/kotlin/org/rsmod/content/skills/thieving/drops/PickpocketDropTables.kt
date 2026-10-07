package org.rsmod.content.skills.thieving.drops

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.rs.RSDropTable
import dtx.rs.RSTable
import dtx.rs.RSWeightedTable
import dtx.table.TableHooks
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.skills.thieving.configs.CoinPouches
import org.rsmod.game.entity.Player

// Do not RegisterDropTable: these NPC IDs already own combat loot tables.
object PickpocketDropTables {

    private val digsiteBeforeCompletion = digsiteWorkmanTable(completed = false)
    private val digsiteAfterCompletion = digsiteWorkmanTable(completed = true)

    fun digsiteWorkman(questProgress: Int): RSWeightedTable<Player, DropRollItem> =
        if (questProgress >= 9) digsiteAfterCompletion else digsiteBeforeCompletion

    private fun digsiteWorkmanTable(completed: Boolean): RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 11) {
            name("Digsite workman")
            (if (completed) 4 else 1) weight "obj.coins" count 10
            3 weight "obj.specimen_brush" count 1
            1 weight "obj.rope" count 1
            1 weight "obj.bucket_empty" count 1
            1 weight "obj.leather_gloves" count 1
            1 weight "obj.spade" count 1
            if (!completed) 3 weight "obj.rock_sample1" count 1
        }

    val farmer: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 128) {
            name("Farmer")
            123 weight CoinPouches.FARMER count 1
            5 weight "obj.potato_seed" count 1
        }

    val rogue: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 144) {
            name("Rogue")
            123 weight CoinPouches.ROGUE count 1
            9 weight "obj.airrune" count 8
            6 weight "obj.jug_wine" count 1
            5 weight "obj.lockpick" count 1
            1 weight "obj.iron_dagger_p" count 1
        }

    // TODO: Replace coin-pouch placeholders for cave-goblin wire, torch and food drops after resolving their item IDs.
    val caveGoblin: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 20) {
            name("Cave goblin")
            13 weight CoinPouches.CAVE_GOBLIN count 1
            1 weight "obj.bullseye_lantern_empty" count 1
            1 weight "obj.oil_lamp_empty" count 1
            2 weight "obj.iron_ore" count 1..4
            1 weight "obj.swamp_tar" count 1
            1 weight "obj.tinderbox" count 1
            1 weight "obj.coal" count 1
        }

    val wealthyCitizen: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 85) {
            name("Wealthy citizen")
            79 weight CoinPouches.WEALTHY count 1
            5 weight "obj.varlamore_thieving_house_key" count 1
            1 weight "obj.trail_clue_easy_simple001" count 1
        }

    val desertBandit: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 7) {
            name("Desert bandit")
            5 weight CoinPouches.DESERT_BANDIT count 1
            1 weight "obj.1doseantipoison" count 1
            1 weight "obj.lockpick" count 1
        }

    val gnome: RSTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Gnome",
            preRoll =
                rsPlayerTertiaryTable {
                    1 outOf 150 weight "obj.trail_clue_medium_anagram001" count 1
                },
            mainTable =
                rsPlayerWeightedTable(total = 128) {
                    name("Gnome")
                    56 weight "obj.arrow_shaft" count 2..4
                    30 weight CoinPouches.GNOME count 1
                    24 weight "obj.swamp_toad" count 1
                    8 weight "obj.gold_ore" count 1
                    5 weight "obj.earthrune" count 1
                    3 weight "obj.king_worm" count 1
                    2 weight "obj.fire_orb" count 1
                },
        )

    val hero: RSTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Hero",
            preRoll =
                rsPlayerTertiaryTable {
                    1 outOf 1400 weight "obj.trail_clue_elite_music001" count 1
                },
            mainTable =
                rsPlayerWeightedTable(total = 128) {
                    name("Hero")
                    105 weight CoinPouches.HERO count 1
                    8 weight "obj.deathrune" count 2
                    6 weight "obj.jug_wine" count 1
                    5 weight "obj.bloodrune" count 1
                    2 weight "obj.fire_orb" count 1
                    1 weight "obj.uncut_diamond" count 1
                    1 weight "obj.gold_ore" count 1
                },
        )

    val vyre: RSTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Vyre",
            preRoll = rsPlayerTertiaryTable { 1 outOf 5000 weight "obj.blood_shard" count 1 },
            mainTable =
                rsPlayerWeightedTable(total = 132) {
                    name("Vyre")
                    109 weight CoinPouches.VYRE count 1
                    8 weight "obj.deathrune" count 2
                    6 weight "obj.blood_pint" count 1
                    5 weight "obj.uncut_ruby" count 1
                    2 weight "obj.bloodrune" count 4
                    1 weight "obj.uncut_diamond" count 1
                    1 weight "obj.cooked_mystery_meat" count 1
                },
        )

    val elf: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 128) {
            name("Elf")
            105 weight CoinPouches.ELF count 1
            8 weight "obj.deathrune" count 2
            6 weight "obj.jug_wine" count 1
            5 weight "obj.naturerune" count 3
            2 weight "obj.fire_orb" count 1
            1 weight "obj.uncut_diamond" count 1
            1 weight "obj.gold_ore" count 1
        }

    val tzhaarHur: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 195) {
            name("TzHaar-Hur")
            182 weight "obj.tzhaar_token" count 3..7
            5 weight "obj.uncut_sapphire" count 1
            4 weight "obj.uncut_emerald" count 1
            3 weight "obj.uncut_ruby" count 1
            1 weight "obj.uncut_diamond" count 1
        }

    val yanilleWatchman: RSTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Yanille watchman",
            guaranteed =
                rsPlayerGuaranteedTable {
                    CoinPouches.WATCHMAN count 1
                    "obj.bread" count 1
                },
        )

    val paladin: RSTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Paladin",
            guaranteed =
                rsPlayerGuaranteedTable {
                    CoinPouches.PALADIN count 1
                    "obj.chaosrune" count 2
                },
            preRoll =
                rsPlayerTertiaryTable { 1 outOf 500 weight "obj.trail_clue_hard_map001" count 1 },
        )

    private val hamHerbs = rsPlayerWeightedTable(total = 11) {
        name("H.A.M. herbs")
        6 weight "obj.unidentified_guam" count 1
        3 weight "obj.unidentified_marentill" count 1
        2 weight "obj.unidentified_tarromin" count 1
    }

    internal val hamNormalLoot = rsPlayerWeightedTable(total = 98) {
        name("H.A.M. member normal loot")
        3 weight "obj.bronze_arrow" count 1..13
        3 weight "obj.bronze_axe" count 1
        3 weight "obj.bronze_dagger" count 1
        3 weight "obj.bronze_pickaxe" count 1
        3 weight "obj.iron_axe" count 1
        3 weight "obj.iron_dagger" count 1
        3 weight "obj.iron_pickaxe" count 1
        3 weight "obj.leather_armour" count 1
        1 weight "obj.ham_boots" count 1
        1 weight "obj.ham_cloak" count 1
        1 weight "obj.ham_gloves" count 1
        1 weight "obj.ham_hood" count 1
        1 weight "obj.ham_badge" count 1
        1 weight "obj.ham_robe" count 1
        1 weight "obj.ham_shirt" count 1
        2 weight "obj.steel_arrow" count 1..13
        2 weight "obj.steel_axe" count 1
        2 weight "obj.steel_dagger" count 1
        2 weight "obj.steel_pickaxe" count 1
        4 weight "obj.digsitebuttons" count 1
        15 weight CoinPouches.HAM count 1
        3 weight "obj.feather" count 1..7
        2 weight "obj.knife" count 1
        3 weight "obj.logs" count 1
        2 weight "obj.needle" count 1
        2 weight "obj.raw_anchovies" count 1
        2 weight "obj.raw_chicken" count 1
        3 weight "obj.thread" count 1..10
        2 weight "obj.tinderbox" count 1
        2 weight "obj.uncut_opal" count 1
        2 weight "obj.coal" count 1
        3 weight "obj.cow_hide" count 1
        4 weight "obj.digsitearmour1" count 1
        2 weight hamHerbs
        2 weight "obj.iron_ore" count 1
        4 weight "obj.digsitesword" count 1
        2 weight "obj.uncut_jade" count 1
    }

    private val hamClueLoot = rsPlayerTertiaryTable {
        1 outOf 50 weight "obj.trail_clue_easy_simple001" count 1
    }

    // Clues replace normal loot; preRoll plus mainTable would grant both.
    val hamMember: RSTable<Player, DropRollItem> = object : RSTable<Player, DropRollItem>,
        TableHooks<Player, DropRollItem> by TableHooks.Default() {
        override val tableIdentifier = "H.A.M. member"
        override val tableEntries = listOf(hamClueLoot, hamNormalLoot)

        override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> =
            hamClueLoot.roll(target, otherArgs).flatten().let { clue ->
                if (clue is RollResult.Nothing) hamNormalLoot.roll(target, otherArgs) else clue
            }
    }

    private val allotmentSeeds: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 35) {
            name("Master Farmer (allotment)")
            8 weight "obj.potato_seed" count 1
            7 weight "obj.onion_seed" count 1
            6 weight "obj.cabbage_seed" count 1
            5 weight "obj.tomato_seed" count 1
            4 weight "obj.sweetcorn_seed" count 1
            3 weight "obj.strawberry_seed" count 1
            2 weight "obj.watermelon_seed" count 1
        }

    private val hopSeeds: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 35) {
            name("Master Farmer (hops)")
            8 weight "obj.barley_seed" count 1
            7 weight "obj.hammerstone_hop_seed" count 1
            6 weight "obj.asgarnian_hop_seed" count 1
            5 weight "obj.jute_seed" count 1
            4 weight "obj.yanillian_hop_seed" count 1
            3 weight "obj.krandorian_hop_seed" count 1
            2 weight "obj.wildblood_hop_seed" count 1
        }

    private val flowerSeeds: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 26) {
            name("Master Farmer (flowers)")
            8 weight "obj.marigold_seed" count 1
            6 weight "obj.rosemary_seed" count 1
            5 weight "obj.nasturtium_seed" count 1
            4 weight "obj.woad_seed" count 1
            3 weight "obj.limpwurt_seed" count 1
        }

    private val bushSeeds: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 24) {
            name("Master Farmer (bushes)")
            8 weight "obj.redberry_bush_seed" count 1
            6 weight "obj.cadavaberry_bush_seed" count 1
            4 weight "obj.dwellberry_bush_seed" count 1
            3 weight "obj.jangerberry_bush_seed" count 1
            2 weight "obj.whiteberry_bush_seed" count 1
            1 weight "obj.poisonivy_bush_seed" count 1
        }

    private val specialSeeds: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 2) {
            name("Master Farmer (special)")
            1 weight "obj.belladonna_seed" count 1
            1 weight "obj.cactus_seed" count 1
        }

    private fun herbSeeds(highFarming: Boolean): RSWeightedTable<Player, DropRollItem> {
        val ranarr = if (highFarming) 12 else 7
        val snapdragon = if (highFarming) 4 else 1
        val torstol = if (highFarming) 3 else 1
        val total = 75 + ranarr + snapdragon + torstol
        return rsPlayerWeightedTable(total = total) {
            name(if (highFarming) "Master Farmer (herbs, Farming 85+)" else "Master Farmer (herbs)")
            20 weight "obj.guam_seed" count 1
            15 weight "obj.marrentill_seed" count 1
            12 weight "obj.tarromin_seed" count 1
            9 weight "obj.harralander_seed" count 1
            5 weight "obj.toadflax_seed" count 1
            4 weight "obj.irit_seed" count 1
            3 weight "obj.avantoe_seed" count 1
            2 weight "obj.kwuarm_seed" count 1
            1 weight "obj.cadantine_seed" count 1
            1 weight "obj.lantadyme_seed" count 1
            1 weight "obj.dwarf_weed_seed" count 1
            2 weight "obj.belladonna_seed" count 1
            ranarr weight "obj.ranarr_seed" count 1
            snapdragon weight "obj.snapdragon_seed" count 1
            torstol weight "obj.torstol_seed" count 1
        }
    }

    private fun masterFarmerTable(highFarming: Boolean): RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 1000) {
            name("Master Farmer")
            485 weight allotmentSeeds
            243 weight hopSeeds
            122 weight flowerSeeds
            97 weight bushSeeds
            48 weight herbSeeds(highFarming)
            5 weight specialSeeds
        }

    val masterFarmer: RSWeightedTable<Player, DropRollItem> = masterFarmerTable(highFarming = false)

    val masterFarmerHighFarming: RSWeightedTable<Player, DropRollItem> =
        masterFarmerTable(highFarming = true)
}
