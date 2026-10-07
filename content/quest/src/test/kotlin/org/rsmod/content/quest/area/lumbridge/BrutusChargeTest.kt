package org.rsmod.content.quest.area.lumbridge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class BrutusChargeTest {
    @Test fun `charge hits the swept footprint in all cardinal directions but not other floors`() {
        val start = CoordGrid(3204, 3204)
        for (dir in listOf(Direction8.North, Direction8.South, Direction8.East, Direction8.West)) {
            val end = start.translate(dir.dx * 5, dir.dz * 5)
            assertTrue(brutusChargeHits(start, end, 3, start.translate(1, 1)))
            assertTrue(brutusChargeHits(start, end, 3, end.translate(2, 2)))
            assertFalse(brutusChargeHits(start, end, 3, CoordGrid(start.x, start.z, 1)))
            assertFalse(brutusChargeHits(start, end, 3, start.translate(-dir.dz * 5, dir.dx * 5)))
        }
    }

    @Test fun `charge pushes three tiles but stops before a blocked tile`() {
        for (dir in listOf(Direction8.North, Direction8.South, Direction8.East, Direction8.West)) {
            val start = CoordGrid(3204, 3204)
            val flags = CollisionFlagMap().apply { allocateIfAbsent(start.x, start.z, 0) }
            val steps = StepValidator(flags)
            assertEquals(start.translate(dir.dx * 3, dir.dz * 3), brutusChargePushDestination(start, dir, steps))
            val blocked = start.translate(dir.dx * 2, dir.dz * 2)
            flags.add(blocked.x, blocked.z, 0, CollisionFlag.LOC)
            assertEquals(start.translate(dir.dx, dir.dz), brutusChargePushDestination(start, dir, steps))
            val adjacent = start.translate(dir.dx, dir.dz)
            flags.add(adjacent.x, adjacent.z, 0, CollisionFlag.LOC)
            assertEquals(start, brutusChargePushDestination(start, dir, steps))
        }
    }
}
