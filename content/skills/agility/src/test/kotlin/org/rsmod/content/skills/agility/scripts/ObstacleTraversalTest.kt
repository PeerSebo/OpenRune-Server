package org.rsmod.content.skills.agility.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.game.entity.Player
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid

class ObstacleTraversalTest {
    @Test
    fun `cancelling a suspended obstacle clears its animation and skips its landing`() {
        val player = Player()
        var landed = false
        val coroutine = player.launch {
            player.withAgilityAnimation(preserveOnCompletion = true) {
                player.pendingSequence = obstacleSequence
                pause { false }
                landed = true
            }
        }

        assertTrue(coroutine.isSuspended)
        assertEquals(obstacleSequence, player.pendingSequence)
        player.cancelActiveCoroutine()

        assertEquals(EntitySeq.ZERO, player.pendingSequence)
        assertFalse(landed)
        assertTrue(coroutine.isIdle)
    }

    @Test
    fun `new action animation survives cancellation of the previous obstacle`() {
        val player = Player()
        player.launch {
            player.withAgilityAnimation {
                player.pendingSequence = obstacleSequence
                pause { false }
            }
        }
        val nextSequence = EntitySeq(id = 1234, delay = 0, priority = 5)

        player.launch { player.pendingSequence = nextSequence }

        assertEquals(nextSequence, player.pendingSequence)
    }

    @Test
    fun `normal obstacle completion clears its animation after the landing`() {
        val player = Player()
        var resume = false
        var landed = false
        val coroutine = player.launch {
            player.withAgilityAnimation {
                player.pendingSequence = obstacleSequence
                pause { resume }
                landed = true
            }
        }

        resume = true
        coroutine.advance()

        assertTrue(landed)
        assertEquals(EntitySeq.ZERO, player.pendingSequence)
    }

    @Test
    fun `normal handler completion can preserve its final animation beat`() {
        val player = Player()
        player.launch {
            player.withAgilityAnimation(preserveOnCompletion = true) {
                player.pendingSequence = obstacleSequence
            }
        }

        assertEquals(obstacleSequence, player.pendingSequence)
    }

    @Test
    fun `presentation error clears the animation and propagates the original failure`() {
        val player = Player()
        val failure = IllegalStateException("failed presentation")

        val thrown = assertThrows(IllegalStateException::class.java) {
            player.launch {
                player.withAgilityAnimation {
                    player.pendingSequence = obstacleSequence
                    throw failure
                }
            }
        }

        assertSame(failure, thrown)
        assertEquals(EntitySeq.ZERO, player.pendingSequence)
    }

    @Test
    fun `mount on another plane is rejected before requesting or forcing movement`() {
        val player = Player().apply { coords = CoordGrid(3090, 3277, 0) }
        val originalRoute = CoordGrid(3091, 3277, 0)
        player.routeDestination.addAll(listOf(originalRoute))
        var reached: Boolean? = null

        val coroutine = player.launch {
            reached = ProtectedAccess(player, this, unusedContext()).approachMount(CoordGrid(3090, 3277, 3))
        }

        assertEquals(false, reached)
        assertTrue(coroutine.isIdle)
        assertFalse(player.forcedRoute)
        assertEquals(CoordGrid(3090, 3277, 0), player.coords)
        assertEquals(originalRoute, player.routeDestination.peekFirst())
    }

    @Test
    fun `already standing on the mount succeeds without a delay`() {
        val mount = CoordGrid(3090, 3277, 3)
        val player = Player().apply { coords = mount }
        var reached: Boolean? = null

        val coroutine = player.launch {
            reached = ProtectedAccess(player, this, unusedContext()).approachMount(mount)
        }

        assertEquals(true, reached)
        assertTrue(coroutine.isIdle)
        assertEquals(mount, player.coords)
    }

    private fun unusedContext() = ProtectedAccessContext(
        getRandom = { error("Unexpected random access") },
        getEventBus = { error("Unexpected event access") },
        getNpcList = { error("Unexpected NPC access") },
        getPlayerList = { error("Unexpected player-list access") },
        getCollision = { error("Unexpected collision access") },
        getAreaChecker = { error("Unexpected area access") },
        getAlignment = { error("Unexpected dialogue access") },
        getLocInteractions = { error("Unexpected loc access") },
        getNpcInteractions = { error("Unexpected NPC interaction") },
        getPlayerInteractions = { error("Unexpected player interaction") },
        getHeldInteractions = { error("Unexpected held interaction") },
        getWornInteractions = { error("Unexpected worn interaction") },
        getMusicPlayer = { error("Unexpected music access") },
        getMarketPrices = { error("Unexpected market access") },
        getInstantHitProcessor = { error("Unexpected damage access") },
        getTeleportValidator = { error("Unexpected teleport access") },
        getWorldRepo = { error("Unexpected world access") },
        getHitModifier = { error("Unexpected hit modifier") },
    )

    private val obstacleSequence = EntitySeq(id = 2583, delay = 0, priority = 5)
}
