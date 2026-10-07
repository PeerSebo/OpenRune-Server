package org.rsmod.api.mechanics.stun

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class StunTest {
    @Test fun `fresh and cleared players are not stunned at an uninitialized clock`() {
        val player = Player()
        assertFalse(Stun.isStunned(player))
        Stun.clear(player)
        assertFalse(Stun.isStunned(player))
        assertEquals(0, Stun.movementCyclesRemaining(player))
        assertFalse(Stun.extendActionLock(player, 5))
    }

    @Test fun `movement and action stuns expire independently and weaker reapplication cannot shorten them`() {
        val player = Player().apply { currentMapClock = 100 }
        Stun.apply(player, movementCycles = 4, actionCycles = 8)
        player.currentMapClock = 102
        Stun.apply(player, movementCycles = 1, actionCycles = 1)
        assertEquals(2, Stun.movementCyclesRemaining(player))
        assertEquals(6, Stun.actionCyclesRemaining(player))
        player.currentMapClock = 104
        assertFalse(Stun.isMovementBlocked(player))
        assertTrue(Stun.isActionBlocked(player))
        assertTrue(Stun.extendActionLock(player, 2))
        assertEquals(6, Stun.actionCyclesRemaining(player))
        player.currentMapClock = 110
        assertFalse(Stun.isStunned(player))
        assertFalse(Stun.extendActionLock(player, 1))
    }

    @Test fun `blocked movement cancels routes and warns once per tick including tick zero`() {
        val messages = mutableListOf<Any>()
        val player = Player().apply {
            currentMapClock = 0
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
            routeDestination.add(CoordGrid(3200, 3200))
        }
        Stun.apply(player, 4)
        assertTrue(player.routeDestination.isEmpty())
        repeat(3) { assertTrue(Stun.rejectBlockedMovement(player)) }
        assertEquals(1, messages.count { Stun.BLOCKED_MOVEMENT_MESSAGE in it.toString() })
        player.currentMapClock++
        Stun.rejectBlockedMovement(player)
        assertEquals(2, messages.count { Stun.BLOCKED_MOVEMENT_MESSAGE in it.toString() })
        Stun.clear(player)
        assertFalse(Stun.rejectBlockedMovement(player))
        Stun.apply(player, 1)
        Stun.rejectBlockedMovement(player)
        assertEquals(3, messages.count { Stun.BLOCKED_MOVEMENT_MESSAGE in it.toString() })
    }

    @Test fun `action-only stuns leave routes intact and invalid durations do not mutate them`() {
        val player = Player().apply { currentMapClock = 100 }
        player.routeDestination.add(CoordGrid(3200, 3200))
        Stun.apply(player, 0, 3)
        assertFalse(Stun.isMovementBlocked(player))
        assertEquals(1, player.routeDestination.size)
        assertFalse(Stun.rejectBlockedMovement(player))
        assertThrows(IllegalArgumentException::class.java) { Stun.apply(player, -1, 3) }
        assertThrows(IllegalArgumentException::class.java) { Stun.apply(player, 0, 0) }
        assertEquals(3, Stun.actionCyclesRemaining(player))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
