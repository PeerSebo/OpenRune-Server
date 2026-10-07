package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.FarmingBinFill
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinManager
import org.rsmod.api.farming.FarmingBinPhase
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingBinRegistry
import org.rsmod.api.farming.FarmingBinRenderer
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingPatchRegistry
import org.rsmod.api.farming.FarmingTransmitVars
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.farming.FarmingCompanionProtectionHook
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
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
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompostBinFeedingTest {
    private val transactionsField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }
    private val storageField by lazy {
        Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
            .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    }
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private var oldVirtualStorage: InvVirtualStorage? = null

    @BeforeAll
    fun loadCacheAndTransactions() {
        ServerCacheManager.init(241).close()
        oldTransactions = transactionsField.get(null)
        oldStorage = storageField.get(null)
        oldVirtualStorage = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll
    fun restoreTransactions() {
        transactionsField.set(null, oldTransactions)
        storageField.set(null, oldStorage)
        InvVirtualStorageHolder.instance = oldVirtualStorage
    }

    @Test
    fun `using one watermelon feeds the matching inventory items at two tick intervals`() {
        val f = Fixture()
        f.give("obj.watermelon", 3)
        f.give("obj.weeds", 2)
        f.use("obj.watermelon")
        assertEquals(1, f.state.count)
        assertEquals(832, f.player.pendingSequence.id)
        assertEquals(1, f.pendingFills())

        f.tick()
        assertEquals(1, f.state.count)
        f.tick()
        assertEquals(2, f.state.count)
        assertEquals(832, f.player.pendingSequence.id)
        f.tick()
        assertEquals(2, f.state.count)
        f.tick()
        assertEquals(3, f.state.count)
        assertEquals(832, f.player.pendingSequence.id, "The last item's animation must not be reset")
        assertEquals(FarmingBinFill.SUPERCOMPOSTABLE, f.state.fill)
        assertEquals(0, f.player.inv.count("obj.watermelon"))
        assertEquals(2, f.player.inv.count("obj.weeds"), "A batch must not silently mix compost tiers")
        assertEquals(0, f.pendingFills())
        assertEquals(0, f.player.statMap.getFineXP("stat.farming"))
    }

    @Test
    fun `standard dark and guild bins stop at their own capacity without losing excess items`() {
        for (name in listOf("farming_compost_bin_1", "farming_compost_bin_3", "farming_compost_bigbin_guild")) {
            val f = Fixture(name)
            repeat(f.def.capacity - 2) { f.bins.addItem(f.player, f.def, FarmingBinFill.COMPOSTABLE) }
            f.give("obj.weeds", 3)
            f.use("obj.weeds")
            repeat(4) { f.tick() }
            assertEquals(f.def.capacity, f.state.count, name)
            assertEquals(1, f.player.inv.count("obj.weeds"), name)
            assertEquals(0, f.pendingFills(), name)
            assertEquals(FarmingBinPhase.FILLING, f.state.phase, "Filling must not close the bin automatically")
        }
    }

    @Test
    fun `reusing an item replaces the old feed chain rather than stacking it`() {
        val f = Fixture()
        f.give("obj.weeds", 8)
        f.use("obj.weeds")
        f.use("obj.weeds")
        assertEquals(2, f.state.count)
        assertEquals(1, f.pendingFills())
        repeat(2) { f.tick() }
        assertEquals(3, f.state.count)
        assertEquals(5, f.player.inv.count("obj.weeds"))
        assertEquals(1, f.pendingFills())
    }

    @Test
    fun `interruption and forced relocation stop feeding without consuming another item`() {
        for (interrupt in listOf<(Fixture) -> Unit>(
            { it.player.ifClose(it.events) },
            { it.player.coords = it.player.coords.translateX(1) },
            { it.player.walk(it.player.coords.translateX(1)) },
        )) {
            val f = Fixture()
            f.give("obj.weeds", 3)
            f.use("obj.weeds")
            interrupt(f)
            repeat(4) { f.tick() }
            assertEquals(1, f.state.count)
            assertEquals(2, f.player.inv.count("obj.weeds"))
            assertEquals(0, f.pendingFills())
        }
    }

    @Test
    fun `missing materials stop cleanly without an extra animation or duplicated bin item`() {
        val f = Fixture()
        f.give("obj.weeds", 3)
        f.use("obj.weeds")
        for (slot in 0 until 28) f.player.inv[slot] = null
        repeat(2) { f.tick() }
        assertEquals(1, f.state.count)
        assertEquals(EntitySeq.NULL, f.player.pendingSequence)
        assertEquals(0, f.pendingFills())
    }

    @Test
    fun `Dump cancels the feed loop and full or closed bins refuse inventory changes`() {
        val f = Fixture()
        f.give("obj.weeds", 3)
        f.use("obj.weeds")
        f.op(InteractionOp.Op5)
        repeat(2) { f.tick() }
        assertEquals(0, f.state.count)
        assertEquals(2, f.player.inv.count("obj.weeds"))

        repeat(f.def.capacity) { f.bins.addItem(f.player, f.def, FarmingBinFill.COMPOSTABLE) }
        f.use("obj.weeds")
        assertEquals(2, f.player.inv.count("obj.weeds"))
        f.op(InteractionOp.Op1)
        assertEquals(FarmingBinPhase.ROTTING, f.state.phase)
        assertEquals(535, f.player.pendingSequence.id)
        f.use("obj.weeds")
        assertEquals(2, f.player.inv.count("obj.weeds"))
        assertEquals(f.def.capacity, f.state.count)
        assertEquals(0, f.pendingFills())
    }

    @Test
    fun `noted produce remains in inventory and cannot start an automatic batch`() {
        val f = Fixture()
        f.give("obj.cert_watermelon", 4)
        f.use("obj.cert_watermelon")
        repeat(3) { f.tick() }
        assertEquals(0, f.state.count)
        assertEquals(4, f.player.inv.count("obj.cert_watermelon"))
        assertEquals(0, f.pendingFills())
    }

    @Test
    fun `single item deposit works with a full inventory and keeps its pickup animation`() {
        val f = Fixture()
        f.give("obj.weeds", 1)
        f.give("obj.bones", 27)
        f.use("obj.weeds")
        assertEquals(1, f.state.count)
        assertEquals(27, f.player.inv.count("obj.bones"))
        assertEquals(832, f.player.pendingSequence.id)
        assertEquals(0, f.pendingFills())
        assertEquals(2, checkNotNull(ServerCacheManager.getAnim(832)).tickDuration)
        assertEquals(2, checkNotNull(ServerCacheManager.getAnim(535)).tickDuration)
    }

    private class Fixture(binKey: String = "farming_compost_bin_1") {
        val clock = MapClock(100)
        val client = RecordingClient()
        val player = Player().apply {
            client = this@Fixture.client
            coords = CoordGrid(3056, 3312)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val events = EventBus()
        private val registry = FarmingBinRegistry()
        val bins = FarmingBinManager(registry, FarmingBinRenderer(FarmingTransmitVars()))
        val def = checkNotNull(registry.byKey(FarmingBinKey(binKey)))
        val state get() = bins.state(player, def)
        private val locType = checkNotNull(ServerCacheManager.getObject(checkNotNull(def.locId)))
        private val loc = BoundLocInfo(
            LocInfo(2, CoordGrid(3057, 3312), LocEntity(locType.id, 10, 0)), locType,
        )
        private val contextFactory = unused<ProtectedAccessContextFactory>().also {
            field(it, "eventBus", events)
            field(it, "random", DefaultGameRandom(1))
        }
        private val launcher = ProtectedAccessLauncher(contextFactory)
        private val queues = PlayerQueueProcessor(events, launcher)
        private val locInteractions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
        private val locUse = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        init {
            val xpMods = XpModifiers(emptySet())
            val objRepo = unused<ObjRepository>()
            val binScript = CompostBinScript(bins, objRepo, xpMods)
            val script = FarmingScript(
                unused<FarmingManager>(), FarmingCropRegistry(FarmingPatchRegistry()),
                objRepo, xpMods, unused<FarmingCompanionProtectionHook>(), events, bins, binScript,
            )
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(binScript) { context.startup() }
            with(script) { context.startup() }
        }

        fun give(name: String, count: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
            if (type.isStackable) player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type, count)
            else repeat(count) { player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type) }
        }

        fun use(name: String) {
            val item = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            assertTrue(launcher.launch(player) {
                val event = with(locUse) { opTrigger(loc, loc, locType, item, slot) }
                checkNotNull(event) { "Missing category item-on-bin handler" }
                assertTrue(events.publish(this, event))
            })
        }

        fun op(op: InteractionOp) {
            val event = checkNotNull(locInteractions.opTrigger(player, loc, op))
            assertTrue(launcher.launch(player) { assertTrue(events.publish(this, event)) })
        }

        fun pendingFills() = player.weakQueueList.count(FarmingParams.QUEUE_BIN_TAKE)

        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            player.pendingSequence = EntitySeq.NULL
            queues.process(player)
        }
    }

    @Test
    fun `compost bucket warning is immediate and a removed bucket cannot consume compost`() {
        val f = Fixture()
        f.state.apply { phase = FarmingBinPhase.OPEN; product = FarmingBinProduct.COMPOST; count = 2 }
        f.op(InteractionOp.Op1)
        assertTrue(f.client.messages.any { FarmingMessages.BIN_NEED_BUCKET in it.toString() })
        assertEquals(0, f.pendingFills())
        assertEquals(2, f.state.count)
        f.give("obj.bucket_empty", 1)
        f.op(InteractionOp.Op1)
        f.player.inv[0] = null
        repeat(4) { f.tick() }
        assertEquals(2, f.state.count)
        assertEquals(0, f.player.inv.count("obj.bucket_compost"))
    }

    @Test
    fun `only successful deposits and closing send their respective sounds`() {
        val f = Fixture()
        f.give("obj.weeds", 2)
        f.use("obj.weeds")
        repeat(2) { f.tick() }
        assertEquals(2, f.client.messages.count { "SynthSound(id=2441," in it.toString() })
        repeat(f.def.capacity - 2) { f.bins.addItem(f.player, f.def, FarmingBinFill.COMPOSTABLE) }
        f.op(InteractionOp.Op1)
        assertEquals(1, f.client.messages.count { "SynthSound(id=2428," in it.toString() })
        f.op(InteractionOp.Op1)
        assertEquals(1, f.client.messages.count { "SynthSound(id=2428," in it.toString() })
    }

    @Test
    fun `collecting the last bin product awards its original item and fractional XP`() {
        val rewards = listOf(
            Triple(FarmingBinProduct.COMPOST, "obj.bucket_compost", 45),
            Triple(FarmingBinProduct.SUPERCOMPOST, "obj.bucket_supercompost", 85),
            Triple(FarmingBinProduct.ULTRACOMPOST, "obj.bucket_ultracompost", 100),
            Triple(FarmingBinProduct.ROTTEN_TOMATOES, "obj.rotten_tomato", 45),
        )
        for ((product, item, fineXp) in rewards) {
            val f = Fixture()
            f.state.apply { phase = FarmingBinPhase.OPEN; this.product = product; count = 1 }
            if (product != FarmingBinProduct.ROTTEN_TOMATOES) f.give("obj.bucket_empty", 1)
            f.op(InteractionOp.Op1)
            assertEquals(0, f.player.inv.count(item))
            repeat(4) { f.tick() }
            assertEquals(1, f.player.inv.count(item), product.name)
            assertEquals(0, f.player.inv.count("obj.bucket_empty"), product.name)
            assertEquals(fineXp, f.player.statMap.getFineXP("stat.farming"), product.name)
            assertEquals(0, f.state.count, product.name)
            assertEquals(0, f.pendingFills(), product.name)
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
        fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }

        inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
