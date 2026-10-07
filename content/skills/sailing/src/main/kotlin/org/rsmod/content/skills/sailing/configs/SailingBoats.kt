package org.rsmod.content.skills.sailing.configs

import dev.openrune.types.ObjectServerType
import org.rsmod.api.table.sailing.SailingBoatFacilityStatsRow
import org.rsmod.api.table.sailing.SailingBoatHullRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

object SailingBoats {

    const val DBROW_RAFT: String = "dbrow.sailing_boat_raft"
    const val DBROW_SKIFF: String = "dbrow.sailing_boat_skiff"
    const val DBROW_SLOOP: String = "dbrow.sailing_boat_sloop"

    val raft: SailingBoatRow by lazy { SailingBoatRow.getRow(DBROW_RAFT) }

    val skiff: SailingBoatRow by lazy { SailingBoatRow.getRow(DBROW_SKIFF) }

    val sloop: SailingBoatRow by lazy { SailingBoatRow.getRow(DBROW_SLOOP) }

    val all: List<SailingBoatRow> by lazy { listOf(raft, skiff, sloop) }

    fun byTypeId(typeId: Int): SailingBoatRow? = all.firstOrNull { it.typeId == typeId }

    val PURCHASE_PRICE_BY_TYPE_ID: Map<Int, Int> =
        mapOf(
            0 to 1_000,
            1 to 15_000,
            2 to 200_000,
        )

    fun purchasePrice(boat: SailingBoatRow): Int =
        PURCHASE_PRICE_BY_TYPE_ID[boat.typeId]
            ?: error("No purchase price authored for boat type_id ${boat.typeId}.")

    fun ownableBoatCount(baseSailingLvl: Int): Int =
        SailingConstants.BOAT_SLOT_UNLOCK_LEVELS.count { it <= baseSailingLvl }

    fun boatSlotRequirement(nth: Int): Int {
        require(nth in 1..SailingConstants.MAX_BOAT_SLOTS) { "Boat slot out of range: $nth" }
        return SailingConstants.BOAT_SLOT_UNLOCK_LEVELS[nth - 1]
    }

    data class BoatHotspot(
        val coords: CoordGrid,
        val hotspotIndex: Int,
        val shape: LocShape,
        val layoutRowId: Int,
        val angle: Int,
    )

    data class BoatStaticFacility(
        val coords: CoordGrid,
        val loc: ObjectServerType,
        val angle: Int,
        val shape: LocShape,
    )

    fun hotspots(boat: SailingBoatRow): List<BoatHotspot> =
        boat.hotspot.mapIndexed { index, tuple ->
            BoatHotspot(tuple.t0, index, LocShape[tuple.t2], tuple.t3.id, tuple.t1)
        }

    fun staticFacilities(boat: SailingBoatRow): List<BoatStaticFacility> =
        boat.staticFacility.map { BoatStaticFacility(it.t0, it.t1, it.t2, LocShape[it.t3]) }

    fun baseHull(boat: SailingBoatRow): SailingBoatHullRow =
        boat.hullOption.minBy { it.sailingRequirement }

    fun baseHullStats(boat: SailingBoatRow): SailingBoatFacilityStatsRow =
        baseHull(boat).facilityStats

    fun usableCargoCapacity(fitted: SailingBoatFacilityStatsRow?): Int =
        fitted?.boatCargoholdSize ?: 0

    fun assertCacheAnchors() {
        check(raft.levelRequired == 1) {
            "dbrow.sailing_boat_raft:level_required expected 1, got ${raft.levelRequired}."
        }
        check(skiff.levelRequired == 15) {
            "dbrow.sailing_boat_skiff:level_required expected 15, got ${skiff.levelRequired}."
        }
        check(sloop.levelRequired == 50) {
            "dbrow.sailing_boat_sloop:level_required expected 50, got ${sloop.levelRequired}."
        }

        check(SailingConstants.BOAT_SLOT_UNLOCK_LEVELS.take(3) == listOf(1, 15, 50)) {
            "BOAT_SLOT_UNLOCK_LEVELS no longer opens 1/15/50; re-check it against the cache."
        }

        val raftWoodenHull = baseHullStats(raft)
        check(raftWoodenHull.boatHpMax == 20) {
            "Raft wooden hull boat_hp_max expected 20, got ${raftWoodenHull.boatHpMax} " +
                "(row ${raftWoodenHull.rowId})."
        }
        check(raftWoodenHull.boatBasespeed == 192) {
            "Raft wooden hull boat_basespeed expected 192 (1.5 tiles/tick), got " +
                "${raftWoodenHull.boatBasespeed} (row ${raftWoodenHull.rowId})."
        }
        check(raftWoodenHull.boatSpeedcap == 320) {
            "Raft wooden hull boat_speedcap expected 320 (2.5 tiles/tick), got " +
                "${raftWoodenHull.boatSpeedcap} (row ${raftWoodenHull.rowId})."
        }

        check(raft.facilityAmount == 1) {
            "Raft facility_amount expected 1, got ${raft.facilityAmount}."
        }
        check(skiff.facilityAmount == 7) {
            "Skiff facility_amount expected 7, got ${skiff.facilityAmount}."
        }
        check(sloop.facilityAmount == 13) {
            "Sloop facility_amount expected 13 (the wiki's 11 is wrong), got " +
                "${sloop.facilityAmount}."
        }

        check(raft.retrievalCost == 250) {
            "Raft retrieval_cost expected 250, got ${raft.retrievalCost}."
        }
        check(skiff.retrievalCost == 3_750) {
            "Skiff retrieval_cost expected 3750 (research doc open question 7 - the wiki says " +
                "${SailingConstants.OQ7_SKIFF_RETRIEVAL_COST_WIKI}), got ${skiff.retrievalCost}."
        }
        check(sloop.retrievalCost == 50_000) {
            "Sloop retrieval_cost expected 50000, got ${sloop.retrievalCost}."
        }

        check(raft.crewCapacity == 0) { "Raft crew_capacity expected 0, got ${raft.crewCapacity}." }
        check(skiff.crewCapacity == 2) {
            "Skiff crew_capacity expected 2, got ${skiff.crewCapacity}."
        }
        check(sloop.crewCapacity == 5) {
            "Sloop crew_capacity expected 5, got ${sloop.crewCapacity}."
        }

        check(raft.keelOption.isEmpty()) {
            "Raft is expected to have no keel_option, got ${raft.keelOption.size} entries."
        }
        check(raft.trimOption.isEmpty()) {
            "Raft is expected to have no trim_option, got ${raft.trimOption.size} entries."
        }
    }
}
