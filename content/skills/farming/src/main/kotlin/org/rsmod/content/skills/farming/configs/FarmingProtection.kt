package org.rsmod.content.skills.farming.configs

object FarmingProtection {
    val COMPANION_FLOWERS: Map<String, Set<String>> by lazy {
        FarmingServiceData.companions
            .filter { !it.protectsAll && it.crop != FarmingCrops.SCARECROW_ID }
            .associate { it.crop to it.allotments }
    }

    val PROTECTS_ALL_ALLOTMENTS: String
        get() = FarmingServiceData.companions.single { it.protectsAll }.crop

    const val SCARECROW_OBJ: String = "obj.scarecrow_complete"

    val SCARECROW_PROTECTS: Set<String>
        get() = FarmingServiceData.companions.single { it.crop == FarmingCrops.SCARECROW_ID }.allotments

    fun flowerProtects(flowerId: String, allotmentId: String): Boolean =
        flowerId == PROTECTS_ALL_ALLOTMENTS ||
            COMPANION_FLOWERS[flowerId]?.contains(allotmentId) == true
}
