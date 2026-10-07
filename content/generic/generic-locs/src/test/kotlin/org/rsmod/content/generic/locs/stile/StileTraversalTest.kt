package org.rsmod.content.generic.locs.stile

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class StileTraversalTest {
    @Test
    fun `both crossing directions use the wall climb sequence and remain protected until landing`() {
        for (angle in 0..3) for (reverse in listOf(false, true)) {
            val f = Fixture(angle)
            val near = f.stile.coords
            val far = near.translate(f.stile.adjustedWidth - 1, f.stile.adjustedLength - 1)
            val start = if (reverse) far else near
            val end = if (reverse) near else far
            f.player.coords = start
            f.player.pendingSequence = EntitySeq(828, 0, 5)
            f.click()

            assertEquals(839, f.player.pendingSequence.id)
            assertEquals(30, f.player.pendingSequence.delay)
            assertEquals(end, f.player.coords)
            val direction = when {
                end.z > start.z -> 1024
                end.z < start.z -> 0
                end.x > start.x -> 1536
                else -> 512
            }
            assertEquals(EntityExactMove(start.x - end.x, start.z - end.z, 0, 0, 30, 94, direction), f.player.pendingExactMove)
            assertTrue(f.player.isAccessProtected)
            f.tick(3)
            assertTrue(f.player.isAccessProtected)
            f.tick()
            assertTrue(f.player.isAccessProtected)
            assertEquals(end, f.player.coords)
            val exit = checkNotNull(stileCrossing(f.stile, start)).exit
            assertEquals(listOf(exit), f.player.routeDestination.toList())
            assertTrue(f.player.forcedRoute)
            f.player.coords = exit
            f.tick()
            assertFalse(f.player.isAccessProtected)
            assertEquals(EntitySeq.NULL, f.player.pendingSequence)
            assertFalse(f.player.forcedRoute)
            assertFalse(f.client.messages.any { "SynthSound" in it.toString() })
        }
    }

    @Test
    fun `adjacent approach walks onto the step before starting the climb`() {
        val f = Fixture(0)
        val start = f.stile.coords
        f.player.coords = start.translate(0, -1)
        f.click()
        assertEquals(listOf(start), f.player.routeDestination.toList())
        assertNull(f.player.pendingExactMove)
        assertEquals(EntitySeq.NULL, f.player.pendingSequence)
        f.player.coords = start
        f.tick()
        assertEquals(839, f.player.pendingSequence.id)
        assertFalse(f.player.forcedRoute)
        assertTrue(f.player.routeDestination.isEmpty())
        f.tick(4)
        assertTrue(f.player.isAccessProtected)
        f.player.coords = checkNotNull(stileCrossing(f.stile, start)).exit
        f.tick()
        assertFalse(f.player.isAccessProtected)
    }

    @Test
    fun `cancelling the crossing clears the climbing sequence`() {
        val f = Fixture(0)
        f.player.coords = f.stile.coords
        f.click()
        assertEquals(839, f.player.pendingSequence.id)
        f.player.cancelActiveCoroutine()
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
    }

    private class Fixture(angle: Int) {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
        }
        private val type = checkNotNull(ServerCacheManager.getObject("loc.farming_style".asRSCM()))
        val stile = BoundLocInfo(LocInfo(2, CoordGrid(3197, 3276), LocEntity(type.id, 10, angle)), type)
        private val events = EventBus()
        private val collision = CollisionFlagMap().apply {
            for (dx in -2..2) for (dz in -2..2) allocateIfAbsent(stile.x + dx, stile.z + dz, stile.level)
        }
        private val updates = ZoneUpdateMap()
        private val world = WorldRepository(updates)
        private val access = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision }, getWorldRepo = { world },
        )
        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(StileScript(world)) { context.startup() }
        }
        fun click() {
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, access) {
                assertTrue(events.publish(this, LocEvents.Op1(stile, stile, type)))
            })
        }
        fun tick(count: Int = 1) = repeat(count) {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.pendingSequence = EntitySeq.NULL
            player.activeCoroutine?.advance()
        }
    }

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
