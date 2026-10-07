package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SailingWindTest {
    @Test
    fun `captured missed offers repeat every 63 moving ticks with a 14 tick window`() {
        val wind = SailingWind()
        val offers = (1..200).filter { wind.tick(true) }
        assertEquals(listOf(49, 112, 175), offers)
        wind.reset()
        repeat(49) { wind.tick(true) }
        assertEquals(14, wind.window)
        repeat(13) { wind.tick(true) }
        assertEquals(1, wind.window)
        wind.tick(true)
        assertEquals(0, wind.window)
        assertFalse(wind.trim())
    }

    @Test
    fun `each wind offer can be claimed once and does not survive docking`() {
        val wind = SailingWind()
        assertFalse(wind.trim())
        repeat(49) { wind.tick(true) }
        assertTrue(wind.trim())
        assertFalse(wind.trim())
        repeat(49) { wind.tick(true) }
        wind.reset()
        assertFalse(wind.trim())
    }

    @Test
    fun `stationary and reverse sailing pause opportunity timers`() {
        val wind = SailingWind()
        repeat(1000) { assertFalse(wind.tick(false)) }
        assertEquals(49, wind.untilOffer)
        repeat(49) { wind.tick(true) }
        repeat(1000) { wind.tick(false) }
        assertEquals(14, wind.window)
    }
}
