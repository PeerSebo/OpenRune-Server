package org.rsmod.content.bosses.giantmole

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class GiantMoleTest {
    @Test fun `burrow health window excludes dying and finished fights`() {
        for (hp in listOf(0, 1, 9, 101, 200)) assertFalse(GiantMole.canBurrow(hp, HitType.Melee, 20))
        for (hp in listOf(10, 11, 99, 100)) assertTrue(GiantMole.canBurrow(hp, HitType.Melee, 20))
    }

    @Test fun `missed melee and ranged still trigger but splashed magic does not`() {
        assertTrue(GiantMole.canBurrow(75, HitType.Melee, 0))
        assertTrue(GiantMole.canBurrow(75, HitType.Ranged, 0))
        assertFalse(GiantMole.canBurrow(75, HitType.Magic, 0))
        assertTrue(GiantMole.canBurrow(75, HitType.Magic, 1))
    }

    @Test fun `scenery or blocked terrain anywhere under the Mole rejects a burrow landing`() {
        val collision = CollisionFlagMap()
        val origin = CoordGrid(1759, 5189)
        val destination = GiantMole.BURROW_SITES.first { it.chebyshevDistance(origin) >= 8 }
        for (dx in 0..2) for (dz in 0..2) {
            collision[destination.x + dx, destination.z + dz, 0] = 0
        }
        assertTrue(destination in GiantMole.openBurrowSites(origin, 3, collision))
        val farCorner = destination.translate(2, 2)
        for (flag in listOf(CollisionFlag.LOC, CollisionFlag.GROUND_DECOR, CollisionFlag.BLOCK_WALK)) {
            collision[farCorner.x, farCorner.z, 0] = flag
            assertFalse(destination in GiantMole.openBurrowSites(origin, 3, collision), "Accepted blocking flag $flag")
            collision[farCorner.x, farCorner.z, 0] = 0
        }
    }
}
