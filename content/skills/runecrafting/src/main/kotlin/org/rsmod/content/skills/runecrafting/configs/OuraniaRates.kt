package org.rsmod.content.skills.runecrafting.configs

object OuraniaRates {
    data class RuneWeight(val rune: String, val weight: Int)

    const val WEIGHT_TOTAL = 10000

    fun weightsForLevel(level: Int): List<RuneWeight> {
        val bands = OuraniaRateData.bands
        return (bands.lastOrNull { level >= it.level } ?: bands.first()).weights
    }
}
