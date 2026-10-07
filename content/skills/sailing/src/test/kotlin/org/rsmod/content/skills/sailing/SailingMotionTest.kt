package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SailingMotionTest {
    private val wooden = SailingMotionStats(192, 64, 20)

    @Test
    fun `captured skiff accelerates through half tile steps and decelerates after unsetting`() {
        val motion = SailingMotion(0)
        motion.increase()
        assertEquals(SailingStep(0, 0, -64), motion.step(0, 1916, wooden))
        motion.increase()
        assertEquals(SailingStep(0, 0, -128), motion.step(0, 1917, wooden))
        assertEquals(SailingStep(0, 0, -192), motion.step(0, 1918, wooden))
        motion.stop()
        assertEquals(-128, motion.step(0, 1919, wooden).dz)
        assertEquals(-64, motion.step(0, 1920, wooden).dz)
        assertEquals(0, motion.step(0, 1921, wooden).dz)
    }

    @Test
    fun `captured cardinal slow and reverse vectors use absolute fine coordinates`() {
        val east = SailingMotion(1536)
        east.increase()
        assertEquals(SailingStep(1536, 64, 0), east.step(1536, 1, wooden))
        val north = SailingMotion(1024)
        north.increase()
        assertEquals(SailingStep(1024, 0, 64), north.step(1024, 1, wooden))
        val reverse = SailingMotion(1024)
        reverse.decrease()
        assertEquals(SailingStep(1024, 0, -64), reverse.step(1024, 1, wooden))
    }

    @Test
    fun `captured boat can turn through zero while stationary`() {
        val motion = SailingMotion(1152)
        motion.heading(0)
        var angle = 1152
        for (expected in listOf(1280, 1408, 1536, 1664, 1792, 1920, 0)) {
            val step = motion.step(angle, 1, wooden)
            assertEquals(SailingStep(expected, 0, 0), step)
            angle = step.angle
        }
    }

    @Test
    fun `reverse stop slow fast controls use native enum values and stop at limits`() {
        val motion = SailingMotion(0)
        assertTrue(motion.decrease())
        assertEquals(3, motion.mode.value)
        assertFalse(motion.decrease())
        for (value in listOf(0, 1, 2)) {
            assertTrue(motion.increase())
            assertEquals(value, motion.mode.value)
        }
        assertFalse(motion.increase())
        motion.toggle()
        assertEquals(0, motion.mode.value)
    }

    @Test
    fun `all sixteen captured fast headings use quarter tile vector quantization`() {
        val vectors = listOf(
            0 to -192, -64 to -192, -128 to -128, -192 to -64,
            -192 to 0, -192 to 64, -128 to 128, -64 to 192,
            0 to 192, 64 to 192, 128 to 128, 192 to 64,
            192 to 0, 192 to -64, 128 to -128, 64 to -192,
        )
        for ((heading, vector) in vectors.withIndex()) {
            val motion = SailingMotion(heading * 128)
            motion.forward()
            repeat(2) { motion.step(heading * 128, it, wooden) }
            assertEquals(SailingStep(heading * 128, vector.first, vector.second), motion.step(heading * 128, 3, wooden))
        }
        val slow = SailingMotion(128)
        slow.increase()
        assertEquals(SailingStep(128, -32, -64), slow.step(128, 1, wooden))
    }

    @Test
    fun `turns take the short path and invalid headings do not mutate controls`() {
        assertEquals(0, SailingMotion.turnTowards(1920, 128))
        assertEquals(1920, SailingMotion.turnTowards(0, 1792))
        val motion = SailingMotion(512)
        assertThrows(IllegalArgumentException::class.java) { motion.heading(16) }
        assertEquals(512, motion.requestedAngle)
    }

    @Test
    fun `wind adds half a tile to forward movement only and expires precisely`() {
        val motion = SailingMotion(0)
        motion.forward()
        repeat(3) { motion.step(0, it, wooden) }
        motion.boost(10, wooden)
        assertEquals(-256, motion.step(0, 29, wooden).dz)
        assertEquals(-192, motion.step(0, 30, wooden).dz)
        motion.halt()
        motion.decrease()
        motion.boost(31, wooden)
        assertEquals(64, motion.step(0, 32, wooden).dz)
    }

    @Test
    fun `collision halt cancels momentum and wind immediately`() {
        val motion = SailingMotion(0)
        motion.forward()
        repeat(3) { motion.step(0, it, wooden) }
        motion.boost(10, wooden)
        motion.halt()
        assertEquals(0, motion.speed)
        assertEquals(0, motion.windUntil)
        assertEquals(SailingStep(0, 0, 0), motion.step(0, 11, wooden))
    }

    @Test
    fun `upgraded sail stats raise cruise speed and helm stats affect acceleration`() {
        val motion = SailingMotion(0)
        motion.forward()
        val upgraded = SailingMotionStats(384, 128, 25)
        assertEquals(-128, motion.step(0, 1, upgraded).dz)
        assertEquals(-256, motion.step(0, 2, upgraded).dz)
        assertEquals(-384, motion.step(0, 3, upgraded).dz)
    }
}
