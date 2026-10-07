package org.rsmod.content.skills.agility.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.configs.AgilityPyramidCourse as Pyramid
import org.rsmod.map.CoordGrid

class PyramidRouteTest {
    @Test
    fun `repeated gap locs follow their own physical floor and direction`() {
        val cases = listOf(
            CoordGrid(3356, 2847, 2) to CoordGrid(3357, 2849, 2),
            CoordGrid(3364, 2833, 2) to CoordGrid(3363, 2834, 2),
            CoordGrid(3370, 2841, 3) to CoordGrid(3370, 2840, 3),
            CoordGrid(3046, 4697, 3) to CoordGrid(3046, 4696, 3),
        )
        for ((clicked, destination) in cases) {
            val obstacle = checkNotNull(Pyramid.clickedAt(clicked))
            assertEquals(destination, obstacle.dest)
            assertTrue(Pyramid.isForwardApproach(obstacle, obstacle.mount))
            assertFalse(Pyramid.isForwardApproach(obstacle, obstacle.dest))
            assertFalse(Pyramid.isForwardApproach(obstacle, obstacle.mount.translateLevel(-1)))
        }
        assertNull(Pyramid.clickedAt(CoordGrid(3356, 2847, 1)))
    }

    @Test
    fun `detached summit stairs change map area without creating a nonexistent plane four`() {
        val stairs = checkNotNull(Pyramid.clickedAt(CoordGrid(3360, 2837, 3)))
        assertEquals(CoordGrid(3041, 4695, 2), stairs.dest)
        assertEquals(Pyramid.Kind.Stairs, stairs.kind)
        assertEquals(22, stairs.index + 1)
        val exit = checkNotNull(Pyramid.clickedAt(CoordGrid(3045, 4696, 3)))
        assertEquals(CoordGrid(3364, 2830, 0), exit.dest)
        assertEquals(Pyramid.COURSE.obstacleCount - 1, exit.index)
    }

    @Test
    fun `both aliases of a plank and wallhang resolve to one ordered obstacle`() {
        assertEquals(Pyramid.clickedAt(CoordGrid(3375, 2845, 1)), Pyramid.clickedAt(CoordGrid(3375, 2840, 1)))
        assertEquals(Pyramid.clickedAt(CoordGrid(3370, 2831, 1)), Pyramid.clickedAt(CoordGrid(3367, 2832, 1)))
        assertEquals((0 until 31).toList(), Pyramid.OBSTACLES.map { it.index })
        val clicks = Pyramid.OBSTACLES.flatMap { it.clicks }
        assertEquals(clicks.size, clicks.toSet().size, "Each physical click must resolve unambiguously")
    }

    @Test
    fun `running across a stone mount triggers once while reverse and unrelated movement do not`() {
        val before = CoordGrid(3355, 2840, 1)
        val mount = CoordGrid(3355, 2841, 1)
        val after = CoordGrid(3355, 2842, 1)
        assertEquals(1, Pyramid.crossedStone(before, mount, after)?.index)
        assertEquals(1, Pyramid.crossedStone(before, CoordGrid.NULL, mount)?.index)
        assertNull(Pyramid.crossedStone(after, mount, before))
        assertNull(Pyramid.crossedStone(CoordGrid(3369, 2849, 2), CoordGrid(3368, 2849, 2), CoordGrid(3367, 2849, 2)))
        assertEquals(1, Pyramid.crossedStone(CoordGrid(3354, 2841, 1), CoordGrid.NULL, mount)?.index)
        assertNull(Pyramid.crossedStone(mount, CoordGrid.NULL, mount))
        assertNull(Pyramid.crossedStone(CoordGrid(3360, 2840, 1), mount, after))
        assertNull(Pyramid.crossedStone(before.translateLevel(-1), mount, after))
    }

    @Test
    fun `floor drops rewind to previously observed landings including both summit floors`() {
        val cases = listOf(1 to 0, 12 to 1, 18 to 9, 24 to 17, 28 to 22)
        for ((failed, expected) in cases) {
            val landing = Pyramid.failureLanding(Pyramid.OBSTACLES[failed])
            assertEquals(expected, landing.expectedIndex)
            val checkpoint = if (expected == 0) Pyramid.OBSTACLES[0].mount else Pyramid.OBSTACLES[expected - 1].dest
            assertEquals(checkpoint, landing.dest)
            assertTrue(expected <= failed, "A fall cannot create missing progress")
        }
        val sameFloor = Pyramid.OBSTACLES[20]
        assertEquals(sameFloor.mount, Pyramid.failureLanding(sameFloor).dest)
        assertNull(Pyramid.failureLanding(sameFloor).expectedIndex)
    }

    @Test
    fun `perfect capture obstacle XP and level dependent exit remain separate`() {
        assertEquals(722.0, Pyramid.OBSTACLES.sumOf { it.kind.xp }, 0.0001)
        assertEquals(948.0, Pyramid.completionXp(81))
        assertEquals(540.0, Pyramid.completionXp(30))
        assertEquals(1000.0, Pyramid.completionXp(88))
        assertEquals(1000.0, Pyramid.completionXp(99))
    }
}
