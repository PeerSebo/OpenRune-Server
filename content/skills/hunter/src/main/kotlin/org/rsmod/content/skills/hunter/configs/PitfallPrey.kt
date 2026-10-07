package org.rsmod.content.skills.hunter.configs

internal enum class PitfallPrey(
    val npc: String, val level: Int, val xp: Double, val maxHit: Int,
    val meat: String, val fur: String, val damagedFur: String? = null, val antler: String? = null,
) {
    LARUPIA("npc.hunting_jaguar", 31, 180.0, 5, "obj.hunting_larupia_meat", "obj.hunting_fur_jaguar_perfect", "obj.hunting_fur_jaguar_shabby"),
    GRAAHK("npc.hunting_leopard", 41, 240.0, 7, "obj.hunting_graahk_meat", "obj.hunting_fur_leopard_perfect", "obj.hunting_fur_leopard_shabby"),
    KYATT("npc.hunting_snow_tiger", 55, 300.0, 7, "obj.hunting_kyatt_meat", "obj.hunting_fur_tiger_perfect", "obj.hunting_fur_tiger_shabby"),
    SUNLIGHT("npc.sunlight_antelope", 72, 380.0, 5, "obj.hunting_antelopesun_meat", "obj.hunting_antelopesun_fur", antler = "obj.hunting_antelopesun_horn"),
    MOONLIGHT("npc.moonlight_antelope", 91, 450.0, 5, "obj.hunting_antelopemoon_meat", "obj.hunting_antelopemoon_fur", antler = "obj.hunting_antelopemoon_horn");

    val guaranteed: Boolean get() = antler != null
    val respawnTicks: Int get() = if (guaranteed) 45 else 10

    // TODO: verify classic big-cat catch rates; their curve is provisional.
    fun catchPercent(hunterLevel: Int, spear: Boolean): Int {
        if (hunterLevel < level) return 0
        if (guaranteed) return 100
        val progress = (hunterLevel - level).coerceIn(0, 99 - level)
        return (60 + 30 * progress / (99 - level) + if (spear) 5 else 0).coerceAtMost(100)
    }

    // TODO: verify pitfall level scaling; this curve is provisional.
    fun perfectFurPercent(hunterLevel: Int): Int =
        if (guaranteed) 100 else 50 + 40 * (hunterLevel - level).coerceIn(0, 99 - level) / (99 - level)
}

internal data class PitfallSpot(val number: Int, val prey: PitfallPrey) {
    val loc: String get() = "loc.hunting_pitfall_$number"
    val varbit: String get() = "varbit.hunt_pitfall_state$number"

    companion object {
        val ALL = (1..25).map { n ->
            PitfallSpot(n, when (n) {
                in 1..6 -> PitfallPrey.KYATT
                in 7..11 -> PitfallPrey.LARUPIA
                in 12..16 -> PitfallPrey.GRAAHK
                in 17..21 -> PitfallPrey.SUNLIGHT
                else -> PitfallPrey.MOONLIGHT
            })
        }
    }
}
