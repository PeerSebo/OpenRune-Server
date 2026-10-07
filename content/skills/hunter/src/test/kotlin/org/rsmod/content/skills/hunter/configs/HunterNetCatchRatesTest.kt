package org.rsmod.content.skills.hunter.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class HunterNetCatchRatesTest {
    // Independent chart points: https://oldschool.runescape.wiki/w/Net_trap.
    @ParameterizedTest
    @CsvSource(
        "SWAMP, 29, 141", "SWAMP, 33, 154", "SWAMP, 50, 207", "SWAMP, 65, 254",
        "SWAMP, 66, 256", "SWAMP, 99, 256",
        "ORANGE, 47, 145", "ORANGE, 48, 147", "ORANGE, 70, 209", "ORANGE, 86, 253",
        "ORANGE, 87, 256", "ORANGE, 99, 256",
        "RED, 59, 143", "RED, 61, 148", "RED, 80, 194", "RED, 99, 241",
        "BLACK, 67, 144", "BLACK, 79, 170", "BLACK, 83, 178", "BLACK, 99, 213",
        "MOUNTAIN, 79, 170", "MOUNTAIN, 83, 179", "MOUNTAIN, 90, 194", "MOUNTAIN, 99, 213",
    )
    fun `net prey uses published catch chance`(colour: NetTrapColour, level: Int, expected: Int) {
        val prey = requireNotNull(HunterPreyTable.netTrapPrey(colour))
        assertEquals(expected, HunterCatchRates.chance(prey.rate, level))
    }

    @Test
    fun `chart entries take precedence over estimated endpoints`() {
        val rate = CatchRate(low = 0, high = 0, chart = mapOf(50 to 200))
        assertEquals(200, HunterCatchRates.chance(rate, 50))
    }

    @Test
    fun `inferred boosted continuation matches every available chart point`() {
        for (colour in NetTrapColour.entries) {
            val rate = HunterCatchRates.netTrap(colour)
            val continuation = rate.copy(chart = emptyMap())
            for ((level, chance) in rate.chart) {
                assertEquals(
                    chance,
                    HunterCatchRates.chance(continuation, level),
                    "$colour level $level",
                )
            }
        }
    }

    @ParameterizedTest
    @CsvSource("RED, 100, 243", "RED, 105, 256", "BLACK, 100, 215", "MOUNTAIN, 100, 215")
    fun `boosted levels use bounded inferred continuation`(
        colour: NetTrapColour,
        level: Int,
        expected: Int,
    ) {
        assertEquals(expected, HunterCatchRates.chance(HunterCatchRates.netTrap(colour), level))
    }

    @Test
    fun `chance stays bounded and monotonic from requirement through boosted levels`() {
        for (colour in NetTrapColour.entries) {
            val prey = requireNotNull(HunterPreyTable.netTrapPrey(colour))
            var previous = 0
            for (level in prey.levelReq..125) {
                val chance = HunterCatchRates.chance(prey.rate, level)
                assertTrue(chance in previous..256, "$colour level $level: $chance after $previous")
                previous = chance
            }
        }
    }

    @Test
    fun `existing non-net rates retain floor interpolation`() {
        assertEquals(107, HunterCatchRates.chance(CatchRate(low = 101, high = 420), 3))
        assertEquals(115, HunterCatchRates.chance(CatchRate(low = -78, high = 228), 63))
        assertEquals(0, HunterCatchRates.chance(CatchRate(low = -78, high = 228), 1))
        assertEquals(256, HunterCatchRates.chance(CatchRate(low = 101, high = 420), 99))
    }
}
