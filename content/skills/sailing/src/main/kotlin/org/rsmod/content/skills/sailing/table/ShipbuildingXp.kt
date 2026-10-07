package org.rsmod.content.skills.sailing.table

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.skills.sailing.ShipPartCategory
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants

internal object ShipbuildingXp {
    const val BUILD_TICKS: Int = 5

    val SHIPWRIGHT_FEE: Map<ShipPartCategory, Map<Int, Int>> =
        mapOf(
            ShipPartCategory.HULL to mapOf(1 to 560, 2 to 4_200, 3 to 27_000),
            ShipPartCategory.STEERING to mapOf(1 to 1_400, 2 to 8_900, 3 to 50_000),
            ShipPartCategory.SAIL to mapOf(1 to 920, 2 to 5_800, 3 to 41_000),
            ShipPartCategory.KEEL to mapOf(1 to 2_100, 2 to 11_000, 3 to 68_000),
        )

    private val CORE_PART_XP: Map<String, Double> =
        mapOf(
            "dbrow.sailing_boat_facility_1x3_eternal_brazier" to 1_373.0,
            "dbrow.sailing_boat_facility_2x5_eternal_brazier" to 1_373.0,
            "dbrow.sailing_boat_facility_3x8_eternal_brazier" to 1_373.0,
            "dbrow.sailing_boat_regular_raft_base" to 19.0,
            "dbrow.sailing_boat_oak_raft_base" to 27.0,
            "dbrow.sailing_boat_teak_raft_base" to 41.0,
            "dbrow.sailing_boat_mahogany_raft_base" to 46.0,
            "dbrow.sailing_boat_camphor_raft_base" to 70.0,
            "dbrow.sailing_boat_ironwood_raft_base" to 84.0,
            "dbrow.sailing_boat_rosewood_raft_base" to 93.0,
            "dbrow.sailing_boat_regular_hull_2x5" to 128.0,
            "dbrow.sailing_boat_oak_hull_2x5" to 238.0,
            "dbrow.sailing_boat_teak_hull_2x5" to 345.0,
            "dbrow.sailing_boat_mahogany_hull_2x5" to 588.0,
            "dbrow.sailing_boat_camphor_hull_2x5" to 881.0,
            "dbrow.sailing_boat_ironwood_hull_2x5" to 1_771.0,
            "dbrow.sailing_boat_rosewood_hull_2x5" to 2_839.0,
            "dbrow.sailing_boat_regular_hull_large" to 199.0,
            "dbrow.sailing_boat_oak_hull_large" to 373.0,
            "dbrow.sailing_boat_teak_hull_large" to 538.0,
            "dbrow.sailing_boat_mahogany_hull_large" to 919.0,
            "dbrow.sailing_boat_camphor_hull_large" to 1_380.0,
            "dbrow.sailing_boat_ironwood_hull_large" to 2_779.0,
            "dbrow.sailing_boat_rosewood_hull_large" to 4_474.0,
            "dbrow.sailing_boat_regular_mast_raft" to 20.0,
            "dbrow.sailing_boat_oak_mast_raft" to 28.0,
            "dbrow.sailing_boat_teak_mast_raft" to 41.0,
            "dbrow.sailing_boat_mahogany_mast_raft" to 55.0,
            "dbrow.sailing_boat_camphor_mast_raft" to 85.0,
            "dbrow.sailing_boat_ironwood_mast_raft" to 152.0,
            "dbrow.sailing_boat_rosewood_mast_raft" to 175.0,
            "dbrow.sailing_boat_regular_mast_2x5" to 24.0,
            "dbrow.sailing_boat_oak_mast_2x5" to 36.0,
            "dbrow.sailing_boat_teak_mast_2x5" to 49.0,
            "dbrow.sailing_boat_mahogany_mast_2x5" to 70.0,
            "dbrow.sailing_boat_camphor_mast_2x5" to 110.0,
            "dbrow.sailing_boat_ironwood_mast_2x5" to 197.0,
            "dbrow.sailing_boat_rosewood_mast_2x5" to 231.0,
            "dbrow.sailing_boat_regular_mast_large" to 31.0,
            "dbrow.sailing_boat_oak_mast_large" to 46.0,
            "dbrow.sailing_boat_teak_mast_large" to 65.0,
            "dbrow.sailing_boat_mahogany_mast_large" to 90.0,
            "dbrow.sailing_boat_camphor_mast_large" to 139.0,
            "dbrow.sailing_boat_ironwood_mast_large" to 250.0,
            "dbrow.sailing_boat_rosewood_mast_large" to 291.0,
            "dbrow.sailing_boat_bronze_steering_raft" to 76.0,
            "dbrow.sailing_boat_iron_steering_raft" to 160.0,
            "dbrow.sailing_boat_steel_steering_raft" to 260.0,
            "dbrow.sailing_boat_mithril_steering_raft" to 400.0,
            "dbrow.sailing_boat_adamant_steering_raft" to 480.0,
            "dbrow.sailing_boat_rune_steering_raft" to 650.0,
            "dbrow.sailing_boat_dragon_steering_raft" to 780.0,
            "dbrow.sailing_boat_bronze_steering_2x5" to 114.0,
            "dbrow.sailing_boat_iron_steering_2x5" to 240.0,
            "dbrow.sailing_boat_steel_steering_2x5" to 390.0,
            "dbrow.sailing_boat_mithril_steering_2x5" to 600.0,
            "dbrow.sailing_boat_adamant_steering_2x5" to 720.0,
            "dbrow.sailing_boat_rune_steering_2x5" to 975.0,
            "dbrow.sailing_boat_dragon_steering_2x5" to 1_410.0,
            "dbrow.sailing_boat_bronze_steering_large" to 152.0,
            "dbrow.sailing_boat_iron_steering_large" to 320.0,
            "dbrow.sailing_boat_steel_steering_large" to 520.0,
            "dbrow.sailing_boat_mithril_steering_large" to 800.0,
            "dbrow.sailing_boat_adamant_steering_large" to 960.0,
            "dbrow.sailing_boat_rune_steering_large" to 1_300.0,
            "dbrow.sailing_boat_dragon_steering_large" to 1_560.0,
            "dbrow.sailing_boat_bronze_keel_2x5" to 100.0,
            "dbrow.sailing_boat_iron_keel_2x5" to 200.0,
            "dbrow.sailing_boat_steel_keel_2x5" to 311.0,
            "dbrow.sailing_boat_mithril_keel_2x5" to 511.0,
            "dbrow.sailing_boat_adamant_keel_2x5" to 761.0,
            "dbrow.sailing_boat_rune_keel_2x5" to 1_511.0,
            "dbrow.sailing_boat_dragon_keel_2x5" to 2_511.0,
            "dbrow.sailing_boat_bronze_keel_large" to 160.0,
            "dbrow.sailing_boat_iron_keel_large" to 320.0,
            "dbrow.sailing_boat_steel_keel_large" to 491.0,
            "dbrow.sailing_boat_mithril_keel_large" to 811.0,
            "dbrow.sailing_boat_adamant_keel_large" to 1_211.0,
            "dbrow.sailing_boat_rune_keel_large" to 2_411.0,
            "dbrow.sailing_boat_dragon_keel_large" to 4_011.0,
        )

    // Null XP is valid for cosmetics; core parts must reject it as missing data.
    fun constructionXp(dbrow: String?): Double? = dbrow?.let(CORE_PART_XP::get)

    fun shipwrightFee(category: ShipPartCategory, tierIndex: Int): Int? {
        if (tierIndex > SailingConstants.OQ13_SHIPWRIGHT_MAX_TIER_INDEX) {
            return null
        }
        return SHIPWRIGHT_FEE[category]?.get(tierIndex)
    }

    fun assertEveryCorePartPriced() {
        val missing = mutableListOf<String>()
        for (boat in SailingBoats.all) {
            val rows =
                boat.hullOption.map { it.rowId } +
                    boat.keelOption.map { it.rowId } +
                    boat.sailOption.map { it.rowId } +
                    boat.steeringOption.map { it.rowId } +
                    listOfNotNull(boat.brazierOption).map { it.rowId }
            for (rowId in rows) {
                val symbol = dbrowSymbolOrNull(rowId)
                if (symbol == null) {
                    missing += "row $rowId (${boat.name}) - no dbrow symbol resolves"
                } else if (symbol !in CORE_PART_XP) {
                    missing += "$symbol (${boat.name})"
                }
            }
        }
        check(missing.isEmpty()) {
            "ShipbuildingXp has no Construction XP for ${missing.size} core boat part row(s): " +
                "${missing.joinToString()}. Every figure is wiki-sourced (research doc open " +
                "question 12) - author the missing rows rather than defaulting them to zero."
        }
    }

    // Keep symbol resolution independent of BoatBuildService to avoid a startup injection cycle.
    private fun dbrowSymbolOrNull(rowId: Int): String? =
        runCatching { RSCM.getReverseMapping(RSCMType.DBROW, rowId) }
            .getOrNull()
            ?.takeIf { it.startsWith("dbrow.") }
}
