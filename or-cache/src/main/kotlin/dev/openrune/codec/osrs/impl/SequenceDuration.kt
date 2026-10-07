package dev.openrune.codec.osrs.impl

import kotlin.math.ceil

object SequenceDuration {
    const val CLIENT_CYCLE_MILLIS = 20
    const val GAME_TICK_MILLIS = 600
    const val CLIENT_CYCLES_PER_TICK = GAME_TICK_MILLIS / CLIENT_CYCLE_MILLIS

    /** Skeletal durations retain the server codec's truncation, including sub-tick zeroes. */
    fun skeletalTicks(rangeBegin: Int, rangeEnd: Int): Int =
        ((rangeEnd - rangeBegin) / CLIENT_CYCLES_PER_TICK.toDouble()).toInt()

    /** Replace the trailing run of delays over 30 cycles with one five-cycle buffer. */
    fun frameCycles(delays: List<Int>): Int {
        val validDelays = delays.dropLastWhile { it > CLIENT_CYCLES_PER_TICK }
        val buffer = if (validDelays.size != delays.size) 5 else 0
        return validDelays.sum() + buffer
    }

    fun frameTicks(delays: List<Int>): Int {
        val durationMillis = frameCycles(delays) * CLIENT_CYCLE_MILLIS
        return ceil(durationMillis / GAME_TICK_MILLIS.toDouble()).toInt()
    }
}
