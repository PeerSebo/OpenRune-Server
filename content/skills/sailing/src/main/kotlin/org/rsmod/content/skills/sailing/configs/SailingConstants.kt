package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.player.protect.ProtectedAccess

object SailingConstants {
    const val SAILING_STAT: String = "stat.sailing"

    const val CONSTRUCTION_STAT: String = "stat.construction"

    const val QUEUE_BOARD: String = "queue.sailing_board"
    const val QUEUE_DISEMBARK: String = "queue.sailing_disembark"
    const val QUEUE_BUY_BOAT: String = "queue.sailing_buy_boat"
    const val QUEUE_BUILD_PART: String = "queue.sailing_build_part"
    const val QUEUE_BUILD_FACILITY: String = "queue.sailing_build_facility"
    const val QUEUE_CARGO_TRANSFER: String = "queue.sailing_cargo_transfer"
    const val QUEUE_SALVAGE: String = "queue.sailing_salvage"
    const val QUEUE_ESCAPE: String = "queue.sailing_escape"

    const val CONTENT_GANGPLANK: String = "content.sailing_gangplank"
    const val CONTENT_SHIPWRIGHT: String = "content.sailing_shipwright"
    const val CONTENT_NOTICE_BOARD: String = "content.sailing_notice_board"
    const val CONTENT_LEDGER_TABLE: String = "content.sailing_ledger_table"
    const val CONTENT_SHIPWRECK: String = "content.sailing_shipwreck"
    const val CONTENT_BUOY: String = "content.sailing_buoy"
    const val CONTENT_HELM: String = "content.sailing_helm"
    const val CONTENT_FACILITY_HOTSPOT: String = "content.sailing_facility_hotspot"

    const val INV_TRAWLING_NET: String = "inv.sailing_trawling_net"

    fun cargoHoldInv(slot: Int): String {
        require(slot in 1..MAX_BOAT_SLOTS) { "Boat slot out of range: $slot" }
        return "inv.sailing_boat_${slot}_cargohold"
    }

    const val MAX_BOAT_SLOTS: Int = 5

    val BOAT_SLOT_UNLOCK_LEVELS: IntArray = intArrayOf(1, 15, 50, 78, 91)

    val CREW_SLOT_UNLOCK_LEVELS: IntArray = intArrayOf(40, 55, 70, 85, 95)

    val TASK_SLOT_UNLOCK_LEVELS: IntArray = intArrayOf(1, 7, 28, 56, 84)

    const val BOUNTY_TASK_MIN_LEVEL: Int = 30

    // Speed uses 1/128 tile per tick.
    const val SPEED_FIXED_POINT_UNIT: Int = 128

    const val OQ2_SPEED_STEP_FINE_UNITS: Int = 64

    const val OQ3_TURN_ANGLE_UNITS_PER_TICK: Int = 128

    const val OQ4_FORWARD_SPEED_STEPS: Int = 2

    const val OQ4_REVERSE_SPEED_STEPS: Int = 1

    const val OQ5_TRIM_WINDOW_TICKS: Int = 20

    const val OQ5_TRIM_COOLDOWN_TICKS: Int = 50

    const val OQ6_BURST_STACK_CAP: Int = 15

    const val OQ6_BURST_DECAY_PER_STEP: Int = 2

    const val OQ6_BURST_DECAY_INTERVAL_TICKS: Int = 4

    const val OQ6_BURST_REFRESH_TICKS: Int = 15

    const val OQ7_SKIFF_RETRIEVAL_COST_WIKI: Int = 4_125

    // Default hazard period is provisional; specialized hazards override it.
    const val OQ8_HAZARD_DEFAULT_PERIOD_TICKS: Int = 5

    const val OQ8_PROFANE_WATERS_PERIOD_TICKS: Int = 12

    const val OQ8_SCALDING_SEAS_PERIOD_TICKS: Int = 1

    // Salvage success endpoints are placeholders; native probabilities are unknown.
    const val OQ9_SALVAGE_RATE_LOW: Int = 102

    const val OQ9_SALVAGE_RATE_HIGH: Int = 102

    const val OQ10_ROUND_TRIP_XP_MULTIPLIER: Int = 2

    const val OQ11_DOCK_COORDS_ARE_HAND_AUTHORED: Boolean = true

    const val OQ12_PART_CONSTRUCTION_XP_IS_WIKI_ONLY: Boolean = true

    const val OQ13_SHIPWRIGHT_FEES_ARE_WIKI_ONLY: Boolean = true

    const val OQ13_SHIPWRIGHT_MAX_TIER_INDEX: Int = 3

    const val OQ14_CREW_STAT_HELMSMANSHIP: Int = 1

    const val CREW_STAT_PRIVATEERING: Int = 2

    const val CREW_STAT_DECKHANDINESS: Int = 3

    const val OQ15_DOCK_ROW_COUNT: Int = 61

    const val OQ16_TARGET_IS_REV240_NOT_LIVE: Boolean = true
}

// Queue/callback additions decrement immediately; compensate with +1.
internal fun ProtectedAccess.sailingQueue(queue: String, cadenceTicks: Int, args: Any? = null) {
    weakQueue(queue, cadenceTicks + 1, args)
}
