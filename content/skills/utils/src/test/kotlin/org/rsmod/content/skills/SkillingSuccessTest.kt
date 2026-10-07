package org.rsmod.content.skills

import kotlin.math.floor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkillingSuccessTest {
    @Test
    fun `shared calculation preserves interpolation rounding and clamping across skill levels`() {
        val bounds = listOf(-1024, -512, -128, -1, 0, 1, 32, 64, 128, 198, 255, 256, 512, 1024)
        for (low in bounds) {
            for (high in bounds) {
                for (level in -5..126) {
                    val clamped = level.coerceIn(1, 99)
                    val oldLow = low * (99 - clamped) / 98.0
                    val oldHigh = high * (clamped - 1) / 98.0
                    val expected = ((1.0 + floor(oldLow + oldHigh + 0.5)) / 256.0).coerceIn(0.0, 1.0)
                    assertEquals(expected, computeSkillingSuccess(low, high, level))
                }
            }
        }
    }

    @Test
    fun `half step rounding and guaranteed success retain their original boundaries`() {
        assertEquals(1.0 / 256.0, computeSkillingSuccess(0, 1, 49))
        assertEquals(2.0 / 256.0, computeSkillingSuccess(0, 1, 50))
        assertEquals(0.0, computeSkillingSuccess(-1, -1, 50))
        assertEquals(1.0, computeSkillingSuccess(512, 512, 50))
        assertEquals(computeSkillingSuccess(64, 512, 99), computeSkillingSuccess(64, 512, 126))
    }
}
