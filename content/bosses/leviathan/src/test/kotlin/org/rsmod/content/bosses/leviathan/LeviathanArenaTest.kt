package org.rsmod.content.bosses.leviathan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class LeviathanArenaTest {
    @Test
    fun `arena includes copied search tiles only on the instance plane`() {
        val arena = Arena.forBoss(LeviathanArena.BOSS_SPAWN.translate(6400, 6400).copy(level = 2))
        val tile = arena.at(LeviathanArena.SEARCH_SW)
        assertTrue(arena.inSearchBox(tile))
        assertFalse(arena.inSearchBox(CoordGrid(tile.x, tile.z, 1)))
        assertFalse(arena.inSearchBox(tile.translate(-1, 0)))
    }

    @Test
    fun `lightning strips cannot hit a matching tile on a different plane`() {
        val arena = Arena.forBoss(LeviathanArena.BOSS_SPAWN)
        val strip = LeviathanArena.LIGHTNING_STRIPS.first()
        val tile = CoordGrid(strip.minX, strip.minZ, 0)
        assertTrue(arena.inStrip(tile, strip))
        assertFalse(arena.inStrip(CoordGrid(tile.x, tile.z, 1), strip))
    }

    @Test
    fun `copied arena coordinates round trip to their static tiles`() {
        val arena = Arena.forBoss(LeviathanArena.BOSS_SPAWN.translate(6400, 6400))
        for (tile in LeviathanArena.PATHFINDER_CORNERS + LeviathanArena.SEARCH_SW) {
            assertEquals(tile, arena.toStatic(arena.at(tile)))
        }
    }
}
