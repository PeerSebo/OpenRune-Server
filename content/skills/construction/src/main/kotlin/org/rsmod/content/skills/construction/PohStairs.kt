package org.rsmod.content.skills.construction

object PohStairs {
    const val STAIR_SPACE_NAME = "Stair Space"

    data class Mirror(val builtLoc: String, val materials: List<Pair<String, Int>> = emptyList())

    val MIRRORS: Map<String, Mirror> =
        mapOf(
            "loc.poh_stairs_3" to Mirror("loc.poh_stairstop_3"),
            "loc.poh_stairs_4" to Mirror("loc.poh_stairstop_4"),
            "loc.poh_stairs_5" to Mirror("loc.poh_stairstop_5"),
            "loc.poh_stairstop_3" to Mirror("loc.poh_stairs_3"),
            "loc.poh_stairstop_4" to Mirror("loc.poh_stairs_4"),
            "loc.poh_stairstop_5" to Mirror("loc.poh_stairs_5"),
            "loc.poh_spiralstairs" to
                Mirror(
                    "loc.poh_spiralstairs",
                    listOf("obj.plank_teak" to 10, "obj.limestonebrick" to 7),
                ),
            "loc.poh_spiralstairs_2" to
                Mirror(
                    "loc.poh_spiralstairs_2",
                    listOf("obj.plank_teak" to 10, "obj.marble_block" to 7),
                ),
        )

    fun linkedDirections(builtLoc: String): List<Int> =
        when (builtLoc) {
            "loc.poh_stairs_3",
            "loc.poh_stairs_4",
            "loc.poh_stairs_5" -> listOf(1)
            "loc.poh_stairstop_3",
            "loc.poh_stairstop_4",
            "loc.poh_stairstop_5" -> listOf(-1)
            "loc.poh_spiralstairs",
            "loc.poh_spiralstairs_2" -> listOf(-1, 1)
            else -> emptyList()
        }
}
