package org.rsmod.content.bosses.gemstonecrab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class GemstoneCrabNotificationsTest {
    @Test
    fun `burrow notifications stay near the current crab including its far edge`() {
        val crab = crab()
        val near = player(1, crab.coords.translate(-1, 0))
        val farEdge = player(2, crab.coords.translate(crab.size, 0))
        val outside = player(3, crab.coords.translate(crab.size + 16, 0))
        val otherPlane = player(4, CoordGrid(crab.coords.x, crab.coords.z, 1))
        val otherSite = player(5, CoordGrid(1351, 3110, 0))
        val recipients = listOf(near, farEdge, outside, otherPlane, otherSite)

        GemstoneCrabNotifications.announceBurrow(crab, recipients, listOf("Alice", "Bob", "Carol"), setOf(1L, 3L))

        assertEquals(3, messages(near).size)
        assertTrue(messages(near).last().contains("mine from its remains"))
        assertEquals(2, messages(farEdge).size)
        assertTrue(messages(farEdge).last().contains("Alice, Bob, & Carol"))
        for (player in listOf(outside, otherPlane, otherSite)) assertTrue(messages(player).isEmpty())
    }

    @Test
    fun `leaving before the announcement suppresses even the personal mining notice`() {
        val crab = crab()
        val player = player(1, crab.coords.translate(-1, 0))
        val eligibility = setOf(1L)
        player.coords = CoordGrid(3222, 3218, 0)
        GemstoneCrabNotifications.announceBurrow(crab, listOf(player), listOf("Alice"), eligibility)
        assertTrue(messages(player).isEmpty())
        assertEquals(setOf(1L), eligibility)
    }

    @Test
    fun `an unclaimed crab only announces its departure locally`() {
        val crab = crab()
        val player = player(1, crab.coords.translate(-1, 0))
        GemstoneCrabNotifications.announceBurrow(crab, listOf(player), emptyList(), emptySet())
        assertEquals(listOf("The gemstone crab burrows away, leaving a piece of its shell behind."), messages(player))
    }

    private fun crab() = Npc(requireNotNull(ServerCacheManager.getNpc("npc.gemstone_crab".asRSCM())), CoordGrid(1271, 3171, 0))
    private fun player(id: Long, tile: CoordGrid) = Player().apply { uuid = id; coords = tile; client = RecordingClient() }
    private fun messages(player: Player) = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>().map { it.message }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
