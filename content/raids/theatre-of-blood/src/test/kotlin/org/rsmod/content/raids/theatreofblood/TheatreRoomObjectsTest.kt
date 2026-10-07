package org.rsmod.content.raids.theatreofblood

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class TheatreRoomObjectsTest {
    @Test fun `native chest transform separates ownership from rare status`() {
        assertEquals(0, TheatreRoomObjectRules.chestState(owner = false, rare = false))
        assertEquals(1, TheatreRoomObjectRules.chestState(owner = false, rare = true))
        assertEquals(2, TheatreRoomObjectRules.chestState(owner = true, rare = false))
        assertEquals(3, TheatreRoomObjectRules.chestState(owner = true, rare = true))
    }

    @Test fun `exit search preserves plane and avoids the doorway and neighbouring tiles`() {
        val entry = CoordGrid(100, 200, 1)
        val candidates = TheatreRoomObjectRules.exitCandidates(entry).toList()
        assertEquals(candidates.distinct().size, candidates.size)
        assertTrue(candidates.all { it.level == entry.level })
        assertTrue(candidates.all { tile ->
            kotlin.math.max(kotlin.math.abs(tile.x - entry.x),
                kotlin.math.abs(tile.z - entry.z)) in 2..6
        })
    }
}
