package org.rsmod.content.skills.agility.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.configs.AgilityCourse

class AgilityCourseProgressTest {
    @Test
    fun `all thirty one physical Pyramid steps are required for completion`() {
        val pyramid = course(id = 14, count = 31)
        var packed = 0
        for (index in 0 until 30) {
            packed = checkNotNull(AgilityCourseProgress.nextProgress(packed, pyramid, index))
            assertTrue(packed != 0, "Lap completed early at index $index")
        }
        assertEquals(0, AgilityCourseProgress.nextProgress(packed, pyramid, 30))
    }

    @Test
    fun `skipping an obstacle above the old sixteen obstacle limit denies completion`() {
        val pyramid = course(id = 14, count = 31)
        val expectingSeventeen = (17 shl 5) or 14
        assertNull(AgilityCourseProgress.nextProgress(expectingSeventeen, pyramid, 18))
        assertNull(AgilityCourseProgress.nextProgress(expectingSeventeen, pyramid, 30))
        assertEquals((18 shl 5) or 14, AgilityCourseProgress.nextProgress(expectingSeventeen, pyramid, 17))
    }

    @Test
    fun `existing saved rooftop progress keeps the same bit layout`() {
        val draynor = course(id = 2, count = 7)
        val saved = (3 shl 5) or 2
        assertEquals((4 shl 5) or 2, AgilityCourseProgress.nextProgress(saved, draynor, 3))
        assertNull(AgilityCourseProgress.nextProgress(saved, draynor, 6))
        assertEquals((1 shl 5) or 2, AgilityCourseProgress.nextProgress(saved, draynor, 0))
    }

    @Test
    fun `another course cannot use a saved expected obstacle`() {
        val seers = course(id = 7, count = 6)
        val savedDraynor = (5 shl 5) or 2
        assertNull(AgilityCourseProgress.nextProgress(savedDraynor, seers, 5))
        assertEquals((1 shl 5) or 7, AgilityCourseProgress.nextProgress(savedDraynor, seers, 0))
    }

    @Test
    fun `Wyrm branch selection retains the completed prefix without awarding another obstacle`() {
        val basic = course(id = 12, count = 8)
        val advanced = course(id = 13, count = 8)
        assertEquals((3 shl 5) or 13, AgilityCourseProgress.switchedProgress((3 shl 5) or 12, basic, advanced, 3))
        assertNull(AgilityCourseProgress.switchedProgress(0, basic, advanced, 3))
        assertNull(AgilityCourseProgress.switchedProgress((2 shl 5) or 12, basic, advanced, 3))
        assertNull(AgilityCourseProgress.switchedProgress((3 shl 5) or 7, basic, advanced, 3))
        assertThrows(IllegalArgumentException::class.java) {
            AgilityCourseProgress.switchedProgress(0, basic, advanced, 0)
        }
    }

    @Test
    fun `Pyramid fall can repeat an earlier floor but cannot invent or advance progress`() {
        val pyramid = course(id = 14, count = 31)
        val nearTop = (24 shl 5) or 14
        assertEquals((16 shl 5) or 14, AgilityCourseProgress.rewoundProgress(nearTop, pyramid, 16))
        assertNull(AgilityCourseProgress.rewoundProgress(nearTop, pyramid, 25))
        assertNull(AgilityCourseProgress.rewoundProgress(0, pyramid, 16))
        assertNull(AgilityCourseProgress.rewoundProgress((24 shl 5) or 7, pyramid, 16))
        assertNull(AgilityCourseProgress.rewoundProgress((31 shl 5) or 14, pyramid, 16))
    }

    @Test
    fun `supported boundary fits the saved fields and oversized courses are rejected`() {
        val course = course(id = 31, count = 32)
        assertEquals((31 shl 5) or 31, AgilityCourseProgress.nextProgress((30 shl 5) or 31, course, 30))
        assertEquals(0, AgilityCourseProgress.nextProgress((31 shl 5) or 31, course, 31))
        assertThrows(IllegalArgumentException::class.java) {
            AgilityCourseProgress.nextProgress(0, course.copy(obstacleCount = 33), 0)
        }
    }

    @Test
    fun `legacy Pyramid repeat counters never masquerade as a claimed top`() {
        for (legacy in 0 until (1 shl 15)) {
            assertFalse(AgilityPyramidProgress.isTopClaimed(legacy), "Legacy counter $legacy")
        }
        assertTrue(AgilityPyramidProgress.isTopClaimed(AgilityPyramidProgress.TOP_CLAIMED_SENTINEL))
    }

    private fun course(id: Int, count: Int) = AgilityCourse(
        id = id,
        displayName = "Test course",
        levelReq = 1,
        obstacleCount = count,
        lapBonusXp = 100.0,
        restoresRunEnergy = false,
        lapCountVarp = "varp.unused_test_laps",
    )
}
