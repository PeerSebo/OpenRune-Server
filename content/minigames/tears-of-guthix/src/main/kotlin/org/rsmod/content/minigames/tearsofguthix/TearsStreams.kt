package org.rsmod.content.minigames.tearsofguthix

import org.rsmod.api.random.GameRandom

internal class TearsStreams(private val random: GameRandom, private val period: Int) {
    private val slots = IntArray(6) { it }
    private val sequence = (0..5).toMutableList().apply {
        for (index in lastIndex downTo 1) {
            val other = random.of(index + 1)
            val previous = this[index]
            this[index] = this[other]
            this[other] = previous
        }
    }
    private var lastCycle = Int.MIN_VALUE

    fun colour(wall: Int): Int = slots.indexOf(wall).let { if (it < 0) 0 else if (it < 3) -1 else 1 }

    fun advance(cycle: Int): Set<Int> {
        if (cycle == lastCycle) return emptySet()
        lastCycle = cycle
        val phase = Math.floorMod(cycle, period)
        if (phase >= sequence.size) return emptySet()
        val stream = sequence[phase]
        val previous = slots[stream]
        val available = (0..8).filter { it !in slots }
        slots[stream] = random.pick(available)
        return setOf(previous, slots[stream])
    }
}
