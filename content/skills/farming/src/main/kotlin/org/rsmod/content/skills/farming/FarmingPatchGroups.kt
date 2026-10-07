package org.rsmod.content.skills.farming

import org.rsmod.content.skills.farming.configs.FarmingServiceData

internal object FarmingPatchGroups {
    internal data class Farm(val flower: String, val allotments: List<String>)

    private val byPatch by lazy {
        buildMap {
            for (farm in FarmingServiceData.farms) {
                put(farm.flower, farm)
                for (allotment in farm.allotments) put(allotment, farm)
            }
        }
    }

    fun forPatch(key: String): Farm? = byPatch[key]
}
