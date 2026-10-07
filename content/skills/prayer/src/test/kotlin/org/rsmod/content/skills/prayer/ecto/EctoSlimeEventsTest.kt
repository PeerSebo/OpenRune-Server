package org.rsmod.content.skills.prayer.ecto

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EctoSlimeEventsTest {
    @Test
    fun `full inventory fills every three ticks and stops the looping animation`() {
        val f = Fixture(3)
        f.use()
        assertEquals(1, f.player.inv.count("obj.bucket_ectoplasm"))
        assertEquals("seq.quest_ahoy_human_filling_bucket".asRSCM(), f.player.pendingSequence.id)
        repeat(2) { f.tick() }
        assertEquals(1, f.player.inv.count("obj.bucket_ectoplasm"))
        f.tick()
        assertEquals(2, f.player.inv.count("obj.bucket_ectoplasm"))
        repeat(2) { f.tick() }
        assertEquals(2, f.player.inv.count("obj.bucket_ectoplasm"))
        f.tick()
        assertEquals(3, f.player.inv.count("obj.bucket_ectoplasm"))
        repeat(3) { f.tick() }
        assertEquals(0, f.player.inv.count("obj.bucket_empty"))
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        assertTrue(f.player.weakQueueList.isEmpty)
        assertEquals(25, f.player.inv.count("obj.logs"))
        assertEquals(3, f.sounds())
    }

    @Test
    fun `spam clicks cannot fill extra buckets or stack queues`() {
        val f = Fixture(3)
        repeat(4) { f.use() }
        assertEquals(1, f.player.inv.count("obj.bucket_ectoplasm"))
        assertEquals(1, f.player.weakQueueList.count("queue.prayer_ecto_collect_slime"))
        assertEquals(1, f.sounds())
    }

    @Test
    fun `leaving the pool or removing remaining buckets stops further filling`() {
        for (moveAway in listOf(false, true)) {
            val f = Fixture(2)
            f.use()
            if (moveAway) f.player.coords = f.player.coords.translateX(10)
            else f.player.inv[1] = null
            repeat(6) { f.tick() }
            assertEquals(1, f.player.inv.count("obj.bucket_ectoplasm"))
            assertEquals(1, f.sounds())
            assertTrue(f.player.weakQueueList.isEmpty)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        }
    }

    @Test
    fun `missing bucket produces no filling animation sound or queue`() {
        val f = Fixture(0)
        f.use()
        assertEquals(0, f.sounds())
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        assertTrue(f.player.weakQueueList.isEmpty)
    }

    private class Fixture(buckets: Int) {
        val messages = mutableListOf<Any>()
        val player = Player().apply {
            coords = CoordGrid(3680, 9889, 0)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            val bucket = checkNotNull(ServerCacheManager.getItem("obj.bucket_empty".asRSCM()))
            val logs = checkNotNull(ServerCacheManager.getItem("obj.logs".asRSCM()))
            for (slot in 0 until 28) inv[slot] = InvObj(if (slot < buckets) bucket else logs)
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        private val type = checkNotNull(ServerCacheManager.getObject("loc.ahoy_new_green_floor".asRSCM()))
        private val pool = BoundLocInfo(
            LocInfo(2, player.coords.translateX(1), LocEntity(type.id, 10, 0)), type,
        )
        private val launch: (Player, PlayerQueueEvents.Protected<*>) -> Unit = { player, event ->
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, event))
            })
        }
        private val queues = PlayerQueueProcessor::class.java
            .getDeclaredConstructor(EventBus::class.java, Function2::class.java)
            .newInstance(events, launch)

        init {
            with(EctoSlimeEvents()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun use() {
            val bucket = checkNotNull(ServerCacheManager.getItem("obj.bucket_empty".asRSCM()))
            val event = LocUEvents.Op(pool, pool, type, bucket, 0)
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, event))
            })
        }

        fun tick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            queues.process(player)
        }

        fun sounds() = messages.count { it.javaClass.simpleName == "SynthSound" }
    }

    companion object {
        private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
        private var previousTransactions: Any? = null

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            previousTransactions = transactions.get(null)
            transactions.set(null, InvTransactions.from())
        }

        @JvmStatic @AfterAll fun restoreTransactions() {
            transactions.set(null, previousTransactions)
        }
    }
}
