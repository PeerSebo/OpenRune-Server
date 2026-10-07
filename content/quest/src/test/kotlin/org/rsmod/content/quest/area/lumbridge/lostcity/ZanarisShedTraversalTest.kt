package org.rsmod.content.quest.area.lumbridge.lostcity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class ZanarisShedTraversalTest {
    private val door = CoordGrid(3202, 3169)

    @Test
    fun `fairy entry applies only to the west side of the doorway`() {
        assertTrue(ZanarisShed.isEntering(door.translateX(-1), door))
        for (inside in listOf(door, door.translateX(1), door.translateZ(1), door.translateZ(-1))) {
            assertFalse(ZanarisShed.isEntering(inside, door))
        }
    }

    @Test
    fun `entry crosses the doorway without an empty or repeated starting waypoint`() {
        val outside = door.translateX(-1)
        assertEquals(listOf(door), ZanarisShed.crossingRoute(outside, door))
        assertEquals(listOf(outside, door), ZanarisShed.crossingRoute(outside.translateZ(1), door))
    }

    @Test
    fun `exit aligns with the doorway before crossing its west boundary`() {
        val outside = door.translateX(-1)
        assertEquals(listOf(outside), ZanarisShed.crossingRoute(door, door))
        assertEquals(listOf(door, outside), ZanarisShed.crossingRoute(door.translateX(1), door))
        assertEquals(listOf(door, outside), ZanarisShed.crossingRoute(door.translateZ(-1), door))
    }
}
