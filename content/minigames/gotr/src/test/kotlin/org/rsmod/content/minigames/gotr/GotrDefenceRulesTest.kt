package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GotrDefenceRulesTest {
    @Test
    fun `cell recharge awards reproduce three captured contribution deltas`() {
        assertEquals(5, GotrDefenceRules.useCell(4, 100, 2).energy)
        assertEquals(9, GotrDefenceRules.useCell(4, 100, 3).energy)
        assertEquals(15, GotrDefenceRules.useCell(4, 100, 4).energy)
    }

    @Test
    fun `a high cell strengthens a weak barrier one tier without skipping to overcharged`() {
        val result = GotrDefenceRules.useCell(1, 5, 4)
        assertEquals(2, result.tier)
        assertEquals(50, result.hitpoints)
        assertEquals("strengthens", result.verb)
        assertFalse(result.wasFull)
    }

    @Test
    fun `only already full recharge consumes the special reward budget`() {
        assertFalse(GotrDefenceRules.useCell(4, 399, 1).wasFull)
        assertTrue(GotrDefenceRules.useCell(4, 400, 1).wasFull)
        assertFalse(GotrDefenceRules.useCell(1, 50, 2).wasFull)
        val budget = GotrDefenceRules.FullBarrierBudget()
        var energy = 0
        var xp = 0.0
        repeat(100) {
            val reward = budget.claim(15, 250.0)
            energy += reward.first * 2
            xp += reward.second
        }
        assertEquals(450, energy)
        assertEquals(2500.0, xp)
        assertEquals(0 to 0.0, budget.claim(15, 250.0))
    }

    @Test
    fun `wave schedule follows attack beat and native birth intervals without catch-up flood`() {
        assertEquals(201, GotrDefenceRules.FIRST_ATTACK_TICK)
        assertEquals(277, GotrDefenceRules.nextWaveTick(201))
        assertEquals(353, GotrDefenceRules.nextWaveTick(277))
        assertEquals(961, GotrDefenceRules.nextWaveTick(950))
    }

    @Test
    fun `combat reach measures footprint edges instead of npc south west anchors`() {
        assertEquals(1, GotrDefenceRules.rectangleDistance(3614, 9514, 3, 3614, 9511, 3))
        assertEquals(0, GotrDefenceRules.rectangleDistance(3614, 9511, 3, 3615, 9512, 1))
        assertEquals(4, GotrDefenceRules.rectangleDistance(3614, 9517, 1, 3614, 9511, 3))
    }
}
