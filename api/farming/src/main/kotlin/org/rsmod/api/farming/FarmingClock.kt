package org.rsmod.api.farming

/**
 * Growth uses aligned wall-clock boundaries so offline time and restarts advance crops. Never
 * reset an anchor to an unaligned observation time.
 */
public object FarmingClock {
    public fun currentMinute(): Long =
        System.currentTimeMillis() / FarmingConstants.MILLIS_PER_MINUTE

    public fun boundaryAtOrBefore(minute: Long, cycleMinutes: Int): Long {
        require(cycleMinutes >= 1) { "Cycle length must be positive: $cycleMinutes" }
        val cycle = cycleMinutes.toLong()
        return minute - Math.floorMod(minute, cycle)
    }

    public fun nextBoundaryAfter(minute: Long, cycleMinutes: Int): Long =
        boundaryAtOrBefore(minute, cycleMinutes) + cycleMinutes
}
