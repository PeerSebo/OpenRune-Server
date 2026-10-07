package org.rsmod.api.area.checker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class WildernessLevelTest {
    @Test fun `Callisto chamber stays level forty at boss exit and northern arrival`() {
        for (tile in listOf(CoordGrid(3357, 10320), CoordGrid(3358, 10315), CoordGrid(3359, 10336),
            CoordGrid(3338, 10314), CoordGrid(3380, 10355))) {
            assertEquals(40, tile.wildernessLevelInArea(), tile.toString())
        }
    }

    @Test fun `Callisto override does not change Artio Scorpia or surface Wilderness`() {
        assertEquals(21, CoordGrid(1759, 11552).wildernessLevelInArea())
        assertEquals(53, CoordGrid(3233, 10341).wildernessLevelInArea())
        assertEquals(42, CoordGrid(3291, 3849).wildernessLevelInArea())
        assertEquals(45, CoordGrid(3359, 10279).wildernessLevelInArea())
        assertEquals(1, CoordGrid(3359, 10336, 1).wildernessLevelInArea())
    }
}
