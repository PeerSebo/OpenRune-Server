package org.rsmod.api.farming

/**
 * Crop ids are persistent keys. Visual anchors must match this crop's cache blocks; growth and
 * produce-regrowth clocks are independent.
 */
public data class FarmingCrop(
    public val id: String,
    public val patchType: FarmingPatchType,
    public val growthStages: Int,
    public val osrsCycleMinutes: Int,
    public val visuals: FarmingCropVisuals,
    public val diseaseChance: Int = 0,
    public val diseaseChanceByStage: Map<Int, Int> = emptyMap(),
    public val waterable: Boolean = patchType.waterable,
    public val baseLives: Int = 0,
    public val produceCapacity: Int = 0,
    public val osrsProduceRegrowMinutes: Int = 0,
    public val checkHealth: Boolean = patchType.checkHealth,
) {
    public val cycleMinutes: Int
        get() = maxOf(1, osrsCycleMinutes / FarmingConstants.GROWTH_SPEED_MULTIPLIER)

    /** Preserve zero as the no-regrowth sentinel when scaling durations. */
    public val produceRegrowMinutes: Int
        get() =
            if (osrsProduceRegrowMinutes <= 0) {
                0
            } else {
                maxOf(1, osrsProduceRegrowMinutes / FarmingConstants.GROWTH_SPEED_MULTIPLIER)
            }

    init {
        require(id.isNotBlank()) { "Farming crop id must not be blank." }
        require(growthStages >= 1) { "Farming crop '$id' needs at least one growth stage." }
        require(osrsCycleMinutes >= 1) { "Farming crop '$id' needs a positive cycle length." }
        require(cycleMinutes >= 1) {
            "Farming crop '$id' scaled cycle length must stay positive (osrs=$osrsCycleMinutes)."
        }
        require(diseaseChance in 0..FarmingConstants.DISEASE_DENOMINATOR) {
            "Farming crop '$id' disease chance must be 0..${FarmingConstants.DISEASE_DENOMINATOR}."
        }
        require(produceCapacity >= 0) { "Farming crop '$id' produce capacity cannot be negative." }
        require(produceCapacity == 0 || produceRegrowMinutes >= 1) {
            "Farming crop '$id' bears produce but has no regrow interval."
        }
    }

    public val grownStage: Int
        get() = growthStages - 1

    public val canBeDiseased: Boolean
        get() = visuals.diseased != null && (diseaseChance > 0 || diseaseChanceByStage.isNotEmpty())

    public val canDie: Boolean
        get() = visuals.dead != null

    public fun diseaseChanceAt(stage: Int): Int = diseaseChanceByStage[stage] ?: diseaseChance
}
