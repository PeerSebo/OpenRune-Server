package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

data class MarkSpawnConfig(
    val chanceNumerator: Int,
    val chanceDenominator: Int,
    val tiles: List<CoordGrid>,
    val overlevelPenaltyApplies: Boolean = true,
)
