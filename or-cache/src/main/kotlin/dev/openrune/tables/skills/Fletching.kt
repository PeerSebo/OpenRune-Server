package dev.openrune.tables.skills

import dev.openrune.definition.util.VarType
import dev.openrune.tables.production.productionTable

/**
 * The latter's section 6 tracks unimplemented recipes.
 *
 * Content must read `xp_tenths`: the shared production builder derives the integer `xp` column
 * by flooring, which loses fractional XP. Ammo inputs and XP are per item; scripts batch 15
 * arrows/javelins or 10 darts/bolts and scale XP to the actual count consumed.
 */
object Fletching {

    const val COL_ANIM = 8

    const val COL_TICKS = 8
    const val COL_KNIFE_TICKS = 9

    fun carving() =
        productionTable(
            "dbtable.fletching_carving",
            serverOnly = true,
            defaultCategory = "Carve",
            extraColumns = {
                column("ticks", COL_TICKS, VarType.INT)
                column("knife_ticks", COL_KNIFE_TICKS, VarType.INT)
            },
        ) {
            fun carveRow(
                rowId: String,
                log: String,
                level: Int,
                tenths: Int,
                output: String,
                outputAmount: Int,
                ticks: Int,
                logAmount: Int = 1,
                secondary: String? = null,
                secondaryAmount: Int = 1,
                craftingLevel: Int? = null,
            ) =
                row(rowId) {
                    production {
                        input(log, logAmount)
                        secondary?.let { input(it, secondaryAmount) }
                        statReq("stat.fletching", level)
                        craftingLevel?.let { statReq("stat.crafting", it) }
                        xpTenths(tenths)
                        output(output, outputAmount)
                    }
                    column(COL_TICKS, ticks)
                    column(COL_KNIFE_TICKS, (ticks - 1).coerceAtLeast(1))
                }

            // Shafts store the first-cut cadence (2 ticks). KnifeCarvingScript.carveCadence uses
            // 3 ticks for subsequent cuts on all seven tiers (fletching-audit.md #5).
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_logs", "obj.sap_treated_logs", 1, 50, "obj.seeking_arrow_shaft", 15, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_oak_logs", "obj.sap_treated_oak_logs", 15, 100, "obj.seeking_arrow_shaft", 30, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_willow_logs", "obj.sap_treated_willow_logs", 30, 150, "obj.seeking_arrow_shaft", 45, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_maple_logs", "obj.sap_treated_maple_logs", 45, 200, "obj.seeking_arrow_shaft", 60, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_yew_logs", "obj.sap_treated_yew_logs", 60, 250, "obj.seeking_arrow_shaft", 75, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_magic_logs", "obj.sap_treated_magic_logs", 75, 300, "obj.seeking_arrow_shaft", 90, 2)
            carveRow("dbrow.fletching_seeking_arrow_shaft_from_sap_treated_redwood_logs", "obj.sap_treated_redwood_logs", 90, 350, "obj.seeking_arrow_shaft", 105, 2)
            carveRow("dbrow.fletching_camphor_blowpipe_empty_from_camphor_logs", "obj.camphor_logs", 58, 1400, "obj.camphor_blowpipe_empty", 1, 3, logAmount = 2, secondary = "obj.squid_beak", secondaryAmount = 1)
            carveRow("dbrow.fletching_ironwood_blowpipe_empty_from_ironwood_logs", "obj.ironwood_logs", 72, 1700, "obj.ironwood_blowpipe_empty", 1, 3, logAmount = 2, secondary = "obj.squid_beak", secondaryAmount = 1)
            carveRow("dbrow.fletching_rosewood_blowpipe_empty_from_rosewood_logs", "obj.rosewood_logs", 84, 2000, "obj.rosewood_blowpipe_empty", 1, 3, logAmount = 2, secondary = "obj.squid_beak", secondaryAmount = 1)

            carveRow("dbrow.fletching_shafts_logs", "obj.logs", 1, 50, "obj.arrow_shaft", 15, 2)
            carveRow(
                "dbrow.fletching_shafts_oak",
                "obj.oak_logs",
                15,
                100,
                "obj.arrow_shaft",
                30,
                2,
            )
            carveRow(
                "dbrow.fletching_shafts_willow",
                "obj.willow_logs",
                30,
                150,
                "obj.arrow_shaft",
                45,
                2,
            )
            carveRow(
                "dbrow.fletching_shafts_maple",
                "obj.maple_logs",
                45,
                200,
                "obj.arrow_shaft",
                60,
                2,
            )
            carveRow(
                "dbrow.fletching_shafts_yew",
                "obj.yew_logs",
                60,
                250,
                "obj.arrow_shaft",
                75,
                2,
            )
            carveRow(
                "dbrow.fletching_shafts_magic",
                "obj.magic_logs",
                75,
                300,
                "obj.arrow_shaft",
                90,
                2,
            )
            carveRow(
                "dbrow.fletching_shafts_redwood",
                "obj.redwood_logs",
                90,
                350,
                "obj.arrow_shaft",
                105,
                2,
            )

            carveRow(
                "dbrow.fletching_javelin_shafts",
                "obj.logs",
                3,
                50,
                "obj.javelin_shaft",
                15,
                2,
            )

            carveRow(
                "dbrow.fletching_carve_shortbow",
                "obj.logs",
                5,
                50,
                "obj.unstrung_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_longbow",
                "obj.logs",
                10,
                100,
                "obj.unstrung_longbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_oak_shortbow",
                "obj.oak_logs",
                20,
                165,
                "obj.unstrung_oak_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_oak_longbow",
                "obj.oak_logs",
                25,
                250,
                "obj.unstrung_oak_longbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_willow_shortbow",
                "obj.willow_logs",
                35,
                333,
                "obj.unstrung_willow_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_willow_longbow",
                "obj.willow_logs",
                40,
                415,
                "obj.unstrung_willow_longbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_maple_shortbow",
                "obj.maple_logs",
                50,
                500,
                "obj.unstrung_maple_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_maple_longbow",
                "obj.maple_logs",
                55,
                583,
                "obj.unstrung_maple_longbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_yew_shortbow",
                "obj.yew_logs",
                65,
                675,
                "obj.unstrung_yew_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_yew_longbow",
                "obj.yew_logs",
                70,
                750,
                "obj.unstrung_yew_longbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_magic_shortbow",
                "obj.magic_logs",
                80,
                833,
                "obj.unstrung_magic_shortbow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_carve_magic_longbow",
                "obj.magic_logs",
                85,
                915,
                "obj.unstrung_magic_longbow",
                1,
                3,
            )

            carveRow(
                "dbrow.fletching_stock_wood",
                "obj.logs",
                9,
                60,
                "obj.xbows_crossbow_stock_wood",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_oak",
                "obj.oak_logs",
                24,
                160,
                "obj.xbows_crossbow_stock_oak",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_willow",
                "obj.willow_logs",
                39,
                220,
                "obj.xbows_crossbow_stock_willow",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_teak",
                "obj.teak_logs",
                46,
                270,
                "obj.xbows_crossbow_stock_teak",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_maple",
                "obj.maple_logs",
                54,
                320,
                "obj.xbows_crossbow_stock_maple",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_mahogany",
                "obj.mahogany_logs",
                61,
                410,
                "obj.xbows_crossbow_stock_mahogany",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_yew",
                "obj.yew_logs",
                69,
                500,
                "obj.xbows_crossbow_stock_yew",
                1,
                3,
            )
            carveRow(
                "dbrow.fletching_stock_magic",
                "obj.magic_logs",
                78,
                700,
                "obj.xbows_crossbow_stock_magic",
                1,
                3,
            )

            // Shields share the knife/log handler to avoid duplicate registrations. Two logs
            // and seven ticks.
            carveRow(
                "dbrow.fletching_shield_oak",
                "obj.oak_logs",
                27,
                500,
                "obj.oak_shield",
                1,
                7,
                logAmount = 2,
            )
            carveRow(
                "dbrow.fletching_shield_willow",
                "obj.willow_logs",
                42,
                830,
                "obj.willow_shield",
                1,
                7,
                logAmount = 2,
            )
            carveRow(
                "dbrow.fletching_shield_maple",
                "obj.maple_logs",
                57,
                1165,
                "obj.maple_shield",
                1,
                7,
                logAmount = 2,
            )
            carveRow(
                "dbrow.fletching_shield_yew",
                "obj.yew_logs",
                72,
                1500,
                "obj.yew_shield",
                1,
                7,
                logAmount = 2,
            )
            carveRow(
                "dbrow.fletching_shield_magic",
                "obj.magic_logs",
                87,
                1830,
                "obj.magic_shield",
                1,
                7,
                logAmount = 2,
            )
            carveRow(
                "dbrow.fletching_shield_redwood",
                "obj.redwood_logs",
                92,
                2160,
                "obj.redwood_shield",
                1,
                7,
                logAmount = 2,
            )
        }

    fun stringing() =
        productionTable(
            "dbtable.fletching_stringing",
            serverOnly = true,
            defaultCategory = "String",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Strung XP on willow/maple/magic short and maple long is 0.1 LOWER than the cut XP
            // (33.2 vs 33.3, 58.2 vs 58.3, 83.2 vs 83.3) - deliberate, per the research doc.
            fun stringRow(
                rowId: String,
                unstrung: String,
                level: Int,
                tenths: Int,
                strung: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(unstrung)
                        input("obj.bow_string")
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(strung)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            stringRow(
                "dbrow.fletching_string_shortbow",
                "obj.unstrung_shortbow",
                5,
                50,
                "obj.shortbow",
                "seq.stringing_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_longbow",
                "obj.unstrung_longbow",
                10,
                100,
                "obj.longbow",
                "seq.stringing_longbow",
            )
            stringRow(
                "dbrow.fletching_string_oak_shortbow",
                "obj.unstrung_oak_shortbow",
                20,
                165,
                "obj.oak_shortbow",
                "seq.stringing_oak_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_oak_longbow",
                "obj.unstrung_oak_longbow",
                25,
                250,
                "obj.oak_longbow",
                "seq.stringing_oak_longbow",
            )
            stringRow(
                "dbrow.fletching_string_willow_shortbow",
                "obj.unstrung_willow_shortbow",
                35,
                332,
                "obj.willow_shortbow",
                "seq.stringing_willow_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_willow_longbow",
                "obj.unstrung_willow_longbow",
                40,
                415,
                "obj.willow_longbow",
                "seq.stringing_willow_longbow",
            )
            stringRow(
                "dbrow.fletching_string_maple_shortbow",
                "obj.unstrung_maple_shortbow",
                50,
                500,
                "obj.maple_shortbow",
                "seq.stringing_maple_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_maple_longbow",
                "obj.unstrung_maple_longbow",
                55,
                582,
                "obj.maple_longbow",
                "seq.stringing_maple_longbow",
            )
            stringRow(
                "dbrow.fletching_string_yew_shortbow",
                "obj.unstrung_yew_shortbow",
                65,
                675,
                "obj.yew_shortbow",
                "seq.stringing_yew_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_yew_longbow",
                "obj.unstrung_yew_longbow",
                70,
                750,
                "obj.yew_longbow",
                "seq.stringing_yew_longbow",
            )
            stringRow(
                "dbrow.fletching_string_magic_shortbow",
                "obj.unstrung_magic_shortbow",
                80,
                832,
                "obj.magic_shortbow",
                "seq.stringing_magic_shortbow",
            )
            stringRow(
                "dbrow.fletching_string_magic_longbow",
                "obj.unstrung_magic_longbow",
                85,
                915,
                "obj.magic_longbow",
                "seq.stringing_magic_longbow",
            )
        }

    fun arrows() =
        productionTable(
            "dbtable.fletching_arrows",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Inputs and XP are per single arrow; the script works in sets of 15.
            fun arrowRow(
                rowId: String,
                attachment: String,
                base: String,
                level: Int,
                tenths: Int,
                output: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(attachment)
                        input(base)
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            arrowRow("dbrow.fletching_seeking_bronze_arrow_from_bronze_arrowheads", "obj.bronze_arrowheads", "obj.seeking_headless_arrow", 1, 13, "obj.seeking_bronze_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_headless_arrow_from_feather", "obj.feather", "obj.seeking_arrow_shaft", 1, 10, "obj.seeking_headless_arrow", "seq.human_fletching_add_feather")
            arrowRow("dbrow.fletching_seeking_iron_arrow_from_iron_arrowheads", "obj.iron_arrowheads", "obj.seeking_headless_arrow", 15, 25, "obj.seeking_iron_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_steel_arrow_from_seeking_headless_arrow", "obj.seeking_headless_arrow", "obj.steel_arrowheads", 15, 25, "obj.seeking_steel_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_mithril_arrow_from_mithril_arrowheads", "obj.mithril_arrowheads", "obj.seeking_headless_arrow", 45, 75, "obj.seeking_mithril_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_slayer_broad_arrows_from_slayer_broad_arrowhead", "obj.slayer_broad_arrowhead", "obj.seeking_headless_arrow", 52, 100, "obj.seeking_slayer_broad_arrows", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_adamant_arrow_from_adamant_arrowheads", "obj.adamant_arrowheads", "obj.seeking_headless_arrow", 60, 100, "obj.seeking_adamant_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_rune_arrow_from_rune_arrowheads", "obj.rune_arrowheads", "obj.seeking_headless_arrow", 75, 125, "obj.seeking_rune_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_amethyst_arrow_from_amethyst_arrowheads", "obj.amethyst_arrowheads", "obj.seeking_headless_arrow", 82, 135, "obj.seeking_amethyst_arrow", "seq.human_fletching_add_arrow_tips")
            arrowRow("dbrow.fletching_seeking_dragon_arrow_from_dragon_arrowheads", "obj.dragon_arrowheads", "obj.seeking_headless_arrow", 90, 150, "obj.seeking_dragon_arrow", "seq.human_fletching_add_arrow_tips")

            arrowRow(
                "dbrow.fletching_headless_arrow",
                "obj.feather",
                "obj.arrow_shaft",
                1,
                10,
                "obj.headless_arrow",
                "seq.human_fletching_add_feather",
            )
            arrowRow(
                "dbrow.fletching_bronze_arrow",
                "obj.bronze_arrowheads",
                "obj.headless_arrow",
                1,
                13,
                "obj.bronze_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_iron_arrow",
                "obj.iron_arrowheads",
                "obj.headless_arrow",
                15,
                25,
                "obj.iron_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_steel_arrow",
                "obj.steel_arrowheads",
                "obj.headless_arrow",
                30,
                50,
                "obj.steel_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_mithril_arrow",
                "obj.mithril_arrowheads",
                "obj.headless_arrow",
                45,
                75,
                "obj.mithril_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_adamant_arrow",
                "obj.adamant_arrowheads",
                "obj.headless_arrow",
                60,
                100,
                "obj.adamant_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_rune_arrow",
                "obj.rune_arrowheads",
                "obj.headless_arrow",
                75,
                125,
                "obj.rune_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_amethyst_arrow",
                "obj.amethyst_arrowheads",
                "obj.headless_arrow",
                82,
                135,
                "obj.amethyst_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
            arrowRow(
                "dbrow.fletching_dragon_arrow",
                "obj.dragon_arrowheads",
                "obj.headless_arrow",
                90,
                150,
                "obj.dragon_arrow",
                "seq.human_fletching_add_arrow_tips",
            )
        }

    fun javelins() =
        productionTable(
            "dbtable.fletching_javelins",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Inputs/XP are per javelin; batches contain 15. add_arrow_tips is an unverified
            // animation stand-in.
            fun javelinRow(rowId: String, head: String, level: Int, tenths: Int, output: String) =
                row(rowId) {
                    production {
                        input(head)
                        input("obj.javelin_shaft")
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output)
                    }
                    columnRSCM(COL_ANIM, "seq.human_fletching_add_arrow_tips")
                }

            javelinRow(
                "dbrow.fletching_bronze_javelin",
                "obj.bronze_javelin_head",
                3,
                10,
                "obj.bronze_javelin",
            )
            javelinRow(
                "dbrow.fletching_iron_javelin",
                "obj.iron_javelin_head",
                17,
                20,
                "obj.iron_javelin",
            )
            javelinRow(
                "dbrow.fletching_steel_javelin",
                "obj.steel_javelin_head",
                32,
                50,
                "obj.steel_javelin",
            )
            javelinRow(
                "dbrow.fletching_mithril_javelin",
                "obj.mithril_javelin_head",
                47,
                80,
                "obj.mithril_javelin",
            )
            javelinRow(
                "dbrow.fletching_adamant_javelin",
                "obj.adamant_javelin_head",
                62,
                100,
                "obj.adamant_javelin",
            )
            javelinRow(
                "dbrow.fletching_rune_javelin",
                "obj.rune_javelin_head",
                77,
                124,
                "obj.rune_javelin",
            )
            javelinRow(
                "dbrow.fletching_amethyst_javelin",
                "obj.amethyst_javelin_head",
                84,
                135,
                "obj.amethyst_javelin",
            )
            javelinRow(
                "dbrow.fletching_dragon_javelin",
                "obj.dragon_javelin_head",
                92,
                150,
                "obj.dragon_javelin",
            )
        }

    fun darts() =
        productionTable(
            "dbtable.fletching_darts",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Inputs and XP are per single dart; the script works in sets of 10. The Tourist
            // Trap gates smithing the tips, not fletching them - no quest check here.
            fun dartRow(
                rowId: String,
                tip: String,
                level: Int,
                tenths: Int,
                output: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(tip)
                        input("obj.feather")
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            dartRow(
                "dbrow.fletching_bronze_dart",
                "obj.bronze_dart_tip",
                10,
                18,
                "obj.bronze_dart",
                "seq.human_fletching_add_dart_feathers_bronze",
            )
            dartRow(
                "dbrow.fletching_iron_dart",
                "obj.iron_dart_tip",
                22,
                38,
                "obj.iron_dart",
                "seq.human_fletching_add_dart_feathers_iron",
            )
            dartRow(
                "dbrow.fletching_steel_dart",
                "obj.steel_dart_tip",
                37,
                75,
                "obj.steel_dart",
                "seq.human_fletching_add_dart_feathers_steel",
            )
            dartRow(
                "dbrow.fletching_mithril_dart",
                "obj.mithril_dart_tip",
                52,
                112,
                "obj.mithril_dart",
                "seq.human_fletching_add_dart_feathers_mithril",
            )
            dartRow(
                "dbrow.fletching_adamant_dart",
                "obj.adamant_dart_tip",
                67,
                150,
                "obj.adamant_dart",
                "seq.human_fletching_add_dart_feathers_adamant",
            )
            dartRow(
                "dbrow.fletching_rune_dart",
                "obj.rune_dart_tip",
                81,
                188,
                "obj.rune_dart",
                "seq.human_fletching_add_dart_feathers_rune",
            )
            dartRow(
                "dbrow.fletching_amethyst_dart",
                "obj.amethyst_dart_tip",
                90,
                210,
                "obj.amethyst_dart",
                "seq.human_fletching_add_dart_feathers_amethyst",
            )
            dartRow(
                "dbrow.fletching_dragon_dart",
                "obj.dragon_dart_tip",
                95,
                250,
                "obj.dragon_dart",
                "seq.human_fletching_add_dart_feathers_dragon",
            )
        }

    fun bolts() =
        productionTable(
            "dbtable.fletching_bolts",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Inputs and XP are per single bolt; the script works in sets of 10. Broad bolts
            // (slayer unlock) are their own separate rows in section 10's broadAmmo() table below,
            // not here. Note the finished bronze bolt is `obj.bolt` (877), not an xbows_ symbol.
            fun boltRow(
                rowId: String,
                unf: String,
                level: Int,
                tenths: Int,
                output: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(unf)
                        input("obj.feather")
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            boltRow(
                "dbrow.fletching_bronze_bolts",
                "obj.xbows_crossbow_bolts_bronze_unfeathered",
                9,
                5,
                "obj.bolt",
                "seq.human_fletching_add_bolt_feathers_bronze",
            )
            boltRow(
                "dbrow.fletching_blurite_bolts",
                "obj.xbows_crossbow_bolts_blurite_unfeathered",
                24,
                10,
                "obj.xbows_crossbow_bolts_blurite",
                "seq.human_fletching_add_bolt_feathers_blurite",
            )
            boltRow(
                "dbrow.fletching_iron_bolts",
                "obj.xbows_crossbow_bolts_iron_unfeathered",
                39,
                15,
                "obj.xbows_crossbow_bolts_iron",
                "seq.human_fletching_add_bolt_feathers_iron",
            )
            boltRow(
                "dbrow.fletching_silver_bolts",
                "obj.xbows_crossbow_bolts_silver_unfeathered",
                43,
                25,
                "obj.xbows_crossbow_bolts_silver",
                "seq.human_fletching_add_bolt_feathers_silver",
            )
            boltRow(
                "dbrow.fletching_steel_bolts",
                "obj.xbows_crossbow_bolts_steel_unfeathered",
                46,
                35,
                "obj.xbows_crossbow_bolts_steel",
                "seq.human_fletching_add_bolt_feathers_steel",
            )
            boltRow(
                "dbrow.fletching_mithril_bolts",
                "obj.xbows_crossbow_bolts_mithril_unfeathered",
                54,
                50,
                "obj.xbows_crossbow_bolts_mithril",
                "seq.human_fletching_add_bolt_feathers_mithril",
            )
            boltRow(
                "dbrow.fletching_adamant_bolts",
                "obj.xbows_crossbow_bolts_adamantite_unfeathered",
                61,
                70,
                "obj.xbows_crossbow_bolts_adamantite",
                "seq.human_fletching_add_bolt_feathers_adamant",
            )
            boltRow(
                "dbrow.fletching_rune_bolts",
                "obj.xbows_crossbow_bolts_runite_unfeathered",
                69,
                100,
                "obj.xbows_crossbow_bolts_runite",
                "seq.human_fletching_add_bolt_feathers_rune",
            )
            boltRow(
                "dbrow.fletching_dragon_bolts",
                "obj.dragon_bolts_unfeathered",
                84,
                120,
                "obj.dragon_bolts",
                "seq.human_fletching_add_bolt_feathers_dragon",
            )
        }

    /** Hammer carried (not consumed) for limb-attachment rows only - crossbows table. */
    const val COL_TOOL = 9

    /**
     * Assembly requires a carried hammer; stringing does not. Assembly is two ticks per wiki
     * recipes; two-tick stringing is inferred.
     */
    fun crossbows() =
        productionTable(
            "dbtable.fletching_crossbows",
            serverOnly = true,
            defaultCategory = "Attach",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
                column("tool", COL_TOOL, VarType.OBJ)
            },
        ) {
            fun assembleRow(
                rowId: String,
                stock: String,
                limbs: String,
                level: Int,
                tenths: Int,
                unstrung: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(stock)
                        input(limbs)
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(unstrung)
                    }
                    columnRSCM(COL_ANIM, anim)
                    columnRSCM(COL_TOOL, "obj.hammer")
                }

            fun stringRow(
                rowId: String,
                unstrung: String,
                level: Int,
                tenths: Int,
                finished: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(unstrung)
                        input("obj.xbows_crossbow_string")
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(finished)
                        category("String")
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            assembleRow(
                "dbrow.fletching_xbow_assemble_bronze",
                "obj.xbows_crossbow_stock_wood",
                "obj.xbows_crossbow_limbs_bronze",
                9,
                120,
                "obj.xbows_crossbow_unstrung_bronze",
                "seq.xbows_fletching_wood_bronze",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_blurite",
                "obj.xbows_crossbow_stock_oak",
                "obj.xbows_crossbow_limbs_blurite",
                24,
                320,
                "obj.xbows_crossbow_unstrung_blurite",
                "seq.xbows_fletching_oak_blurite",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_iron",
                "obj.xbows_crossbow_stock_willow",
                "obj.xbows_crossbow_limbs_iron",
                39,
                440,
                "obj.xbows_crossbow_unstrung_iron",
                "seq.xbows_fletching_willow_iron",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_steel",
                "obj.xbows_crossbow_stock_teak",
                "obj.xbows_crossbow_limbs_steel",
                46,
                540,
                "obj.xbows_crossbow_unstrung_steel",
                "seq.xbows_fletching_teak_steel",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_mithril",
                "obj.xbows_crossbow_stock_maple",
                "obj.xbows_crossbow_limbs_mithril",
                54,
                640,
                "obj.xbows_crossbow_unstrung_mithril",
                "seq.xbows_fletching_maple_mithril",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_adamant",
                "obj.xbows_crossbow_stock_mahogany",
                "obj.xbows_crossbow_limbs_adamantite",
                61,
                820,
                "obj.xbows_crossbow_unstrung_adamantite",
                "seq.xbows_fletching_mahogany_adamantite",
            )
            assembleRow(
                "dbrow.fletching_xbow_assemble_rune",
                "obj.xbows_crossbow_stock_yew",
                "obj.xbows_crossbow_limbs_runite",
                69,
                1000,
                "obj.xbows_crossbow_unstrung_runite",
                "seq.xbows_fletching_yew_runite",
            )
            // Dragon crossbows require magic stock (21952), matching the level-78 carving row.
            // Cache ids 21918/21921 identify dragon limbs/unstrung crossbow. The animation name
            // xbows_fletching_yew_dragon describes an asset, not the recipe's wood tier.
            assembleRow(
                "dbrow.fletching_xbow_assemble_dragon",
                "obj.xbows_crossbow_stock_magic",
                "obj.xbows_crossbow_limbs_dragon",
                78,
                1350,
                "obj.xbows_crossbow_unstrung_dragon",
                "seq.xbows_fletching_yew_dragon",
            )

            stringRow(
                "dbrow.fletching_xbow_string_bronze",
                "obj.xbows_crossbow_unstrung_bronze",
                9,
                60,
                "obj.xbows_crossbow_bronze",
                "seq.xbows_stringing_crossbow_bronze",
            )
            stringRow(
                "dbrow.fletching_xbow_string_blurite",
                "obj.xbows_crossbow_unstrung_blurite",
                24,
                160,
                "obj.xbows_crossbow_blurite",
                "seq.xbows_stringing_crossbow_blurite",
            )
            stringRow(
                "dbrow.fletching_xbow_string_iron",
                "obj.xbows_crossbow_unstrung_iron",
                39,
                220,
                "obj.xbows_crossbow_iron",
                "seq.xbows_stringing_crossbow_iron",
            )
            stringRow(
                "dbrow.fletching_xbow_string_steel",
                "obj.xbows_crossbow_unstrung_steel",
                46,
                270,
                "obj.xbows_crossbow_steel",
                "seq.xbows_stringing_crossbow_steel",
            )
            stringRow(
                "dbrow.fletching_xbow_string_mithril",
                "obj.xbows_crossbow_unstrung_mithril",
                54,
                320,
                "obj.xbows_crossbow_mithril",
                "seq.xbows_stringing_crossbow_mithril",
            )
            stringRow(
                "dbrow.fletching_xbow_string_adamant",
                "obj.xbows_crossbow_unstrung_adamantite",
                61,
                410,
                "obj.xbows_crossbow_adamantite",
                "seq.xbows_stringing_crossbow_adamantite",
            )
            stringRow(
                "dbrow.fletching_xbow_string_rune",
                "obj.xbows_crossbow_unstrung_runite",
                69,
                500,
                "obj.xbows_crossbow_runite",
                "seq.xbows_stringing_crossbow_runite",
            )
            stringRow(
                "dbrow.fletching_xbow_string_dragon",
                "obj.xbows_crossbow_unstrung_dragon",
                78,
                700,
                "obj.xbows_crossbow_dragon",
                "seq.xbows_stringing_crossbow_dragon",
            )
        }

    // Gem tips award Fletching XP; amethyst tips belong to Crafting.

    fun boltTips() =
        productionTable(
            "dbtable.fletching_bolt_tips",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            fun tipRow(
                rowId: String,
                gem: String,
                level: Int,
                tenths: Int,
                output: String,
                outputAmount: Int,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(gem)
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output, outputAmount)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            // No dedicated bolt-tip cutting sequences were found, so these reuse per-gem
            // Crafting.gems() animations. Inferred; needs an rsprox check.
            tipRow("dbrow.fletching_bolt_tips_opal", "obj.opal", 11, 15, "obj.opal_bolttips", 12, "seq.human_opalcutting")
            tipRow("dbrow.fletching_bolt_tips_jade", "obj.jade", 26, 20, "obj.xbows_bolt_tips_jade", 12, "seq.human_jadecutting")
            tipRow(
                "dbrow.fletching_bolt_tips_red_topaz",
                "obj.red_topaz",
                48,
                39,
                "obj.xbows_bolt_tips_redtopaz",
                12,
                "seq.human_redtopazcutting",
            )
            tipRow(
                "dbrow.fletching_bolt_tips_sapphire",
                "obj.sapphire",
                56,
                40,
                "obj.xbows_bolt_tips_sapphire",
                12,
                "seq.human_sapphirecutting",
            )
            tipRow(
                "dbrow.fletching_bolt_tips_emerald",
                "obj.emerald",
                58,
                55,
                "obj.xbows_bolt_tips_emerald",
                12,
                "seq.human_emeraldcutting",
            )
            tipRow("dbrow.fletching_bolt_tips_ruby", "obj.ruby", 63, 63, "obj.xbows_bolt_tips_ruby", 12, "seq.human_rubycutting")
            tipRow(
                "dbrow.fletching_bolt_tips_diamond",
                "obj.diamond",
                65,
                70,
                "obj.xbows_bolt_tips_diamond",
                12,
                "seq.human_diamondcutting",
            )
            tipRow(
                "dbrow.fletching_bolt_tips_dragonstone",
                "obj.dragonstone",
                71,
                82,
                "obj.xbows_bolt_tips_dragonstone",
                12,
                "seq.human_dragonstonecutting",
            )
            tipRow(
                "dbrow.fletching_bolt_tips_onyx",
                "obj.onyx",
                73,
                94,
                "obj.xbows_bolt_tips_onyx",
                24,
                "seq.human_onyxcutting",
            )

            // Pearls yield 6 or 24 tips by input size. xbows_fletching_pearl (4470) is inferred
            // from its cache name; fixed per-craft XP is inferred from the research doc's single
            // XP figure for both sizes. Both need an rsprox check.
            tipRow(
                "dbrow.fletching_bolt_tips_pearl_small",
                "obj.smalloysterpearls",
                41,
                32,
                "obj.pearl_bolttips",
                6,
                "seq.xbows_fletching_pearl",
            )
            tipRow(
                "dbrow.fletching_bolt_tips_pearl_big",
                "obj.bigoysterpearls",
                41,
                32,
                "obj.pearl_bolttips",
                24,
                "seq.xbows_fletching_pearl",
            )
        }

    fun tippedBolts() =
        productionTable(
            "dbtable.fletching_tipped_bolts",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
            },
        ) {
            // Inputs and XP are per single bolt; the script works in sets of 10. `anim` is keyed by
            // the BOLT's metal (`seq.human_fletching_add_bolt_tips_<metal>`), shared by every gem
            // combined with that metal - confirmed against the cache's "Tipping seqs" gameval range
            // (8472-8479), which is metal-keyed with no per-gem variant.
            fun tippedRow(
                rowId: String,
                tip: String,
                bolt: String,
                level: Int,
                tenths: Int,
                output: String,
                anim: String,
            ) =
                row(rowId) {
                    production {
                        input(tip)
                        input(bolt)
                        statReq("stat.fletching", level)
                        xpTenths(tenths)
                        output(output)
                    }
                    columnRSCM(COL_ANIM, anim)
                }

            val bronzeAnim = "seq.human_fletching_add_bolt_tips_bronze"
            val bluriteAnim = "seq.human_fletching_add_bolt_tips_blurite"
            val ironAnim = "seq.human_fletching_add_bolt_tips_iron"
            val steelAnim = "seq.human_fletching_add_bolt_tips_steel"
            val mithrilAnim = "seq.human_fletching_add_bolt_tips_mithril"
            val adamantAnim = "seq.human_fletching_add_bolt_tips_adamant"
            val runeAnim = "seq.human_fletching_add_bolt_tips_rune"
            val dragonAnim = "seq.human_fletching_add_bolt_tips_dragon"

            // Old-style (RS2-era) items: opal/barb tip bronze `obj.bolt`; pearl tips iron bolts.
            tippedRow(
                "dbrow.fletching_tipped_bolt_opal",
                "obj.opal_bolttips",
                "obj.bolt",
                11,
                16,
                "obj.opal_bolt",
                bronzeAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_jade",
                "obj.xbows_bolt_tips_jade",
                "obj.xbows_crossbow_bolts_blurite",
                26,
                24,
                "obj.xbows_crossbow_bolts_blurite_tipped_jade",
                bluriteAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_pearl",
                "obj.pearl_bolttips",
                "obj.xbows_crossbow_bolts_iron",
                41,
                32,
                "obj.pearl_bolt",
                ironAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_red_topaz",
                "obj.xbows_bolt_tips_redtopaz",
                "obj.xbows_crossbow_bolts_steel",
                48,
                39,
                "obj.xbows_crossbow_bolts_steel_tipped_redtopaz",
                steelAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_barb",
                "obj.barbed_bolttips",
                "obj.bolt",
                51,
                95,
                "obj.barbed_bolt",
                bronzeAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_sapphire",
                "obj.xbows_bolt_tips_sapphire",
                "obj.xbows_crossbow_bolts_mithril",
                56,
                47,
                "obj.xbows_crossbow_bolts_mithril_tipped_sapphire",
                mithrilAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_emerald",
                "obj.xbows_bolt_tips_emerald",
                "obj.xbows_crossbow_bolts_mithril",
                58,
                55,
                "obj.xbows_crossbow_bolts_mithril_tipped_emerald",
                mithrilAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_ruby",
                "obj.xbows_bolt_tips_ruby",
                "obj.xbows_crossbow_bolts_adamantite",
                63,
                63,
                "obj.xbows_crossbow_bolts_adamantite_tipped_ruby",
                adamantAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_diamond",
                "obj.xbows_bolt_tips_diamond",
                "obj.xbows_crossbow_bolts_adamantite",
                65,
                70,
                "obj.xbows_crossbow_bolts_adamantite_tipped_diamond",
                adamantAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_dragonstone",
                "obj.xbows_bolt_tips_dragonstone",
                "obj.xbows_crossbow_bolts_runite",
                71,
                82,
                "obj.xbows_crossbow_bolts_runite_tipped_dragonstone",
                runeAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_bolt_onyx",
                "obj.xbows_bolt_tips_onyx",
                "obj.xbows_crossbow_bolts_runite",
                73,
                94,
                "obj.xbows_crossbow_bolts_runite_tipped_onyx",
                runeAnim,
            )

            // Tipped dragon bolts: all level 84, tip + `obj.dragon_bolts` -> unenchanted dragon
            // bolt (the Enchant Crossbow Bolt spells enchant these further; out of scope here).
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_opal",
                "obj.opal_bolttips",
                "obj.dragon_bolts",
                84,
                16,
                "obj.dragon_bolts_unenchanted_opal",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_jade",
                "obj.xbows_bolt_tips_jade",
                "obj.dragon_bolts",
                84,
                24,
                "obj.dragon_bolts_unenchanted_jade",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_pearl",
                "obj.pearl_bolttips",
                "obj.dragon_bolts",
                84,
                32,
                "obj.dragon_bolts_unenchanted_pearl",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_red_topaz",
                "obj.xbows_bolt_tips_redtopaz",
                "obj.dragon_bolts",
                84,
                40,
                "obj.dragon_bolts_unenchanted_topaz",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_sapphire",
                "obj.xbows_bolt_tips_sapphire",
                "obj.dragon_bolts",
                84,
                47,
                "obj.dragon_bolts_unenchanted_sapphire",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_emerald",
                "obj.xbows_bolt_tips_emerald",
                "obj.dragon_bolts",
                84,
                55,
                "obj.dragon_bolts_unenchanted_emerald",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_ruby",
                "obj.xbows_bolt_tips_ruby",
                "obj.dragon_bolts",
                84,
                63,
                "obj.dragon_bolts_unenchanted_ruby",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_diamond",
                "obj.xbows_bolt_tips_diamond",
                "obj.dragon_bolts",
                84,
                70,
                "obj.dragon_bolts_unenchanted_diamond",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_dragonstone",
                "obj.xbows_bolt_tips_dragonstone",
                "obj.dragon_bolts",
                84,
                82,
                "obj.dragon_bolts_unenchanted_dragonstone",
                dragonAnim,
            )
            tippedRow(
                "dbrow.fletching_tipped_dragon_bolt_onyx",
                "obj.xbows_bolt_tips_onyx",
                "obj.dragon_bolts",
                84,
                94,
                "obj.dragon_bolts_unenchanted_onyx",
                dragonAnim,
            )
        }

    // Broad ammo requires the Broader Fletching unlock, checked by content.

    /** Set size (15 arrows / 10 bolts) - broad ammo table only. */
    const val COL_SET_SIZE = 9

    fun broadAmmo() =
        productionTable(
            "dbtable.fletching_broad",
            serverOnly = true,
            defaultCategory = "Make",
            extraColumns = {
                column("anim", COL_ANIM, VarType.SEQ)
                column("set_size", COL_SET_SIZE, VarType.INT)
            },
        ) {
            row("dbrow.fletching_broad_arrows") {
                production {
                    input("obj.slayer_broad_arrowhead")
                    input("obj.headless_arrow")
                    statReq("stat.fletching", 52)
                    xpTenths(100)
                    output("obj.slayer_broad_arrows")
                }
                // No dedicated broad-arrow seq found; reuses the normal arrow-tipping anim (same
                // physical action - attaching a head to a headless arrow). Inferred.
                columnRSCM(COL_ANIM, "seq.human_fletching_add_arrow_tips")
                column(COL_SET_SIZE, 15)
            }
            row("dbrow.fletching_broad_bolts") {
                production {
                    input("obj.slayer_broad_bolt_unfinished")
                    input("obj.feather")
                    statReq("stat.fletching", 55)
                    xpTenths(30)
                    output("obj.slayer_broad_bolt")
                }
                // No dedicated broad-bolt seq found; reuses the bronze bolt-feathering anim (broad
                // bolts have no metal tier of their own). Inferred.
                columnRSCM(COL_ANIM, "seq.human_fletching_add_bolt_feathers_bronze")
                column(COL_SET_SIZE, 10)
            }
            row("dbrow.fletching_broad_bolts_amethyst") {
                production {
                    input("obj.xbows_bolt_tips_amethyst")
                    input("obj.slayer_broad_bolt")
                    statReq("stat.fletching", 76)
                    // 10.6xp is the one per-set tick figure the wiki states for any bolt (2 ticks
                    // per set of 10) - see FletchingParams.DART_BOLT_SET_TICKS.
                    xpTenths(106)
                    output("obj.slayer_broad_bolt_amethyst")
                }
                // No dedicated seq found; reuses the bronze tipping anim (broad bolts have no metal
                // tier). Inferred.
                columnRSCM(COL_ANIM, "seq.human_fletching_add_bolt_tips_bronze")
                column(COL_SET_SIZE, 10)
            }
        }

    fun specialAssembly() =
        productionTable(
            "dbtable.fletching_special_assembly",
            serverOnly = true,
            defaultCategory = "Batch",
            extraColumns = { column("ticks", COL_TICKS, VarType.INT) },
        ) {
            row("dbrow.fletching_seeking_adamant_arrow_from_adamant_arrow") {
                production {
                    input("obj.adamant_arrow", 15)
                    input("obj.bucket_of_bloodwood_sap", 1)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_adamant_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_amethyst_arrow_from_amethyst_arrow") {
                production {
                    input("obj.amethyst_arrow", 15)
                    input("obj.bucket_of_bloodwood_sap", 1)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_amethyst_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_slayer_broad_arrows_from_slayer_broad_arrows") {
                production {
                    input("obj.slayer_broad_arrows", 15)
                    input("obj.bucket_of_bloodwood_sap", 1)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_slayer_broad_arrows", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_bronze_arrow_from_bronze_arrow") {
                production {
                    input("obj.bronze_arrow", 15)
                    input("obj.bucket_of_bloodwood_sap", 1)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_bronze_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_dragon_arrow_from_bucket_of_bloodwood_sap") {
                production {
                    input("obj.bucket_of_bloodwood_sap", 1)
                    input("obj.dragon_arrow", 15)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_dragon_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_iron_arrow_from_bucket_of_bloodwood_sap") {
                production {
                    input("obj.bucket_of_bloodwood_sap", 1)
                    input("obj.iron_arrow", 15)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_iron_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_mithril_arrow_from_bucket_of_bloodwood_sap") {
                production {
                    input("obj.bucket_of_bloodwood_sap", 1)
                    input("obj.mithril_arrow", 15)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_mithril_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_rune_arrow_from_bucket_of_bloodwood_sap") {
                production {
                    input("obj.bucket_of_bloodwood_sap", 1)
                    input("obj.rune_arrow", 15)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_rune_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_steel_arrow_from_bucket_of_bloodwood_sap") {
                production {
                    input("obj.bucket_of_bloodwood_sap", 1)
                    input("obj.steel_arrow", 15)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_steel_arrow", 15)
                }
                column(COL_TICKS, 1)
            }
            row("dbrow.fletching_seeking_adamant_arrow_from_adamant_arrow_x150") {
                production {
                    input("obj.adamant_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_adamant_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_amethyst_arrow_from_amethyst_arrow_x150") {
                production {
                    input("obj.amethyst_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_amethyst_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_slayer_broad_arrows_from_slayer_broad_arrows_x150") {
                production {
                    input("obj.slayer_broad_arrows", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_slayer_broad_arrows", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_bronze_arrow_from_bronze_arrow_x150") {
                production {
                    input("obj.bronze_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_bronze_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_dragon_arrow_from_dragon_arrow_x150") {
                production {
                    input("obj.dragon_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_dragon_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_iron_arrow_from_iron_arrow_x150") {
                production {
                    input("obj.iron_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_iron_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_mithril_arrow_from_mithril_arrow_x150") {
                production {
                    input("obj.mithril_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_mithril_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_rune_arrow_from_rune_arrow_x150") {
                production {
                    input("obj.rune_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_rune_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
            row("dbrow.fletching_seeking_steel_arrow_from_steel_arrow_x150") {
                production {
                    input("obj.steel_arrow", 150)
                    input("obj.seeking_headless_arrow", 150)
                    statReq("stat.fletching", 1)
                    xpTenths(0)
                    output("obj.seeking_steel_arrow", 150)
                }
                column(COL_TICKS, 2)
            }
        }
}
