package org.rsmod.content.skills.hunter.configs

enum class NetPreyKind { BUTTERFLY, IMPLING }

data class NetPrey(
    val npcs: List<String>,
    val kind: NetPreyKind,
    val levelReq: Int,
    val xp: Double,
    val filledJar: String,
    val rate: CatchRate?,
    val enhancedRate: CatchRate? = rate?.copy(low = rate.low + 20, high = rate.high + 20),
    val barehandedBonus: Boolean = true,
    val needsJar: Boolean = false,
)

object NetPreyTable {
    val ALL: List<NetPrey> = buildList {
        add(
            NetPrey(
                npcs = listOf("npc.butterfly_ruby"),
                kind = NetPreyKind.BUTTERFLY,
                levelReq = 15,
                xp = 24.0,
                filledJar = "obj.butterfly_jar_ruby",
                rate = null,
            )
        )
        add(
            NetPrey(
                npcs = listOf("npc.butterfly_glacialis"),
                kind = NetPreyKind.BUTTERFLY,
                levelReq = 25,
                xp = 34.0,
                filledJar = "obj.butterfly_jar_glacialis",
                rate = null,
            )
        )
        add(
            NetPrey(
                npcs = listOf("npc.butterfly_snowy"),
                kind = NetPreyKind.BUTTERFLY,
                levelReq = 35,
                xp = 44.0,
                filledJar = "obj.butterfly_jar_snowy",
                rate = null,
            )
        )
        add(
            NetPrey(
                npcs = listOf("npc.butterfly_warlock"),
                kind = NetPreyKind.BUTTERFLY,
                levelReq = 45,
                xp = 54.0,
                filledJar = "obj.butterfly_jar_warlock",
                rate = CatchRate(21, 297, roundToNearest = true),
            )
        )

        add(NetPrey(listOf("npc.moth_sunlight"), NetPreyKind.BUTTERFLY, 65, 74.0,
            "obj.butterfly_jar_sunmoth", CatchRate(21, 297, roundToNearest = true), barehandedBonus = false))
        add(NetPrey(listOf("npc.moth_moonlight", "npc.moth_moonlight_low_wander"), NetPreyKind.BUTTERFLY, 75, 84.0,
            "obj.butterfly_jar_moonmoth", CatchRate(1, 277, roundToNearest = true),
            enhancedRate = CatchRate(21, 287, roundToNearest = true)))

        addImpling(index = 1, levelReq = 17, xp = 20.0, mazeXp = 18.0)
        addImpling(index = 2, levelReq = 22, xp = 22.0, mazeXp = 20.0)
        addImpling(index = 3, levelReq = 28, xp = 24.0, mazeXp = 22.0)
        addImpling(index = 4, levelReq = 36, xp = 27.0, mazeXp = 25.0)
        addImpling(index = 5, levelReq = 42, xp = 29.0, mazeXp = 27.0)
        addImpling(index = 6, levelReq = 50, xp = 32.0, mazeXp = 30.0)
        addImpling(index = 7, levelReq = 58, xp = 36.0, mazeXp = 34.0)
        addImpling(index = 8, levelReq = 65, xp = 216.0, mazeXp = 44.0)
        addImpling(index = 9, levelReq = 74, xp = 240.0, mazeXp = 50.0)
        addImpling(index = 10, levelReq = 83, xp = 300.0, mazeXp = 65.0)
        addImpling(index = 11, levelReq = 89, xp = 380.0, mazeXp = 80.0)
        add(NetPrey(
            listOf("johnny", "junior", "andy", "joey", "trouble", "hingy", "zolty", "neil", "yanny", "matty", "stace", "ian", "jamie", "damo", "xander", "steveo", "stewie")
                .map { "npc.ii_impling_type_12_$it" },
            NetPreyKind.IMPLING, 80, 280.0, "obj.ii_captured_impling_12", CatchRate(9, 142, roundToNearest = true),
        ))
    }

    private fun MutableList<NetPrey>.addImpling(index: Int, levelReq: Int, xp: Double, mazeXp: Double) {
        add(
            NetPrey(
                npcs = listOf("npc.ii_impling_type_$index"),
                kind = NetPreyKind.IMPLING,
                levelReq = levelReq,
                xp = xp,
                filledJar = "obj.ii_captured_impling_$index",
                rate = implingRate(index),
            )
        )
        add(last().copy(npcs = listOf("npc.ii_impling_type_${index}_maze"), xp = mazeXp, needsJar = true))
    }

    // Rounded fits reproduce published chart points; the native constants remain unknown.
    private fun implingRate(index: Int): CatchRate = when (index) {
        1 -> CatchRate(80, 403, roundToNearest = true)
        2 -> CatchRate(70, 352, roundToNearest = true)
        3 -> CatchRate(62, 326, roundToNearest = true)
        4 -> CatchRate(52, 303, roundToNearest = true)
        5 -> CatchRate(41, 276, roundToNearest = true)
        6 -> CatchRate(31, 251, roundToNearest = true)
        7 -> CatchRate(21, 201, roundToNearest = true)
        8 -> CatchRate(16, 178, roundToNearest = true)
        9 -> CatchRate(11, 152, roundToNearest = true)
        10 -> CatchRate(6, 126, roundToNearest = true)
        11 -> CatchRate(3, 101, roundToNearest = true)
        else -> error("Unknown impling tier: $index")
    }
}
