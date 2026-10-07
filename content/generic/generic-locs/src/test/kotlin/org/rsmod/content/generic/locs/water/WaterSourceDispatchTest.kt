package org.rsmod.content.generic.locs.water

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaterSourceDispatchTest {
    private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    private val storage = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    private var previousTransactions: Any? = null
    private var previousStorage: Any? = null
    private var previousVirtual: InvVirtualStorage? = null

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
        previousTransactions = transactions.get(null)
        previousStorage = storage.get(null)
        previousVirtual = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll fun restore() {
        transactions.set(null, previousTransactions)
        storage.set(null, previousStorage)
        InvVirtualStorageHolder.instance = previousVirtual
    }

    @Test fun `empty and partially full cans refill at every ordinary source family`() {
        for (source in listOf("loc.qip_cook_sink", "loc.fountain", "loc.fai_falador_waterpump",
            "loc.kebos_farming_guild_watertap", "loc.farm_water_barrel1")) {
            for (dose in 0..7) {
                val f = Fixture(source)
                f.put(9, "obj.watering_can_$dose")
                f.use(9)
                assertEquals(item("obj.watering_can_8").id, f.player.inv[9]?.id, "$source dose $dose")
                assertEquals(9401, f.player.pendingSequence.id)
                assertTrue(f.client.messages.any { "2609" in it.toString() })
            }
        }
    }

    @Test fun `refilling preserves the selected slot even in a completely full backpack`() {
        val f = Fixture("loc.fountain")
        for (slot in 0 until 28) f.put(slot, "obj.bones")
        f.put(2, "obj.watering_can_3")
        f.put(20, "obj.watering_can_3")
        f.use(20)
        assertEquals(item("obj.watering_can_3").id, f.player.inv[2]?.id)
        assertEquals(item("obj.watering_can_8").id, f.player.inv[20]?.id)
        assertEquals(26, f.player.inv.count("obj.bones"))
        assertEquals(0, f.player.inv.freeSpace())
    }

    @Test fun `a full can stays unchanged and does not start filling`() {
        val f = Fixture("loc.fountain")
        f.put(4, "obj.watering_can_8")
        val sequence = f.player.pendingSequence
        f.use(4)
        assertEquals(1, f.player.inv.count("obj.watering_can_8"))
        assertEquals(sequence, f.player.pendingSequence)
        assertTrue(f.player.weakQueueList.isEmpty)
        assertTrue(f.client.messages.any { "already full" in it.toString() })
    }

    @Test fun `wells and broken sinks do not gain watering can handlers`() {
        for (source in listOf("loc.well", "loc.fai_falador_well", "loc.con_contract_hosidius_north_sink_broken")) {
            val f = Fixture(source)
            for (dose in 0..8) {
                f.put(0, "obj.watering_can_$dose")
                f.launcher.launch(f.player) { assertNull(f.event(this, 0)) }
                assertEquals(item("obj.watering_can_$dose").id, f.player.inv[0]?.id)
            }
        }
    }

    @Test fun `can batches refill matching doses without touching another container family`() {
        val f = Fixture("loc.fountain")
        f.put(0, "obj.watering_can_0")
        f.put(1, "obj.watering_can_0")
        f.put(2, "obj.watering_can_4")
        f.put(3, "obj.bucket_empty")
        f.use(0)
        repeat(3) { f.tick() }
        assertEquals(2, f.player.inv.count("obj.watering_can_8"))
        assertEquals(1, f.player.inv.count("obj.watering_can_4"))
        assertEquals(1, f.player.inv.count("obj.bucket_empty"))
        assertTrue(f.player.weakQueueList.isEmpty)
    }

    @Test fun `ordinary jug and bucket fill batches still work`() {
        for ((empty, filled) in listOf("obj.jug_empty" to "obj.jug_water", "obj.bucket_empty" to "obj.bucket_water")) {
            val f = Fixture("loc.fountain")
            f.put(0, empty)
            f.put(1, empty)
            f.use(1)
            repeat(3) { f.tick() }
            assertEquals(2, f.player.inv.count(filled))
            assertEquals(0, f.player.inv.count(empty))
        }
    }

    private class Fixture(source: String) {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = 100
            processedMapClock = 100
            coords = CoordGrid(3200, 3200)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val context = unused<ProtectedAccessContextFactory>().also {
            field(it, "eventBus", events)
        }
        val launcher = ProtectedAccessLauncher(context)
        private val queues = PlayerQueueProcessor(events, launcher)
        private val type = checkNotNull(ServerCacheManager.getObject(source.asRSCM()))
        private val loc = BoundLocInfo(LocInfo(2, CoordGrid(3201, 3200), LocEntity(type.id, 10, 0)), type)
        private val interactions = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        init {
            with(WaterSourceScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun put(slot: Int, name: String) { player.inv[slot] = InvObj(item(name)) }

        fun event(access: ProtectedAccess, slot: Int) = with(interactions) {
            access.opTrigger(loc, loc, type, checkNotNull(ServerCacheManager.getItem(player.inv[slot]!!.id)), slot)
        }

        fun use(slot: Int) {
            assertTrue(launcher.launch(player) { assertTrue(events.publish(this, checkNotNull(event(this, slot)))) })
        }

        fun tick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.pendingSequence = EntitySeq.NULL
            queues.process(player)
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

    private companion object {
        fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
        fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }
        inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
