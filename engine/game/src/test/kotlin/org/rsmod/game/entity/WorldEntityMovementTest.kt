package org.rsmod.game.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class WorldEntityMovementTest {
    @Test
    fun `repeated one and a half tile movement retains the fractional position`() {
        val entity = entity().apply { rootCoords = CoordGrid(3031, 3203) }
        val startX = entity.fineX
        val startZ = entity.fineZ

        repeat(11) { step ->
            entity.moveRoot(entity.fineX + 192, entity.fineZ)
            val expectedX = startX + (step + 1) * 192
            assertEquals(expectedX, entity.fineX)
            assertEquals(startZ, entity.fineZ)
            assertEquals(CoordGrid(expectedX / 128, 3203), entity.rootCoords)
        }
    }

    @Test
    fun `fine movement preserves fractions on both axes and accepts a projected level`() {
        val entity = entity()
        entity.moveRoot(3031 * 128 + 17, 3203 * 128 + 113, level = 2)

        assertEquals(3031 * 128 + 17, entity.fineX)
        assertEquals(3203 * 128 + 113, entity.fineZ)
        assertEquals(CoordGrid(3031, 3203, 2), entity.rootCoords)
        assertEquals(2, entity.projectedLevel)

        entity.moveRoot(entity.fineX - 64, entity.fineZ - 192)
        assertEquals(CoordGrid(3030, 3202, 2), entity.rootCoords)
        assertEquals(2, entity.projectedLevel)
    }

    @Test
    fun `assigning a root tile resets a previous fractional position`() {
        val entity = entity()
        entity.moveRoot(3031 * 128 + 17, 3203 * 128 + 113, level = 2)
        entity.rootCoords = CoordGrid(3051, 2974, 0)

        assertEquals(3051 * 128, entity.fineX)
        assertEquals(2974 * 128, entity.fineZ)
        assertEquals(0, entity.projectedLevel)
    }

    @Test
    fun `moving the root leaves the occupied deck geometry in place`() {
        val entity = entity()
        val arrival = entity.instanceSouthWest.translate(4, 5)
        assertTrue(arrival in entity)

        entity.moveRoot(3031 * 128 + 64, 3203 * 128 + 96)

        assertTrue(arrival in entity)
        assertEquals(CoordGrid(15936, 13504, 1), entity.instanceSouthWest)
        assertFalse(entity.rootCoords in entity)
        assertEquals(CoordGrid(15936, 13504, 1), entity.region.southWest)
    }

    @Test
    fun `out of bounds movement fails before any part of the position changes`() {
        val entity = entity()
        entity.moveRoot(3031 * 128 + 64, 3203 * 128 + 96, level = 1)
        val original = Triple(entity.fineX, entity.fineZ, entity.rootCoords)
        val invalid = listOf(
            Triple(-1, 0, 0),
            Triple(CoordGrid.MAP_WIDTH * 128, 0, 0),
            Triple(0, -1, 0),
            Triple(0, CoordGrid.MAP_LENGTH * 128, 0),
            Triple(0, 0, -1),
            Triple(0, 0, CoordGrid.LEVEL_COUNT),
        )

        for ((x, z, level) in invalid) {
            assertThrows<IllegalArgumentException> { entity.moveRoot(x, z, level) }
            assertEquals(original, Triple(entity.fineX, entity.fineZ, entity.rootCoords))
        }
    }

    @Test
    fun `the first and last valid fine coordinates do not wrap`() {
        val entity = entity()
        entity.moveRoot(0, 0, 0)
        assertEquals(CoordGrid.ZERO, entity.rootCoords)

        val lastX = CoordGrid.MAP_WIDTH * 128 - 1
        val lastZ = CoordGrid.MAP_LENGTH * 128 - 1
        entity.moveRoot(lastX, lastZ, CoordGrid.LEVEL_COUNT - 1)
        assertEquals(lastX, entity.fineX)
        assertEquals(lastZ, entity.fineZ)
        assertEquals(CoordGrid(16383, 16383, 3), entity.rootCoords)
    }

    private fun entity(): WorldEntity {
        val southWest = CoordGrid(15936, 13504, 1)
        return WorldEntity(
            configId = 1,
            region = Region(southWest, southWest.translate(64, 64), uid = 1, slot = 1),
            instanceSouthWest = southWest,
            zoneSizeX = 1,
            zoneSizeZ = 1,
            minLevel = 0,
            maxLevel = 3,
            activeLevel = 1,
        )
    }
}
