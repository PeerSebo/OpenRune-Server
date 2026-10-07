package org.rsmod.content.generic.locs.stile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class StileCrossingTest {
    @Test
    fun `adjacent approaches align with the nearest step in all rotations`() {
        for (angle in 0..3) {
            val stile = stile(angle)
            val first = stile.coords
            val last = first.translate(stile.adjustedWidth - 1, stile.adjustedLength - 1)
            val dx = last.x - first.x
            val dz = last.z - first.z
            assertEquals(StileCrossing(first, last), stileCrossing(stile, first.translate(-dx, -dz)))
            assertEquals(StileCrossing(last, first), stileCrossing(stile, last.translate(dx, dz)))
            assertEquals(StileCrossing(first, last), stileCrossing(stile, first.translate(dz, dx)))
            assertEquals(StileCrossing(last, first), stileCrossing(stile, last.translate(-dz, -dx)))
            val forward = checkNotNull(stileCrossing(stile, first))
            val reverse = checkNotNull(stileCrossing(stile, last))
            assertEquals(last.translate(dx, dz), forward.exit)
            assertEquals(first.translate(-dx, -dz), reverse.exit)
        }
    }

    @Test
    fun `standing on either step preserves the native one-tile crossing`() {
        val stile = stile(0)
        assertEquals(StileCrossing(CoordGrid(3197, 3276), CoordGrid(3197, 3277)), stileCrossing(stile, CoordGrid(3197, 3276)))
        assertEquals(StileCrossing(CoordGrid(3197, 3277), CoordGrid(3197, 3276)), stileCrossing(stile, CoordGrid(3197, 3277)))
    }

    @Test
    fun `remote and other-plane approaches cannot cross`() {
        val stile = stile(0)
        assertNull(stileCrossing(stile, CoordGrid(3197, 3276, 1)))
        assertNull(stileCrossing(stile, CoordGrid(3197, 3274)))
        assertNull(stileCrossing(stile, CoordGrid(3197, 3279)))
    }

    private fun stile(angle: Int) = BoundLocInfo(
        coords = CoordGrid(3197, 3276),
        entity = LocEntity(12982, LocShape.CentrepieceStraight.id, angle),
        layer = 2,
        width = 1,
        length = 2,
        forceApproachFlags = 0,
    )
}
