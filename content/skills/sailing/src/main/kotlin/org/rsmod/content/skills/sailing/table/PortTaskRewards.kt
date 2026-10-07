package org.rsmod.content.skills.sailing.table

import dev.openrune.rscm.RSCM.asRSCM
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.rsmod.api.table.PortTaskRow
import org.rsmod.api.table.sailing.SailingDockRow
import org.rsmod.content.skills.sailing.configs.SailingConstants

object PortTaskRewards {

    const val TASK_TYPE_COURIER: Int = 0

    const val TASK_TYPE_BOUNTY: Int = 1

    // Approximate port positions estimate XP only; never use them as landing coordinates.
    data class PortAnchor(val x: Int, val z: Int, val cacheDerived: Boolean)

    fun anchorOf(dock: SailingDockRow?): PortAnchor? = dock?.let {
        PortTaskRewardData.packed.anchors[it.rowId]
    }

    const val COURIER_XP_REFERENCE_XP: Int = 78

    const val COURIER_XP_REFERENCE_DISTANCE: Double = 232.0

    // TODO: replace the fitted courier distance curve with exact rewards.
    const val COURIER_XP_DISTANCE_EXPONENT: Double = 3.136

    // TODO: replace this level-only fallback with exact rewards for unanchored ports.
    const val COURIER_XP_LEVEL_EXPONENT: Double = 1.11

    const val COURIER_XP_MIN: Int = 10

    const val COURIER_XP_MAX: Int = 11_519

    fun isBounty(row: PortTaskRow): Boolean = row.taskType == TASK_TYPE_BOUNTY

    fun xpFor(row: PortTaskRow): Int = if (isBounty(row)) bountyXp(row) else courierXp(row)

    fun coinsFor(row: PortTaskRow): Int =
        if (isBounty(row)) 0 else PortTaskRewardData.packed.couriers[row.rowId]?.coins ?: courierXp(row)

    fun bountyXp(row: PortTaskRow): Int {
        val target = row.bountyTargetAlive ?: return 0
        return PortTaskRewardData.packed.bounties[target.id] ?: 0
    }

    fun courierXp(row: PortTaskRow): Int {
        PortTaskRewardData.packed.couriers[row.rowId]?.let {
            return it.xp
        }

        val legs = legsOf(row)
        if (legs.isEmpty()) return COURIER_XP_MIN

        val perLeg = legs.map { (from, to) -> legDistance(from, to) }
        val total =
            if (perLeg.any { it == null }) {
                levelFallbackXp(row.levelRequired) * legs.size
            } else {
                perLeg.sumOf { xpFromDistance(it ?: 0.0) }
            }
        return total.coerceIn(COURIER_XP_MIN, COURIER_XP_MAX)
    }

    fun legsOf(row: PortTaskRow): List<Pair<SailingDockRow, SailingDockRow>> {
        val end = row.endingPort
        val cargo = row.cargoPort ?: row.startingPort ?: end
        val start = row.startingPort ?: cargo
        val legs = mutableListOf<Pair<SailingDockRow, SailingDockRow>>()
        if (start.rowId != cargo.rowId) legs += start to cargo
        if (cargo.rowId != end.rowId) legs += cargo to end
        return legs
    }

    fun isRoundTrip(row: PortTaskRow): Boolean {
        val start = row.startingPort ?: return false
        val cargo = row.cargoPort ?: return false
        return start.rowId == row.endingPort.rowId && cargo.rowId != start.rowId
    }

    private fun legDistance(from: SailingDockRow, to: SailingDockRow): Double? {
        val a = anchorOf(from) ?: return null
        val b = anchorOf(to) ?: return null
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz)
    }

    fun xpFromDistance(tiles: Double): Int {
        if (tiles <= 0.0) return 0
        val ratio = tiles / COURIER_XP_REFERENCE_DISTANCE
        val xp = COURIER_XP_REFERENCE_XP * ratio.pow(COURIER_XP_DISTANCE_EXPONENT)
        return xp.roundToInt().coerceAtLeast(0)
    }

    fun levelFallbackXp(levelRequired: Int): Int {
        val level = levelRequired.coerceAtLeast(1).toDouble()
        val xp = COURIER_XP_REFERENCE_XP * level.pow(COURIER_XP_LEVEL_EXPONENT)
        return xp.roundToInt().coerceAtLeast(COURIER_XP_MIN)
    }

    fun assertCacheAnchors() {
        val anchors = PortTaskRewardData.packed.anchors
        check("dbrow.sailing_dock_port_sarim".asRSCM() in anchors &&
            "dbrow.sailing_dock_the_pandemonium".asRSCM() in anchors) {
            "Sailing reward anchors must include Port Sarim and The Pandemonium."
        }

        val oneWay = xpFromDistance(COURIER_XP_REFERENCE_DISTANCE)
        val roundTrip = oneWay * 2
        check(roundTrip == oneWay * SailingConstants.OQ10_ROUND_TRIP_XP_MULTIPLIER) {
            "SailingConstants.OQ10_ROUND_TRIP_XP_MULTIPLIER is " +
                "${SailingConstants.OQ10_ROUND_TRIP_XP_MULTIPLIER}, but courierXp pays a round " +
                "trip as two equal legs, i.e. exactly double. The two rules have drifted apart."
        }
        check(oneWay == COURIER_XP_REFERENCE_XP) {
            "The distance curve no longer reproduces its own calibration point: " +
                "xpFromDistance($COURIER_XP_REFERENCE_DISTANCE) returned $oneWay, expected " +
                "$COURIER_XP_REFERENCE_XP."
        }
    }
}
