package org.rsmod.content.bosses.vardorvis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VardorvisScalingTest {
    @Test
    fun `health loss lowers defence and increases strength`() {
        assertEquals(215, VardorvisScaling.defence(1.0, 215))
        assertEquals(107, VardorvisScaling.defence(0.5, 215))
        assertEquals(0, VardorvisScaling.defence(0.0, 215))
        assertEquals(270, VardorvisScaling.strength(1.0, 270))
        assertEquals(335, VardorvisScaling.strength(0.5, 270))
        assertEquals(400, VardorvisScaling.strength(0.0, 270))
        assertEquals(400, VardorvisScaling.strength(-0.1, 270))
        assertEquals(215, VardorvisScaling.defence(1.1, 215))
    }
}
