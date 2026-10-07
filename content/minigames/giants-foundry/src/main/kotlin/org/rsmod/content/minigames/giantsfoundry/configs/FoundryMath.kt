package org.rsmod.content.minigames.giantsfoundry.configs

object FoundryMath {
    const val CRUCIBLE_CAPACITY = 28
    const val COMPLETE = 1000

    fun alloyScore(counts: List<Int>): Int {
        require(counts.size == 6 && counts.all { it in 0..CRUCIBLE_CAPACITY })
        require(counts.sum() == CRUCIBLE_CAPACITY)
        val metals = counts.indices.sortedWith(compareByDescending<Int> { counts[it] }.thenByDescending { it })
        val first = metals[0].let { (it + 1) * counts[it] * 10 }
        val second = metals[1].let { (it + 1) * counts[it] * 10 }
        return (first * 10 / 28 + second * 10 / 28 + first * second / (28 * 28)) / 10
    }

    fun sections(difficulty: Int): Int = when {
        difficulty < 20 -> 3
        difficulty < 60 -> 4
        difficulty < 90 -> 5
        difficulty < 120 -> 6
        else -> 7
    }

    fun section(completion: Int, difficulty: Int): Int =
        completion.coerceIn(0, COMPLETE) * sections(difficulty) / COMPLETE

    fun temperatureRange(station: FoundryStation, difficulty: Int): IntRange {
        require(difficulty in 0..130)
        val third = COMPLETE / 3
        val width = third - difficulty * (third - third / 2) / 130
        val low = third / 2 + third * (2 - station.ordinal) - width / 2
        return low..low + width
    }

    fun experience(quality: Int): Int {
        require(quality in 0..205)
        return (quality * quality / 73 + quality * 3 / 2 + 1) * 30
    }

    fun coins(quality: Int): Int = experience(quality) * 2
}

enum class FoundryStation(val cycles: Int, val progress: Int, val heat: Int, val xp: Double) {
    HAMMER(5, 20, -25, 4.5),
    GRINDSTONE(2, 10, 15, 3.2),
    POLISHING_WHEEL(2, 10, -17, 3.3),
}
