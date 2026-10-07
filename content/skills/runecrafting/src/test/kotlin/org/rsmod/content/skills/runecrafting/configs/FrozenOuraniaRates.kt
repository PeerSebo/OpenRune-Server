package org.rsmod.content.skills.runecrafting.configs

// Literal catalogue frozen before the cache extraction on 2026-09-19.
internal object FrozenOuraniaRates {
    data class RuneWeight(val rune: String, val weight: Int)

    const val WEIGHT_TOTAL = 10000

    private val LEVEL_1_9 =
        listOf(
            RuneWeight("obj.airrune", 5000),
            RuneWeight("obj.mindrune", 2500),
            RuneWeight("obj.waterrune", 1200),
            RuneWeight("obj.earthrune", 600),
            RuneWeight("obj.firerune", 300),
            RuneWeight("obj.bodyrune", 150),
            RuneWeight("obj.cosmicrune", 85),
            RuneWeight("obj.chaosrune", 60),
            RuneWeight("obj.astralrune", 45),
            RuneWeight("obj.naturerune", 30),
            RuneWeight("obj.lawrune", 15),
            RuneWeight("obj.deathrune", 8),
            RuneWeight("obj.bloodrune", 5),
            RuneWeight("obj.soulrune", 2),
        )

    private val LEVEL_10_19 =
        listOf(
            RuneWeight("obj.airrune", 1500),
            RuneWeight("obj.mindrune", 1800),
            RuneWeight("obj.waterrune", 2100),
            RuneWeight("obj.earthrune", 2400),
            RuneWeight("obj.firerune", 1200),
            RuneWeight("obj.bodyrune", 600),
            RuneWeight("obj.cosmicrune", 175),
            RuneWeight("obj.chaosrune", 80),
            RuneWeight("obj.astralrune", 60),
            RuneWeight("obj.naturerune", 40),
            RuneWeight("obj.lawrune", 24),
            RuneWeight("obj.deathrune", 12),
            RuneWeight("obj.bloodrune", 6),
            RuneWeight("obj.soulrune", 3),
        )

    private val LEVEL_20_29 =
        listOf(
            RuneWeight("obj.airrune", 1200),
            RuneWeight("obj.mindrune", 1300),
            RuneWeight("obj.waterrune", 1350),
            RuneWeight("obj.earthrune", 1400),
            RuneWeight("obj.firerune", 1500),
            RuneWeight("obj.bodyrune", 1600),
            RuneWeight("obj.cosmicrune", 800),
            RuneWeight("obj.chaosrune", 420),
            RuneWeight("obj.astralrune", 210),
            RuneWeight("obj.naturerune", 110),
            RuneWeight("obj.lawrune", 55),
            RuneWeight("obj.deathrune", 32),
            RuneWeight("obj.bloodrune", 15),
            RuneWeight("obj.soulrune", 8),
        )

    private val LEVEL_30_39 =
        listOf(
            RuneWeight("obj.airrune", 700),
            RuneWeight("obj.mindrune", 800),
            RuneWeight("obj.waterrune", 900),
            RuneWeight("obj.earthrune", 1100),
            RuneWeight("obj.firerune", 1200),
            RuneWeight("obj.bodyrune", 1300),
            RuneWeight("obj.cosmicrune", 2000),
            RuneWeight("obj.chaosrune", 1000),
            RuneWeight("obj.astralrune", 500),
            RuneWeight("obj.naturerune", 250),
            RuneWeight("obj.lawrune", 130),
            RuneWeight("obj.deathrune", 60),
            RuneWeight("obj.bloodrune", 40),
            RuneWeight("obj.soulrune", 20),
        )

    private val LEVEL_40_49 =
        listOf(
            RuneWeight("obj.airrune", 600),
            RuneWeight("obj.mindrune", 650),
            RuneWeight("obj.waterrune", 700),
            RuneWeight("obj.earthrune", 750),
            RuneWeight("obj.firerune", 800),
            RuneWeight("obj.bodyrune", 1000),
            RuneWeight("obj.cosmicrune", 1500),
            RuneWeight("obj.chaosrune", 2000),
            RuneWeight("obj.astralrune", 1000),
            RuneWeight("obj.naturerune", 500),
            RuneWeight("obj.lawrune", 260),
            RuneWeight("obj.deathrune", 120),
            RuneWeight("obj.bloodrune", 80),
            RuneWeight("obj.soulrune", 40),
        )

    private val LEVEL_50_59 =
        listOf(
            RuneWeight("obj.airrune", 500),
            RuneWeight("obj.mindrune", 550),
            RuneWeight("obj.waterrune", 600),
            RuneWeight("obj.earthrune", 650),
            RuneWeight("obj.firerune", 700),
            RuneWeight("obj.bodyrune", 750),
            RuneWeight("obj.cosmicrune", 1000),
            RuneWeight("obj.chaosrune", 1100),
            RuneWeight("obj.astralrune", 1500),
            RuneWeight("obj.naturerune", 1350),
            RuneWeight("obj.lawrune", 700),
            RuneWeight("obj.deathrune", 350),
            RuneWeight("obj.bloodrune", 170),
            RuneWeight("obj.soulrune", 80),
        )

    private val LEVEL_60_69 =
        listOf(
            RuneWeight("obj.airrune", 450),
            RuneWeight("obj.mindrune", 500),
            RuneWeight("obj.waterrune", 550),
            RuneWeight("obj.earthrune", 600),
            RuneWeight("obj.firerune", 700),
            RuneWeight("obj.bodyrune", 750),
            RuneWeight("obj.cosmicrune", 950),
            RuneWeight("obj.chaosrune", 1050),
            RuneWeight("obj.astralrune", 1400),
            RuneWeight("obj.naturerune", 1550),
            RuneWeight("obj.lawrune", 800),
            RuneWeight("obj.deathrune", 400),
            RuneWeight("obj.bloodrune", 200),
            RuneWeight("obj.soulrune", 100),
        )

    private val LEVEL_70_79 =
        listOf(
            RuneWeight("obj.airrune", 300),
            RuneWeight("obj.mindrune", 300),
            RuneWeight("obj.waterrune", 300),
            RuneWeight("obj.earthrune", 400),
            RuneWeight("obj.firerune", 400),
            RuneWeight("obj.bodyrune", 500),
            RuneWeight("obj.cosmicrune", 700),
            RuneWeight("obj.chaosrune", 900),
            RuneWeight("obj.astralrune", 1200),
            RuneWeight("obj.naturerune", 1500),
            RuneWeight("obj.lawrune", 1800),
            RuneWeight("obj.deathrune", 1000),
            RuneWeight("obj.bloodrune", 500),
            RuneWeight("obj.soulrune", 200),
        )

    private val LEVEL_80_89 =
        listOf(
            RuneWeight("obj.airrune", 200),
            RuneWeight("obj.mindrune", 200),
            RuneWeight("obj.waterrune", 300),
            RuneWeight("obj.earthrune", 400),
            RuneWeight("obj.firerune", 500),
            RuneWeight("obj.bodyrune", 600),
            RuneWeight("obj.cosmicrune", 700),
            RuneWeight("obj.chaosrune", 800),
            RuneWeight("obj.astralrune", 1050),
            RuneWeight("obj.naturerune", 1350),
            RuneWeight("obj.lawrune", 1450),
            RuneWeight("obj.deathrune", 1450),
            RuneWeight("obj.bloodrune", 600),
            RuneWeight("obj.soulrune", 400),
        )

    private val LEVEL_90_98 =
        listOf(
            RuneWeight("obj.airrune", 100),
            RuneWeight("obj.mindrune", 100),
            RuneWeight("obj.waterrune", 200),
            RuneWeight("obj.earthrune", 300),
            RuneWeight("obj.firerune", 400),
            RuneWeight("obj.bodyrune", 500),
            RuneWeight("obj.cosmicrune", 600),
            RuneWeight("obj.chaosrune", 700),
            RuneWeight("obj.astralrune", 1000),
            RuneWeight("obj.naturerune", 1350),
            RuneWeight("obj.lawrune", 1450),
            RuneWeight("obj.deathrune", 1650),
            RuneWeight("obj.bloodrune", 1000),
            RuneWeight("obj.soulrune", 650),
        )

    private val LEVEL_99_PLUS =
        listOf(
            RuneWeight("obj.airrune", 100),
            RuneWeight("obj.mindrune", 100),
            RuneWeight("obj.waterrune", 200),
            RuneWeight("obj.earthrune", 300),
            RuneWeight("obj.firerune", 300),
            RuneWeight("obj.bodyrune", 400),
            RuneWeight("obj.cosmicrune", 500),
            RuneWeight("obj.chaosrune", 600),
            RuneWeight("obj.astralrune", 950),
            RuneWeight("obj.naturerune", 1350),
            RuneWeight("obj.lawrune", 1450),
            RuneWeight("obj.deathrune", 1550),
            RuneWeight("obj.bloodrune", 1300),
            RuneWeight("obj.soulrune", 900),
        )

    private val BAND_FLOORS = intArrayOf(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99)

    private val BANDS: List<List<RuneWeight>> =
        listOf(
            LEVEL_1_9,
            LEVEL_10_19,
            LEVEL_20_29,
            LEVEL_30_39,
            LEVEL_40_49,
            LEVEL_50_59,
            LEVEL_60_69,
            LEVEL_70_79,
            LEVEL_80_89,
            LEVEL_90_98,
            LEVEL_99_PLUS,
        )

    fun weightsForLevel(level: Int): List<RuneWeight> {
        var index = 0
        for (i in BAND_FLOORS.indices) {
            if (level >= BAND_FLOORS[i]) index = i
        }
        return BANDS[index]
    }
}
