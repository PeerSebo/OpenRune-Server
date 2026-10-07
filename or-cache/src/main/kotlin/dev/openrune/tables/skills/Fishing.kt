package dev.openrune.tables.skills

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Success pairs use a 256 denominator; harpoon pairs already include tool boosts. Big-net rows
 * roll independently. Specialty rates, junk XP and catch wording remain unverified; see
 */
object Fishing {

    const val COL_STYLE = 0
    const val COL_FISH_ITEM = 1
    const val COL_LEVEL = 2

    /** XP is stored in tenths. */
    const val COL_XP_TENTHS = 3
    const val COL_SUCCESS_RATE_LOW = 4
    const val COL_SUCCESS_RATE_HIGH = 5

    const val COL_DRAGON_RATE_LOW = 6
    const val COL_DRAGON_RATE_HIGH = 7

    const val COL_CRYSTAL_RATE_LOW = 8
    const val COL_CRYSTAL_RATE_HIGH = 9

    /** Big-net rows roll independently; omitted roll counts default to one. */
    const val COL_ROLLS = 10
    const val COL_CATCH_MESSAGE = 11

    /** Add this bonus out of 256 before interpolation flooring; omitted rows default to zero. */
    const val COL_BONUS = 17

    const val COL_AGILITY_LEVEL = 12
    const val COL_STRENGTH_LEVEL = 13

    /** Barbarian catches grant this XP, in tenths, to both Agility and Strength. */
    const val COL_SECONDARY_XP_TENTHS = 14

    /** Uniform catch quantity bounds default to one; level-scaled catches are handled in content. */
    const val COL_COUNT_MIN = 15
    const val COL_COUNT_MAX = 16

    const val STYLE_SMALL_NET = 1
    const val STYLE_NET_SPOT_BAIT = 2
    const val STYLE_LURE = 3
    const val STYLE_ROD_SPOT_BAIT = 4
    const val STYLE_CAGE = 5
    const val STYLE_CAGE_SPOT_HARPOON = 6
    const val STYLE_BIG_NET = 7
    const val STYLE_BIG_NET_SPOT_HARPOON = 8
    const val STYLE_MONKFISH_NET = 9
    const val STYLE_ANGLER_BAIT = 10
    const val STYLE_BARBARIAN_ROD = 11
    const val STYLE_SLIMY_EEL = 12
    const val STYLE_CAVE_EEL = 13
    const val STYLE_LAVA_EEL = 14
    const val STYLE_INFERNAL_EEL = 15
    const val STYLE_DARK_CRAB = 16
    const val STYLE_KARAMBWANJI = 17
    const val STYLE_MINNOW = 18

    fun catches() =
        dbTable("dbtable.fishing_catches", serverOnly = true) {
            column("style", COL_STYLE, VarType.INT)
            column("fish_item", COL_FISH_ITEM, VarType.OBJ)
            column("level", COL_LEVEL, VarType.INT)
            column("xp_tenths", COL_XP_TENTHS, VarType.INT)
            column("success_rate_low", COL_SUCCESS_RATE_LOW, VarType.INT)
            column("success_rate_high", COL_SUCCESS_RATE_HIGH, VarType.INT)
            column("dragon_rate_low", COL_DRAGON_RATE_LOW, VarType.INT)
            column("dragon_rate_high", COL_DRAGON_RATE_HIGH, VarType.INT)
            column("crystal_rate_low", COL_CRYSTAL_RATE_LOW, VarType.INT)
            column("crystal_rate_high", COL_CRYSTAL_RATE_HIGH, VarType.INT)
            column("rolls", COL_ROLLS, VarType.INT)
            column("catch_message", COL_CATCH_MESSAGE, VarType.STRING)
            column("agility_level", COL_AGILITY_LEVEL, VarType.INT)
            column("strength_level", COL_STRENGTH_LEVEL, VarType.INT)
            column("secondary_xp_tenths", COL_SECONDARY_XP_TENTHS, VarType.INT)
            column("count_min", COL_COUNT_MIN, VarType.INT)
            column("count_max", COL_COUNT_MAX, VarType.INT)
            column("bonus", COL_BONUS, VarType.INT)

            fun catchRow(
                rowId: String,
                style: Int,
                fish: String,
                level: Int,
                xpTenths: Int,
                low: Int,
                high: Int,
                message: String,
            ) =
                row(rowId) {
                    column(COL_STYLE, style)
                    columnRSCM(COL_FISH_ITEM, fish)
                    column(COL_LEVEL, level)
                    column(COL_XP_TENTHS, xpTenths)
                    column(COL_SUCCESS_RATE_LOW, low)
                    column(COL_SUCCESS_RATE_HIGH, high)
                    column(COL_CATCH_MESSAGE, message)
                }

            catchRow(
                "dbrow.fishing_shrimps",
                STYLE_SMALL_NET,
                "obj.raw_shrimp", // singular in gamevals - NOT "raw_shrimps".
                1,
                100,
                48,
                256,
                "You catch some shrimps.",
            )
            catchRow(
                "dbrow.fishing_anchovies",
                STYLE_SMALL_NET,
                "obj.raw_anchovies",
                15,
                400,
                24,
                128,
                "You catch some anchovies.",
            )

            catchRow(
                "dbrow.fishing_sardine",
                STYLE_NET_SPOT_BAIT,
                "obj.raw_sardine",
                5,
                200,
                32,
                192,
                "You catch a sardine.",
            )
            catchRow(
                "dbrow.fishing_herring",
                STYLE_NET_SPOT_BAIT,
                "obj.raw_herring",
                10,
                300,
                24,
                128,
                "You catch a herring.",
            )

            catchRow(
                "dbrow.fishing_trout",
                STYLE_LURE,
                "obj.raw_trout",
                20,
                500,
                32,
                192,
                "You catch a trout.",
            )
            catchRow(
                "dbrow.fishing_salmon",
                STYLE_LURE,
                "obj.raw_salmon",
                30,
                700,
                16,
                96,
                "You catch a salmon.",
            )

            catchRow(
                "dbrow.fishing_pike",
                STYLE_ROD_SPOT_BAIT,
                "obj.raw_pike",
                25,
                600,
                16,
                96,
                "You catch a pike.",
            )

            catchRow(
                "dbrow.fishing_lobster",
                STYLE_CAGE,
                "obj.raw_lobster",
                40,
                900,
                6,
                95,
                "You catch a lobster.",
            )

            row("dbrow.fishing_tuna") {
                column(COL_STYLE, STYLE_CAGE_SPOT_HARPOON)
                columnRSCM(COL_FISH_ITEM, "obj.raw_tuna")
                column(COL_LEVEL, 35)
                column(COL_XP_TENTHS, 800)
                column(COL_SUCCESS_RATE_LOW, 8)
                column(COL_SUCCESS_RATE_HIGH, 64)
                column(COL_DRAGON_RATE_LOW, 9)
                column(COL_DRAGON_RATE_HIGH, 76)
                column(COL_CRYSTAL_RATE_LOW, 10)
                column(COL_CRYSTAL_RATE_HIGH, 86)
                column(COL_CATCH_MESSAGE, "You catch a tuna.")
            }
            row("dbrow.fishing_swordfish") {
                column(COL_STYLE, STYLE_CAGE_SPOT_HARPOON)
                columnRSCM(COL_FISH_ITEM, "obj.raw_swordfish")
                column(COL_LEVEL, 50)
                column(COL_XP_TENTHS, 1000)
                column(COL_SUCCESS_RATE_LOW, 4)
                column(COL_SUCCESS_RATE_HIGH, 48)
                column(COL_DRAGON_RATE_LOW, 4)
                column(COL_DRAGON_RATE_HIGH, 57)
                column(COL_CRYSTAL_RATE_LOW, 5)
                column(COL_CRYSTAL_RATE_HIGH, 64)
                column(COL_CATCH_MESSAGE, "You catch a swordfish.")
            }

            // Big-net rows roll independently; mackerel adds 10/256 to one roll. Row order is unverified.
            row("dbrow.fishing_mackerel") {
                column(COL_STYLE, STYLE_BIG_NET)
                columnRSCM(COL_FISH_ITEM, "obj.raw_mackerel")
                column(COL_LEVEL, 16)
                column(COL_XP_TENTHS, 200)
                column(COL_SUCCESS_RATE_LOW, 5)
                column(COL_SUCCESS_RATE_HIGH, 65)
                column(COL_BONUS, 10)
                column(COL_CATCH_MESSAGE, "You catch a mackerel.")
            }
            catchRow(
                "dbrow.fishing_cod",
                STYLE_BIG_NET,
                "obj.raw_cod",
                23,
                450,
                4,
                55,
                "You catch a cod.",
            )
            catchRow(
                "dbrow.fishing_bass",
                STYLE_BIG_NET,
                "obj.raw_bass",
                46,
                1000,
                3,
                40,
                "You catch a bass.",
            )
            catchRow(
                "dbrow.fishing_casket",
                STYLE_BIG_NET,
                "obj.casket",
                16,
                0,
                1,
                2,
                "You catch a casket.",
            )
            catchRow(
                "dbrow.fishing_oyster",
                STYLE_BIG_NET,
                "obj.oystershell",
                16,
                0,
                3,
                7,
                "You catch an oyster.",
            )
            catchRow(
                "dbrow.fishing_leather_gloves",
                STYLE_BIG_NET,
                "obj.leather_gloves",
                16,
                0,
                5,
                5,
                "You catch some leather gloves.",
            )
            catchRow(
                "dbrow.fishing_seaweed",
                STYLE_BIG_NET,
                "obj.seaweed",
                16,
                0,
                10,
                10,
                "You catch some seaweed.",
            )
            catchRow(
                "dbrow.fishing_leather_boots",
                STYLE_BIG_NET,
                "obj.leather_boots",
                16,
                0,
                10,
                10,
                "You catch some leather boots.",
            )

            row("dbrow.fishing_shark") {
                column(COL_STYLE, STYLE_BIG_NET_SPOT_HARPOON)
                columnRSCM(COL_FISH_ITEM, "obj.raw_shark")
                column(COL_LEVEL, 76)
                column(COL_XP_TENTHS, 1100)
                column(COL_SUCCESS_RATE_LOW, 3)
                column(COL_SUCCESS_RATE_HIGH, 40)
                column(COL_DRAGON_RATE_LOW, 3)
                column(COL_DRAGON_RATE_HIGH, 48)
                column(COL_CRYSTAL_RATE_LOW, 4)
                column(COL_CRYSTAL_RATE_HIGH, 54)
                column(COL_CATCH_MESSAGE, "You catch a shark.")
            }

            // Source: OSRS wiki Raw monkfish. Piscatoris op3 reuses the cage-spot harpoon rows.
            catchRow(
                "dbrow.fishing_monkfish",
                STYLE_MONKFISH_NET,
                "obj.raw_monkfish",
                62,
                1200,
                48,
                90,
                "You catch a monkfish.",
            )

            // Source: OSRS wiki Raw anglerfish; the diabolic-worm variant remains deferred.
            catchRow(
                "dbrow.fishing_anglerfish",
                STYLE_ANGLER_BAIT,
                "obj.raw_anglerfish",
                82,
                1200,
                3,
                36,
                "You catch an anglerfish.",
            )

            // Resolve leaping fish by descending level; catch wording remains unverified.
            fun barbarianRow(
                rowId: String,
                fish: String,
                level: Int,
                secondaryLevel: Int,
                xpTenths: Int,
                secondaryXpTenths: Int,
                low: Int,
                high: Int,
                message: String,
            ) =
                row(rowId) {
                    column(COL_STYLE, STYLE_BARBARIAN_ROD)
                    columnRSCM(COL_FISH_ITEM, fish)
                    column(COL_LEVEL, level)
                    column(COL_XP_TENTHS, xpTenths)
                    column(COL_SUCCESS_RATE_LOW, low)
                    column(COL_SUCCESS_RATE_HIGH, high)
                    column(COL_AGILITY_LEVEL, secondaryLevel)
                    column(COL_STRENGTH_LEVEL, secondaryLevel)
                    column(COL_SECONDARY_XP_TENTHS, secondaryXpTenths)
                    column(COL_CATCH_MESSAGE, message)
                }

            barbarianRow(
                "dbrow.fishing_leaping_trout",
                "obj.brut_spawning_trout",
                level = 48,
                secondaryLevel = 15,
                xpTenths = 500,
                secondaryXpTenths = 50,
                low = 32,
                high = 192,
                message = "You catch a leaping trout.",
            )
            barbarianRow(
                "dbrow.fishing_leaping_salmon",
                "obj.brut_spawning_salmon",
                level = 58,
                secondaryLevel = 30,
                xpTenths = 700,
                secondaryXpTenths = 60,
                low = 16,
                high = 96,
                message = "You catch a leaping salmon.",
            )
            barbarianRow(
                "dbrow.fishing_leaping_sturgeon",
                "obj.brut_sturgeon",
                level = 70,
                secondaryLevel = 45,
                xpTenths = 800,
                secondaryXpTenths = 70,
                low = 8,
                high = 64,
                message = "You catch a leaping sturgeon.",
            )

            // Specialty success pairs and catch wording remain unverified; see fishing-audit.md.

            // Mort Myre / Haunted Mine bait spots (op1 Bait; npc 2653-2655).
            catchRow(
                "dbrow.fishing_slimy_eel",
                STYLE_SLIMY_EEL,
                "obj.mort_slimey_eel",
                28,
                650,
                10,
                80,
                "You catch a slimy eel.",
            )
            catchRow(
                "dbrow.fishing_cave_eel",
                STYLE_CAVE_EEL,
                "obj.raw_cave_eel",
                38,
                800,
                10,
                80,
                "You catch a cave eel.",
            )
            // These spots cascade cave eel before slimy eel; slimy eel XP is 80 here and 65 at Mort Myre.
            catchRow(
                "dbrow.fishing_cave_eel_slimy",
                STYLE_CAVE_EEL,
                "obj.mort_slimey_eel",
                28,
                800,
                10,
                80,
                "You catch a slimy eel.",
            )

            catchRow(
                "dbrow.fishing_lava_eel",
                STYLE_LAVA_EEL,
                "obj.raw_lava_eel",
                53,
                600,
                16,
                96,
                "You catch a lava eel.",
            )
            catchRow(
                "dbrow.fishing_infernal_eel",
                STYLE_INFERNAL_EEL,
                "obj.infernal_eel",
                80,
                950,
                30,
                93,
                "You catch an infernal eel.",
            )
            catchRow(
                "dbrow.fishing_dark_crab",
                STYLE_DARK_CRAB,
                "obj.raw_dark_crab",
                85,
                1300,
                3,
                40,
                "You catch a dark crab.",
            )
            // Karambwanji quantity scales with level in content; uniform table bounds cannot express it.
            catchRow(
                "dbrow.fishing_karambwanji",
                STYLE_KARAMBWANJI,
                "obj.tbwt_raw_karambwanji",
                5,
                50,
                100,
                250,
                "You catch a karambwanji.",
            )
            // Shrimps are the low-level fallback in the karambwanji spot's catch cascade.
            catchRow(
                "dbrow.fishing_karambwanji_shrimp",
                STYLE_KARAMBWANJI,
                "obj.raw_shrimp",
                1,
                100,
                48,
                256,
                "You catch some shrimps.",
            )
            // Minnow quantity scales in content; base level gates entry, while boosts affect catches.
            row("dbrow.fishing_minnow") {
                column(COL_STYLE, STYLE_MINNOW)
                columnRSCM(COL_FISH_ITEM, "obj.minnow")
                column(COL_LEVEL, 82)
                column(COL_XP_TENTHS, 261)
                column(COL_SUCCESS_RATE_LOW, 140)
                column(COL_SUCCESS_RATE_HIGH, 280)
                column(COL_COUNT_MIN, 10)
                column(COL_COUNT_MAX, 14)
                column(COL_CATCH_MESSAGE, "You catch some minnows.")
            }
        }
}
