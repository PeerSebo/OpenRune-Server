package org.rsmod.content.minigames.giantsfoundry.configs

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FoundryMathTest {
    @Test
    fun `all six pure metals retain their intrinsic scores`() {
        for (metal in 0..5) {
            assertEquals((metal + 1) * 10, FoundryMath.alloyScore(List(6) { if (it == metal) 28 else 0 }))
        }
    }

    @Test
    fun `alloys use the two most abundant metals with higher tier breaking ties`() {
        assertEquals(40, FoundryMath.alloyScore(listOf(0, 14, 14, 0, 0, 0)))
        assertEquals(65, FoundryMath.alloyScore(listOf(0, 0, 14, 14, 0, 0)))
        assertEquals(95, FoundryMath.alloyScore(listOf(0, 0, 0, 14, 14, 0)))
        assertEquals(130, FoundryMath.alloyScore(listOf(0, 0, 0, 0, 14, 14)))
        assertEquals(59, FoundryMath.alloyScore(listOf(0, 0, 19, 9, 0, 0)))
        assertEquals(89, FoundryMath.alloyScore(listOf(0, 0, 0, 18, 10, 0)))
        assertEquals(118, FoundryMath.alloyScore(listOf(0, 0, 0, 0, 19, 9)))
        assertEquals(25, FoundryMath.alloyScore(listOf(7, 7, 7, 7, 0, 0)))
    }

    @Test
    fun `incomplete and overflowing crucibles cannot be scored`() {
        assertThrows(IllegalArgumentException::class.java) { FoundryMath.alloyScore(listOf(0, 0, 0, 0, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { FoundryMath.alloyScore(listOf(28, 1, 0, 0, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { FoundryMath.alloyScore(listOf(28)) }
        assertThrows(IllegalArgumentException::class.java) { FoundryMath.alloyScore(listOf(29, -1, 0, 0, 0, 0)) }
    }

    @Test
    fun `refinement section boundaries match native HUD integer scaling`() {
        for ((difficulty, sections) in listOf(0 to 3, 19 to 3, 20 to 4, 59 to 4, 60 to 5, 89 to 5, 90 to 6, 119 to 6, 120 to 7, 130 to 7)) {
            assertEquals(sections, FoundryMath.sections(difficulty))
            for (section in 1 until sections) {
                val threshold = (section * 1000 + sections - 1) / sections
                assertEquals(section - 1, FoundryMath.section(threshold - 1, difficulty))
                assertEquals(section, FoundryMath.section(threshold, difficulty))
            }
            assertEquals(sections, FoundryMath.section(1000, difficulty))
        }
    }

    @Test
    fun `temperature bounds preserve the native inclusive ranges and integer rounding`() {
        assertEquals(666..999, FoundryMath.temperatureRange(FoundryStation.HAMMER, 0))
        assertEquals(333..666, FoundryMath.temperatureRange(FoundryStation.GRINDSTONE, 0))
        assertEquals(0..333, FoundryMath.temperatureRange(FoundryStation.POLISHING_WHEEL, 0))
        assertEquals(749..915, FoundryMath.temperatureRange(FoundryStation.HAMMER, 130))
        assertEquals(416..582, FoundryMath.temperatureRange(FoundryStation.GRINDSTONE, 130))
        assertEquals(83..249, FoundryMath.temperatureRange(FoundryStation.POLISHING_WHEEL, 130))
        for (difficulty in 0..130) for (station in FoundryStation.entries) {
            val range = FoundryMath.temperatureRange(station, difficulty)
            assertTrue(range.first in range && range.last in range)
            assertFalse(range.first - 1 in range || range.last + 1 in range)
        }
    }

    @Test
    fun `hand in awards preserve observed ordinary and ruined swords`() {
        assertEquals(6120, FoundryMath.experience(79))
        assertEquals(12240, FoundryMath.coins(79))
        assertEquals(30, FoundryMath.experience(0))
        assertEquals(60, FoundryMath.coins(0))
        assertEquals(25230, FoundryMath.experience(199))
        assertThrows(IllegalArgumentException::class.java) { FoundryMath.experience(-1) }
    }

    @Test
    fun `opposite commission traits do not cancel bonuses between different moulds`() {
        val broad = FoundryMould(FoundryMouldPart.FORTE, 1, -1, "broad", 15, 8, -4, 0, null)
        val narrow = broad.copy(width = -8)
        assertEquals(0, broad.score(1))
        assertEquals(8, broad.score(4))
        assertEquals(8, narrow.score(1))
        assertEquals(0, narrow.score(4))
        assertEquals(4, broad.score(2))
        assertEquals(0, broad.score(5))
        assertEquals(8, listOf(broad, narrow).sumOf { it.score(1) })
    }
}
