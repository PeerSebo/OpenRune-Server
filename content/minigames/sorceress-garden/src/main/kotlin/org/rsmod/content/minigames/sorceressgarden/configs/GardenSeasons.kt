package org.rsmod.content.minigames.sorceressgarden.configs

import org.rsmod.map.CoordGrid

enum class GardenSeason {
    WINTER,
    SPRING,
    AUTUMN,
    SUMMER;

    private val data: GardenSeasonData.Season get() = GardenSeasonData.rows.getValue(name)

    val thievingLevel: Int get() = data.thievingLevel
    val doorLoc: String get() = data.doorLoc
    val catchEjectCoord: CoordGrid get() = data.catchEjectCoord
    val treeLoc: String get() = data.treeLoc
    val herbLoc: String get() = data.herbLoc
    val fruitObj: String get() = data.fruitObj
    val juiceObj: String get() = data.juiceObj
    val sqirkCount: Int get() = data.sqirkCount
    val farmingXp: Double get() = data.farmingXp
    val osmanXp: Double get() = data.osmanXp
    val thievingBoost: Int get() = data.thievingBoost
    val runEnergyPercent: Int get() = data.runEnergyPercent
    val treeAnchor: CoordGrid get() = data.treeAnchor
    val herbAnchor: CoordGrid get() = data.herbAnchor

    companion object {
        val byFruit: Map<String, GardenSeason> by lazy { entries.associateBy { it.fruitObj } }
        val byJuice: Map<String, GardenSeason> by lazy { entries.associateBy { it.juiceObj } }
    }
}
