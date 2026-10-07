package org.rsmod.content.skills.thieving.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.thieving.configs.ThievingDoors
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

class ThievingLocActionTest {
    @Test
    fun `repeated clicks cannot queue another attempt during the opening animation`() {
        val player = player()
        assertTrue(ThievingLocAction.start(player, closed, current, 2))
        player.currentMapClock++
        assertFalse(ThievingLocAction.start(player, closed, current, 2))
        assertEquals(12, player.actionDelay)
        player.currentMapClock++
        assertTrue(ThievingLocAction.start(player, closed, current, 2))
    }

    @Test
    fun `another thief emptying or opening the loc cancels delayed reward`() {
        val player = player()
        assertTrue(ThievingLocAction.start(player, closed, current, 2))
        assertFalse(ThievingLocAction.canFinish(player, closed, current.copy(entity = LocEntity(101, 10, 0))))
        assertFalse(ThievingLocAction.canFinish(player, closed, null))
        assertTrue(ThievingLocAction.canFinish(player, closed, current))
    }

    @Test
    fun `walking or teleporting away cannot finish a queued theft`() {
        val player = player()
        player.coords = CoordGrid(3210, 3210)
        assertFalse(ThievingLocAction.canFinish(player, closed, current))
        player.coords = CoordGrid(3200, 3199, 1)
        assertFalse(ThievingLocAction.canFinish(player, closed, current))
    }

    @Test
    fun `a rejected stale click does not consume the cooldown`() {
        val player = player()
        val before = player.actionDelay
        assertFalse(ThievingLocAction.start(player, closed, null, 2))
        assertEquals(before, player.actionDelay)
    }

    @Test
    fun `wide rotated stall remains reachable from its far edge`() {
        val wide = closed.copy(width = 1, length = 3, entity = LocEntity(100, 10, 1))
        val player = player().apply { coords = CoordGrid(3203, 3200) }
        assertTrue(ThievingLocAction.canFinish(player, wide, current.copy(entity = wide.entity)))
    }

    @Test
    fun `Ardougne chest doors use requirements matching their rooms`() {
        val north = checkNotNull(ThievingDoors.forLoc("loc.picklock1"))
        val south = checkNotNull(ThievingDoors.forLoc("loc.picklock2"))
        assertEquals(1, north.levelReq)
        assertEquals(3.2, north.xp)
        assertEquals(16, south.levelReq)
        assertEquals(15.0, south.xp)
    }

    private fun player() = Player().apply { currentMapClock = 10; coords = CoordGrid(3200, 3199) }
    private val current = LocInfo(2, CoordGrid(3200, 3200), LocEntity(100, 10, 0))
    private val closed = BoundLocInfo(current, 1, 1, 0)
}
