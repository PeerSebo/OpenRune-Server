package org.rsmod.content.skills.farming.configs

import org.rsmod.api.farming.FarmingCrop
import org.rsmod.api.farming.FarmingCropVisuals
import org.rsmod.api.farming.FarmingPatchType

data class FarmingCropDef(
    val id: String,
    val type: FarmingPatchType,
    val seed: String,
    val produce: String?,
    val levelReq: Int,
    val deferredPlantXp: Double,
    val harvestXp: Double,
    val growthCycles: Int,
    val cycleMinutes: Int,
    val seedsPerPatch: Int,
    val visuals: FarmingCropVisuals,
    val protection: ProtectionPayment? = null,
    val checkHealthXp: Double = 0.0,
    val sapling: String? = null,
    val baseLives: Int = 0,
    val produceCapacity: Int = 0,
    val produceRegrowMinutes: Int = 0,
    val diseaseChance: Int,
    val chanceToSave: ChanceToSave? = null,
    val potSeed: String? = null,
    val potSeedWatered: String? = null,
    val placedNotGrown: Boolean = false,
    val waterable: Boolean? = null,
    val hesporiSeedDenominator: Int? = null,
) {
    val plantedObj: String
        get() = sapling ?: seed

    val growthMinutes: Int
        get() = growthCycles * cycleMinutes

    fun toApiCrop(): FarmingCrop =
        FarmingCrop(
            id = id,
            patchType = type,
            growthStages = growthCycles + 1,
            osrsCycleMinutes = cycleMinutes,
            visuals = visuals,
            diseaseChance = diseaseChance,
            waterable = waterable ?: type.waterable,
            baseLives = baseLives,
            produceCapacity = produceCapacity,
            osrsProduceRegrowMinutes = produceRegrowMinutes,
        )
}

data class ChanceToSave(val low: Int, val high: Int) {
    fun atLevel(level: Int): Int {
        val f = level.coerceIn(1, 99)
        val interpolated = (low * (99 - f) + high * (f - 1)) / 98.0
        return 1 + Math.floor(interpolated + 0.5).toInt()
    }
}

object FarmingCrops {
    const val SCARECROW_ID: String = "scarecrow"

    val ALL: List<FarmingCropDef>
        get() = FarmingCropData.crops

    private val BY_ID: Map<String, FarmingCropDef> by lazy { ALL.associateBy { it.id } }

    private val BY_PLANTED: Map<String, FarmingCropDef> by lazy { ALL.associateBy { it.plantedObj } }

    fun byId(id: String?): FarmingCropDef? = id?.let(BY_ID::get)

    fun byPlantedObj(obj: String): FarmingCropDef? = BY_PLANTED[obj]
}
