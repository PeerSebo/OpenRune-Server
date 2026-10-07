package org.rsmod.content.bosses.scorpia

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class GuardianCycleTest {
    @Test fun `threshold-crossing attack waits for the next attack and every life summons once`() {
        val fight = ScorpiaLife()
        assertFalse(fight.claimSummon(currentHp = 120, playerAttack = true))
        assertFalse(fight.claimSummon(currentHp = 100, playerAttack = true))
        assertFalse(fight.claimSummon(currentHp = 90, playerAttack = false))
        assertTrue(fight.claimSummon(currentHp = 90, playerAttack = true))
        assertFalse(fight.claimSummon(currentHp = 200, playerAttack = true))
        assertFalse(fight.claimSummon(currentHp = 1, playerAttack = true))
        assertTrue(ScorpiaLife().claimSummon(currentHp = 99, playerAttack = true))
        assertFalse(ScorpiaLife().claimSummon(currentHp = 0, playerAttack = true))
    }

    @Test fun `two guardians heal sixteen each three ticks without duplicate same-tick healing`() {
        val guardians = listOf(GuardianCycle(100), GuardianCycle(100))
        var hp = 90
        for (tick in 100..112) {
            for (guardian in guardians) {
                val action = guardian.tick(tick, ownerAlive = true, inRange = true)
                if (action == GuardianAction.HEAL) hp += 8
                assertEquals(GuardianAction.WAIT, guardian.tick(tick, ownerAlive = true, inRange = true))
            }
        }
        assertEquals(90 + 4 * 16, hp)
    }

    @Test fun `lured and immobilised guardian expires exactly twenty-five ticks after last heal`() {
        val guardian = GuardianCycle(100)
        assertEquals(GuardianAction.HEAL, guardian.tick(103, true, true))
        for (tick in 104..127) assertEquals(GuardianAction.FOLLOW, guardian.tick(tick, true, false))
        assertEquals(GuardianAction.REMOVE, guardian.tick(128, true, false))
    }

    @Test fun `returning inside heal range refreshes timeout and queen death removes immediately`() {
        val guardian = GuardianCycle(100)
        assertEquals(GuardianAction.FOLLOW, guardian.tick(124, true, false))
        assertEquals(GuardianAction.HEAL, guardian.tick(124, true, true))
        assertEquals(GuardianAction.FOLLOW, guardian.tick(148, true, false))
        assertEquals(GuardianAction.REMOVE, guardian.tick(149, true, false))
        assertEquals(GuardianAction.REMOVE, GuardianCycle(100).tick(100, false, true))
    }

    @Test fun `healing range measures all sides of large queen and rejects another floor`() {
        val origin = CoordGrid(3233, 10341)
        assertEquals(3, ScorpiaRules.footprintDistance(origin, 5, origin.translate(7, 7)))
        assertEquals(4, ScorpiaRules.footprintDistance(origin, 5, origin.translate(8, 0)))
        assertEquals(1, ScorpiaRules.footprintDistance(origin, 5, origin.translate(-1, 3)))
        assertEquals(Int.MAX_VALUE, ScorpiaRules.footprintDistance(origin, 5, CoordGrid(origin.x, origin.z, 1)))
    }

    @Test fun `blocked preferred summon positions choose distinct open tiles outside the queen`() {
        val origin = CoordGrid(3233, 10341)
        val sites = ScorpiaRules.guardianSites(origin, 5) { it.x == origin.x - 2 }
        assertEquals(2, sites.size)
        assertEquals(2, sites.distinct().size)
        assertTrue(sites.all { ScorpiaRules.footprintDistance(origin, 5, it) == 2 })
        assertTrue(ScorpiaRules.guardianSites(origin, 5) { false }.isEmpty())
    }

    @Test fun `offspring stop attacking poisoned bystanders but continue queen targets`() {
        assertTrue(ScorpiaRules.offspringAggressive(poisoned = false, attackedByQueen = false))
        assertFalse(ScorpiaRules.offspringAggressive(poisoned = true, attackedByQueen = false))
        assertTrue(ScorpiaRules.offspringAggressive(poisoned = true, attackedByQueen = true))
        assertTrue(ScorpiaRules.offspringAggressive(poisoned = true, attackedByQueen = false, retaliating = true))
    }
}
