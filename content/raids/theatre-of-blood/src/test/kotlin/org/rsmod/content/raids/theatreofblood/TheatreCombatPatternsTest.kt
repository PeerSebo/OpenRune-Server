package org.rsmod.content.raids.theatreofblood

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TheatreCombatPatternsTest {
    @Test
    fun `rooms proceed linearly and the final room does not wrap`() {
        assertEquals(listOf(1, 2, 3, 4, 5, null), (0..5).map {
            TheatreCombatPatterns.nextRoom(it)
        })
        assertEquals(listOf(false, true, false, true, false, false), (0..5).map {
            TheatreCombatPatterns.supplyChestAfter(it)
        })
    }

    @Test
    fun `waves stall at the current cap and resume as surviving nylocas die`() {
        assertFalse(TheatreCombatPatterns.nyloWaveReady(19, 12, 200, 200))
        assertTrue(TheatreCombatPatterns.nyloWaveReady(19, 11, 200, 200))
        assertTrue(TheatreCombatPatterns.nyloWaveReady(20, 12, 200, 200))
        assertFalse(TheatreCombatPatterns.nyloWaveReady(20, 24, 200, 200))
        assertFalse(TheatreCombatPatterns.nyloWaveReady(20, 0, 199, 200))
        assertTrue(TheatreCombatPatterns.nyloWaveReady(31, 23, 200, 200))
    }

    @Test
    fun `Nylocas boss requires all waves cleared with at least one pillar remaining`() {
        assertFalse(TheatreCombatPatterns.nyloBossReady(30, 0, 4))
        assertFalse(TheatreCombatPatterns.nyloBossReady(31, 1, 4))
        assertFalse(TheatreCombatPatterns.nyloBossReady(31, 0, 0))
        assertTrue(TheatreCombatPatterns.nyloBossReady(31, 0, 1))
    }

    @Test
    fun `wrong style locks only the player who made the attack for that small nylocas`() {
        val mask = TheatreCombatPatterns.nyloWrongStyleMask(0, 3)
        for (member in 0..4) {
            assertEquals(member == 3, TheatreCombatPatterns.nyloMemberLocked(mask, member))
        }
        assertEquals(0, TheatreCombatPatterns.nyloSmallDamage(
            TheatreAttackStyle.MELEE,
            TheatreAttackStyle.MELEE,
            10,
            memberLocked = true,
        ))
        assertEquals(10, TheatreCombatPatterns.nyloSmallDamage(
            TheatreAttackStyle.MELEE,
            TheatreAttackStyle.MELEE,
            10,
            memberLocked = false,
        ))
        assertEquals(0, TheatreCombatPatterns.nyloSmallDamage(
            TheatreAttackStyle.MELEE,
            TheatreAttackStyle.RANGED,
            10,
            memberLocked = false,
        ))
    }

    @Test
    fun `Sotetseg mazes trigger once at 66 point 6 and 33 point 3 percent`() {
        assertTrue(TheatreCombatPatterns.sotetsegMazeThresholds(667, 1000, 0).isEmpty())
        assertEquals(listOf(666), TheatreCombatPatterns.sotetsegMazeThresholds(666, 1000, 0))
        assertEquals(listOf(666, 333), TheatreCombatPatterns.sotetsegMazeThresholds(333, 1000, 0))
        assertEquals(listOf(333), TheatreCombatPatterns.sotetsegMazeThresholds(333, 1000, 1))
        assertTrue(TheatreCombatPatterns.sotetsegMazeThresholds(1, 1000, 3).isEmpty())
    }

    @Test
    fun `a valid queued small Nylocas hit keeps its launch eligibility through a flicker`() {
        val queuedDamage = TheatreCombatPatterns.nyloSmallDamage(
            TheatreAttackStyle.MAGIC,
            TheatreAttackStyle.MAGIC,
            8,
            memberLocked = false,
        )
        assertEquals(
            8,
            TheatreCombatPatterns.nyloSmallImpactDamage(queuedDamage, memberLocked = false),
        )
        assertEquals(
            0,
            TheatreCombatPatterns.nyloSmallImpactDamage(queuedDamage, memberLocked = true),
        )
        assertEquals(0, TheatreCombatPatterns.nyloSmallImpactDamage(0, memberLocked = false))
    }

    @Test
    fun `Xarpus counter phase uses health after recovery rather than unhealed maximum`() {
        assertFalse(TheatreCombatPatterns.xarpusStareReady(801, 3200))
        assertTrue(TheatreCombatPatterns.xarpusStareReady(800, 3200))
    }

    @Test
    fun `Verzik phase deaths transition twice before raid completion`() {
        assertEquals(1, TheatreCombatPatterns.nextVerzikPhaseOnDeath(0))
        assertEquals(2, TheatreCombatPatterns.nextVerzikPhaseOnDeath(1))
        assertEquals(3, TheatreCombatPatterns.nextVerzikPhaseOnDeath(2))
    }

    @Test
    fun `Verzik requires four autos before each rotating special`() {
        assertNull(TheatreCombatPatterns.verzikSpecial(3, 0))
        assertEquals(VerzikSpecial.NYLOCAS, TheatreCombatPatterns.verzikSpecial(4, 0))
        assertEquals(VerzikSpecial.WEBS, TheatreCombatPatterns.verzikSpecial(4, 1))
        assertEquals(VerzikSpecial.POWER_BLAST, TheatreCombatPatterns.verzikSpecial(4, 2))
        assertEquals(VerzikSpecial.GREEN_BALL, TheatreCombatPatterns.verzikSpecial(4, 3))
        assertEquals(VerzikSpecial.NYLOCAS, TheatreCombatPatterns.verzikSpecial(4, 4))
        assertEquals(7, TheatreCombatPatterns.verzikAttackTicks(enraged = false))
        assertEquals(5, TheatreCombatPatterns.verzikAttackTicks(enraged = true))
    }

    @Test
    fun `Verzik green ball and tornado use different hitpoints sources`() {
        assertEquals(74, TheatreCombatPatterns.verzikGreenBallDamage(99))
        assertEquals(25, TheatreCombatPatterns.verzikTornadoDamage(50))
        assertEquals(75, TheatreCombatPatterns.verzikTornadoHealing(25))
        assertEquals(57, TheatreCombatPatterns.verzikTornadoDamage(115))
        assertEquals(171, TheatreCombatPatterns.verzikTornadoHealing(57))
    }
}
