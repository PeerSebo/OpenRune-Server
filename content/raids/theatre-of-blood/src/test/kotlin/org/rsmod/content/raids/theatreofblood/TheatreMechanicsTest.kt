package org.rsmod.content.raids.theatreofblood

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TheatreMechanicsTest {
    @Test
    fun `normal teams retain the documented three four and five player health scales`() {
        assertEquals(listOf(2625, 2625, 2625, 3062, 3500), (1..5).map {
            TheatreMechanics.normalHitpoints(3500, it)
        })
        assertEquals(1, TheatreMechanics.normalHitpoints(1, 1))
        assertEquals(Int.MAX_VALUE, TheatreMechanics.normalHitpoints(Int.MAX_VALUE, 5))
        assertFailsWith<IllegalArgumentException> { TheatreMechanics.normalHitpoints(3500, 6) }
    }

    @Test
    fun `a large Maiden hit queues every crossed threshold only once`() {
        assertEquals(listOf(70, 50), TheatreMechanics.maidenThresholds(490, 1000, 0))
        var mask = TheatreMechanics.markMaidenThreshold(0, 70)
        mask = TheatreMechanics.markMaidenThreshold(mask, 50)
        assertTrue(TheatreMechanics.maidenThresholds(490, 1000, mask).isEmpty())
        assertEquals(listOf(30), TheatreMechanics.maidenThresholds(290, 1000, mask))
        mask = TheatreMechanics.markMaidenThreshold(mask, 30)
        assertTrue(TheatreMechanics.maidenThresholds(0, 1000, mask).isEmpty())
    }

    @Test
    fun `healing Maiden past a previous threshold does not reset that threshold`() {
        val mask = TheatreMechanics.markMaidenThreshold(0, 70)
        val healed = TheatreMechanics.healedHitpoints(
            600,
            1000,
            TheatreMechanics.maidenHealing(200),
        )
        assertEquals(1000, healed)
        assertTrue(TheatreMechanics.maidenThresholds(690, 1000, mask).isEmpty())
        assertEquals(200, TheatreMechanics.maidenHealing(100))
        assertEquals(0, TheatreMechanics.maidenHealing(0))
    }

    @Test
    fun `percentage checks do not round the current health into another phase`() {
        assertFalse(TheatreMechanics.atOrBelowPercent(701, 1000, 70))
        assertTrue(TheatreMechanics.atOrBelowPercent(700, 1000, 70))
        assertFalse(TheatreMechanics.atOrBelowPercent(613, 875, 70))
        assertTrue(TheatreMechanics.atOrBelowPercent(612, 875, 70))
        assertTrue(TheatreMechanics.atOrBelowPercent(Int.MAX_VALUE, Int.MAX_VALUE, 100))
    }

    @Test
    fun `absorbed Maiden healers increase magic damage before protection is applied`() {
        assertEquals(36, TheatreMechanics.maidenMagicDamage(0, protectFromMagic = false))
        assertEquals(18, TheatreMechanics.maidenMagicDamage(0, protectFromMagic = true))
        assertEquals(40, TheatreMechanics.maidenMagicDamage(1, protectFromMagic = false))
        assertEquals(21, TheatreMechanics.maidenMagicDamage(2, protectFromMagic = true))
    }

    @Test
    fun `healing near the maximum integer cannot wrap into negative hitpoints`() {
        assertEquals(Int.MAX_VALUE, TheatreMechanics.healedHitpoints(
            Int.MAX_VALUE - 10,
            Int.MAX_VALUE,
            100,
        ))
        assertFailsWith<ArithmeticException> { TheatreMechanics.maidenHealing(Int.MAX_VALUE) }
    }

    @Test
    fun `Bloat moving reduction rounds down and sleeping restores full damage`() {
        assertEquals(15, TheatreMechanics.bloatIncomingDamage(31, moving = true))
        assertEquals(31, TheatreMechanics.bloatIncomingDamage(31, moving = false))
        assertEquals(0, TheatreMechanics.bloatIncomingDamage(1, moving = true))
        assertEquals(15, TheatreMechanics.bloatFlyDamage(20, protectFromMissiles = true))
        assertEquals(20, TheatreMechanics.bloatFlyDamage(20, protectFromMissiles = false))
    }

    @Test
    fun `Bloat health determines movement speed until hits toggle it below forty percent`() {
        assertFalse(TheatreMechanics.bloatRuns(601, 1000, runningBelowForty = true))
        assertFalse(TheatreMechanics.bloatRuns(600, 1000, runningBelowForty = false))
        assertTrue(TheatreMechanics.bloatRuns(599, 1000, runningBelowForty = false))
        assertTrue(TheatreMechanics.bloatRuns(400, 1000, runningBelowForty = false))
        assertFalse(TheatreMechanics.bloatRuns(399, 1000, runningBelowForty = false))
        assertTrue(TheatreMechanics.bloatRuns(399, 1000, runningBelowForty = true))
    }

    @Test
    fun `a recent Bloat turn or speed change extends his walk by five ticks`() {
        assertFalse(TheatreMechanics.bloatCanSleep(100, 101, 80))
        assertFalse(TheatreMechanics.bloatCanSleep(100, 100, 96))
        assertTrue(TheatreMechanics.bloatCanSleep(101, 100, 96))
    }

    @Test
    fun `Nylocas reflects and heals exactly the wrong style damage`() {
        for (bossStyle in TheatreAttackStyle.entries) {
            for (attackStyle in TheatreAttackStyle.entries) {
                val actual = TheatreMechanics.nyloHit(bossStyle, attackStyle, 43)
                val expected = if (bossStyle == attackStyle) TheatreHit(43, 0, 0)
                    else TheatreHit(0, 43, 43)
                assertEquals(expected, actual)
            }
        }
        assertEquals(TheatreHit(0, 0, 0), TheatreMechanics.nyloHit(
            TheatreAttackStyle.MELEE,
            TheatreAttackStyle.MAGIC,
            0,
        ))
    }

    @Test
    fun `Nylocas next style always differs and can choose either other style`() {
        for (style in TheatreAttackStyle.entries) {
            val choices = (0..1).map { TheatreMechanics.nextNyloStyle(style, it) }.toSet()
            assertEquals(TheatreAttackStyle.entries.toSet() - style, choices)
        }
        assertFailsWith<IllegalArgumentException> {
            TheatreMechanics.nextNyloStyle(TheatreAttackStyle.MAGIC, 2)
        }
    }

    @Test
    fun `shared ball damage uses all soakers and retains the selected minimum`() {
        assertEquals(180, TheatreMechanics.sharedBallDamage(180, 1, minimumDamage = 0))
        assertEquals(90, TheatreMechanics.sharedBallDamage(180, 2, minimumDamage = 0))
        assertEquals(60, TheatreMechanics.sharedBallDamage(180, 3, minimumDamage = 0))
        assertEquals(38, TheatreMechanics.sharedBallDamage(188, 5, minimumDamage = 0))
        assertEquals(90, TheatreMechanics.sharedBallDamage(60, 1, minimumDamage = 90))
        assertEquals(429496730, TheatreMechanics.sharedBallDamage(Int.MAX_VALUE, 5, 0))
    }

    @Test
    fun `maze paths must stay inside the allowed floor and use connected cardinal steps`() {
        val floor = (0..4).flatMap { x -> (0..4).map { z -> TheatreTile(x, z) } }.toSet()
        val path = listOf(TheatreTile(0, 0), TheatreTile(0, 1), TheatreTile(1, 1))
        assertTrue(TheatreMechanics.connectedMaze(path, floor))
        assertTrue(TheatreMechanics.connectedMaze(path.reversed(), floor))
        assertFalse(TheatreMechanics.connectedMaze(emptyList(), floor))
        assertFalse(TheatreMechanics.connectedMaze(
            listOf(TheatreTile(0, 0), TheatreTile(1, 1)),
            floor,
        ))
        assertFalse(TheatreMechanics.connectedMaze(path + TheatreTile(0, 1), floor))
        assertFalse(TheatreMechanics.connectedMaze(path + TheatreTile(1, -1), floor))
        assertFalse(TheatreMechanics.connectedMaze(path + TheatreTile(4, 1), floor))
    }

    @Test
    fun `Xarpus only retaliates against attacks from his selected quadrant`() {
        val center = TheatreTile(10, 10)
        val quadrants = mapOf(
            TheatreTile(13, 13) to TheatreQuadrant.NORTH_EAST,
            TheatreTile(13, 7) to TheatreQuadrant.SOUTH_EAST,
            TheatreTile(7, 7) to TheatreQuadrant.SOUTH_WEST,
            TheatreTile(7, 13) to TheatreQuadrant.NORTH_WEST,
        )
        for ((attacker, quadrant) in quadrants) {
            assertEquals(quadrant, TheatreMechanics.xarpusQuadrant(center, attacker))
            for (staring in TheatreQuadrant.entries) {
                assertEquals(
                    staring == quadrant,
                    TheatreMechanics.xarpusRetaliates(staring, center, attacker),
                )
            }
        }
        assertNull(TheatreMechanics.xarpusQuadrant(center, center))
    }

    @Test
    fun `Verzik throne damage caps apply per style while Dawnbringer special bypasses them`() {
        assertEquals(10, TheatreMechanics.verzikFirstPhaseDamage(TheatreAttackStyle.MELEE, 70))
        assertEquals(3, TheatreMechanics.verzikFirstPhaseDamage(TheatreAttackStyle.RANGED, 70))
        assertEquals(3, TheatreMechanics.verzikFirstPhaseDamage(TheatreAttackStyle.MAGIC, 70))
        assertEquals(2, TheatreMechanics.verzikFirstPhaseDamage(TheatreAttackStyle.MAGIC, 2))
        assertEquals(150, TheatreMechanics.verzikFirstPhaseDamage(
            TheatreAttackStyle.MAGIC,
            150,
            dawnbringerSpecial = true,
        ))
    }

    @Test
    fun `Verzik blood subphase and enrage use their own health boundaries`() {
        assertFalse(TheatreMechanics.verzikBloodPhase(350, 1000))
        assertTrue(TheatreMechanics.verzikBloodPhase(349, 1000))
        assertFalse(TheatreMechanics.verzikEnraged(201, 1000))
        assertTrue(TheatreMechanics.verzikEnraged(200, 1000))
    }

    @Test
    fun `reward shares exclude zero contribution and sum to one`() {
        val points = listOf(60, 30, 10, 0)
        val total = points.sumOf { it.toLong() }
        assertEquals(1.0, points.sumOf { TheatreMechanics.rewardShare(it, total) }, 0.000001)
        assertEquals(0.0, TheatreMechanics.rewardShare(0, total))
        assertEquals(0.0, TheatreMechanics.rewardShare(0, 0))
        assertFailsWith<IllegalArgumentException> { TheatreMechanics.rewardShare(100, 99) }
    }

    @Test
    fun `a configured repeated penalty floors each application and is bounded`() {
        assertEquals(100, TheatreMechanics.applyRepeatedPenalty(100, 0, 4, 5))
        assertEquals(80, TheatreMechanics.applyRepeatedPenalty(100, 1, 4, 5))
        assertEquals(64, TheatreMechanics.applyRepeatedPenalty(100, 2, 4, 5))
        assertEquals(1, TheatreMechanics.applyRepeatedPenalty(3, 2, 4, 5))
        assertEquals(0, TheatreMechanics.applyRepeatedPenalty(100, Int.MAX_VALUE, 4, 5))
        assertEquals(100, TheatreMechanics.applyRepeatedPenalty(100, Int.MAX_VALUE, 5, 5))
    }
}
