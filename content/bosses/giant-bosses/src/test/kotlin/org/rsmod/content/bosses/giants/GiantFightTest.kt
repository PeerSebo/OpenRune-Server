package org.rsmod.content.bosses.giants

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class GiantFightTest {
    @Test fun `first entry needs a key but an unlocked entrance remains free`() {
        assertFalse(GiantRules.mayEnter(false, 0))
        assertTrue(GiantRules.mayEnter(false, 1))
        assertTrue(GiantRules.mayEnter(true, 0))
    }

    @Test fun `chest cannot be claimed alive or twice after one death`() {
        val life = GiantLife()
        assertFalse(life.attemptChest(10))
        assertFalse(life.ready(100))
        assertTrue(life.died())
        assertFalse(life.died())
        assertTrue(life.attemptChest(20))
        assertFalse(life.attemptChest(21))
        assertEquals(35, life.restartAt)
        assertFalse(life.ready(34))
        assertTrue(life.ready(35))
    }

    @Test fun `restart clock begins on the attempt including an empty inventory`() {
        val life = GiantLife()
        life.died()
        assertFalse(life.ready(10000))
        life.attemptChest(10000)
        assertEquals(10015, life.restartAt)
    }

    @Test fun `ordinary growthling hits retain finishing step and cutting tools one shot`() {
        assertEquals(9, GiantRules.growthlingDamage(10, 80, false))
        assertEquals(0, GiantRules.growthlingDamage(1, 80, false))
        assertEquals(10, GiantRules.growthlingDamage(10, 0, true))
        assertEquals(1, GiantRules.growthlingDamage(1, 0, true))
        assertEquals(0, GiantRules.growthlingDamage(0, 80, false))
        assertEquals(0, GiantRules.growthlingDamage(0, 0, true))
    }

    @Test fun `Obor ranged prayer halves rather than nullifies incoming damage`() {
        assertEquals(26, GiantRules.oborRangedDamage(26, false))
        assertEquals(13, GiantRules.oborRangedDamage(26, true))
        assertEquals(12, GiantRules.oborRangedDamage(25, true))
        assertEquals(0, GiantRules.oborRangedDamage(0, true))
    }

    @Test fun `giant footprint range measures each edge and excludes other floors`() {
        val origin = CoordGrid(3220, 9933)
        assertEquals(1, GiantRules.distance(origin, 3, CoordGrid(3223, 9934)))
        assertEquals(10, GiantRules.distance(origin, 3, CoordGrid(3232, 9934)))
        assertEquals(11, GiantRules.distance(origin, 3, CoordGrid(3233, 9934)))
        assertEquals(Int.MAX_VALUE, GiantRules.distance(origin, 3, CoordGrid(3221, 9934, 1)))
    }
}
