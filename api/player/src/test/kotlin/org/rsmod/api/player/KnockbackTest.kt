package org.rsmod.api.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnockbackTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `knockback sends interpolated movement with a reaction and cancels queued walking`() {
        for ((dx, dz) in listOf(3 to 0, -3 to 0, 0 to 3, 0 to -3, 3 to 3)) {
            val start = CoordGrid(3204, 3204)
            val player = Player().apply {
                coords = start
                currentMapClock = 100
                processedMapClock = 100
                routeRequest = RouteRequestCoord(start.translateX(5), clientRequest = true)
                routeDestination.add(start.translateX(5))
                anim("seq.human_unarmedpunch")
            }
            val flags = CollisionFlagMap().apply { allocateIfAbsent(start.x, start.z, 0) }
            val end = start.translate(dx, dz)
            player.knockback(end, 512, flags)
            val move = checkNotNull(player.pendingExactMove)
            assertEquals(start.x, player.coords.x + move.deltaX1)
            assertEquals(start.z, player.coords.z + move.deltaZ1)
            assertEquals(end, player.coords)
            assertEquals(0, move.deltaX2)
            assertEquals(0, move.deltaZ2)
            assertEquals(30, move.clientDelay2 - move.clientDelay1)
            assertFalse(player.pendingTelejump)
            assertTrue(player.pendingTeleport)
            assertEquals(MoveSpeed.Walk, player.moveSpeed)
            assertEquals("seq.knockback_30".asRSCM(), player.pendingSequence.id)
            assertEquals(512, move.direction)
            assertNull(player.routeRequest)
            assertTrue(player.routeDestination.isEmpty())
            assertTrue(player.isDelayed)
            player.processedMapClock = 103
            assertFalse(player.isDelayed)
        }
    }

    @Test fun `wall blocked shove still reacts and cannot shorten an existing delay`() {
        val player = Player().apply {
            coords = CoordGrid(3204, 3204)
            currentMapClock = 100
            delay = 110
        }
        player.knockback(player.coords, 0, CollisionFlagMap())
        assertNull(player.pendingExactMove)
        assertEquals("seq.knockback_30".asRSCM(), player.pendingSequence.id)
        assertEquals(110, player.delay)
    }

    @Test fun `knockback rejects another floor before modifying the player`() {
        val player = Player().apply { coords = CoordGrid(3204, 3204) }
        assertThrows(IllegalArgumentException::class.java) {
            player.knockback(CoordGrid(3204, 3204, 1), 0, CollisionFlagMap())
        }
        assertEquals(0, player.level)
        assertNull(player.pendingExactMove)
    }
}
