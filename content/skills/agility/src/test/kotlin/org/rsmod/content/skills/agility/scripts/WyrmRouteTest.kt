package org.rsmod.content.skills.agility.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.configs.ColossalWyrmCourse as Wyrm
import org.rsmod.map.CoordGrid

class WyrmRouteTest {
    @Test
    fun `common automatic edge descends to the fork before either branch`() {
        assertEquals(CoordGrid(1655, 2916, 1), Wyrm.BALANCE_1_LANDING)
        assertEquals(CoordGrid(1649, 2910, 1), Wyrm.SHARED_HOPS.last())
        val route = listOf(Wyrm.BALANCE_1_LANDING) + Wyrm.SHARED_HOPS
        assertEquals(listOf(2, 2, 2, 2, 2, 2), route.zipWithNext { a, b -> a.chebyshevDistance(b) })
        assertTrue(Wyrm.SHARED_HOPS.all { it.level == 1 })
        assertTrue(Wyrm.SHARED_HOPS.last().chebyshevDistance(Wyrm.BASIC_BALANCE_MOUNT) <= 3)
        assertEquals(CoordGrid(1648, 2908, 2), Wyrm.ADV_LADDER_DEST)
    }

    @Test
    fun `basic automatic leaps connect the diagonal rope to its own upper ladder`() {
        assertEquals(CoordGrid(1627, 2918, 1), Wyrm.BASIC_ROPE_LANDING)
        val route = listOf(Wyrm.BASIC_ROPE_LANDING) + Wyrm.BASIC_AUTO_JUMPS
        assertEquals(listOf(5, 3, 5), route.zipWithNext { a, b -> a.chebyshevDistance(b) })
        assertEquals(CoordGrid(1627, 2931, 1), Wyrm.BASIC_AUTO_JUMPS.last())
        assertEquals(CoordGrid(1625, 2932, 2), Wyrm.BASIC_LADDER_DEST)
        assertTrue(Wyrm.BASIC_LADDER_DEST.chebyshevDistance(Wyrm.ZIPLINE_MOUNT) <= 1)
    }

    @Test
    fun `advanced diagonal crossing and automatic rope meet the shared exit on plane two`() {
        assertEquals(CoordGrid(1633, 2908, 2), Wyrm.ADV_BALANCE_MOUNT)
        assertEquals(CoordGrid(1625, 2916, 2), Wyrm.ADV_BALANCE_DEST)
        assertEquals(CoordGrid(1624, 2931, 2), Wyrm.ADV_ROPE_DEST)
        assertEquals(2, Wyrm.ZIPLINE_MOUNT.level)
        assertEquals(CoordGrid(1645, 2933, 0), Wyrm.ZIPLINE_DEST)
        for ((mount, dest) in listOf(Wyrm.BASIC_ROPE_MOUNT to Wyrm.BASIC_ROPE_DEST,
            Wyrm.ADV_BALANCE_MOUNT to Wyrm.ADV_BALANCE_DEST)) {
            assertTrue(Wyrm.isForwardApproach(mount, mount, dest))
            assertFalse(Wyrm.isForwardApproach(dest, mount, dest))
            assertFalse(Wyrm.isForwardApproach(mount.translateLevel(-1), mount, dest))
        }
    }

    @Test
    fun `advanced branch retains the complete common prefix and cannot be entered for a lap from nothing`() {
        var progress = 0
        for (index in 0..2) progress = checkNotNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, index))
        progress = checkNotNull(AgilityCourseProgress.switchedProgress(progress, Wyrm.BASIC, Wyrm.ADVANCED, 3))
        for (index in 3..6) progress = checkNotNull(AgilityCourseProgress.nextProgress(progress, Wyrm.ADVANCED, index))
        assertEquals(0, AgilityCourseProgress.nextProgress(progress, Wyrm.ADVANCED, 7))
        assertNull(AgilityCourseProgress.switchedProgress(0, Wyrm.BASIC, Wyrm.ADVANCED, 3))
        assertNull(AgilityCourseProgress.nextProgress(0, Wyrm.ADVANCED, 7))
    }

    @Test
    fun `missing automatic basic edge denies finish but completing it makes the lap reachable`() {
        var progress = 0
        for (index in 0..4) progress = checkNotNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, index))
        assertNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, 6))
        assertNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, 7))
        for (index in 5..6) progress = checkNotNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, index))
        assertNotNull(AgilityCourseProgress.nextProgress(progress, Wyrm.BASIC, 7))
    }

    @Test
    fun `route repair retains working XP totals without treating the zipline as repeatable base XP`() {
        val shared = 2 * Wyrm.BASIC_OBSTACLE_XP + 6 * Wyrm.SHARED_HOP_XP
        assertEquals(601.6, shared + 4 * Wyrm.BASIC_OBSTACLE_XP + Wyrm.BASIC.lapBonusXp, 0.0001)
        assertEquals(1053.6, shared + 4 * Wyrm.ADVANCED_OBSTACLE_XP + Wyrm.ADVANCED.lapBonusXp, 0.0001)
    }
}
