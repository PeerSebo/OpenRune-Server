package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import net.rsprot.protocol.game.incoming.buttons.IfButtonT
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.*
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.net.rsprot.handlers.IfButtonTHandler
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.farming.FarmingCompanionProtectionHook
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.content.skills.farming.configs.FarmingSaplingData
import org.rsmod.events.EventBus
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
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FarmingWateringDispatchTest {
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

    @Test fun `watering a growing patch spends one dose after three ticks without clipping the animation`() {
        val f = Fixture()
        f.put(0, "obj.watering_can_8")
        f.useOnPatch(0)
        f.assertWaterAnimation()
        assertFalse(f.state.watered)
        repeat(2) { f.tick(); assertEquals(EntitySeq.NULL, f.player.pendingSequence) }
        assertFalse(f.state.watered)
        f.tick()
        assertTrue(f.state.watered)
        assertEquals(1, f.player.inv.count("obj.watering_can_7"))
        assertEquals(EntitySeq.NULL, f.player.pendingSequence, "Completion must not send sequence -1 before the native return pose")
        repeat(3) { f.tick(); assertEquals(EntitySeq.NULL, f.player.pendingSequence) }
        assertEquals(1, f.client.messages.count { "water the" in it.toString() })
    }

    @Test fun `seedling watering uses the same full animation and swaps safely with a full backpack`() {
        val f = Fixture()
        val oak = checkNotNull(FarmingCrops.byId("oak"))
        f.put(0, "obj.watering_can_1")
        f.put(1, checkNotNull(oak.potSeed))
        for (slot in 2 until 28) f.put(slot, "obj.bones")
        f.useOnSeedling()
        f.assertWaterAnimation()
        f.processInputQueues()
        repeat(2) { f.tick() }
        assertEquals(1, f.player.inv.count(checkNotNull(oak.potSeed)))
        f.tick()
        assertEquals(1, f.player.inv.count(checkNotNull(oak.potSeedWatered)))
        assertEquals(1, f.player.inv.count("obj.watering_can_0"))
        assertEquals(26, f.player.inv.count("obj.bones"))
        assertEquals(EntitySeq.NULL, f.player.pendingSequence)
    }

    @Test fun `tree seeds can be potted in either item selection order with a full inventory`() {
        for (crop in FarmingSaplingData.all) {
            for (pot in FarmingParams.FILLED_PLANT_POTS) for (reverse in listOf(false, true)) {
                val f = Fixture()
                f.put(0, crop.seed)
                f.put(1, pot)
                f.put(2, FarmingParams.GARDENING_TROWEL)
                for (slot in 3 until 28) f.put(slot, "obj.bones")
                f.useItems(if (reverse) 1 else 0, if (reverse) 0 else 1)
                assertEquals(1, f.player.inv.count(checkNotNull(crop.potSeed)), crop.id)
                assertEquals(0, f.player.inv.count(crop.seed), crop.id)
                assertEquals(0, f.player.inv.count(pot), crop.id)
                assertEquals(1, f.player.inv.count(FarmingParams.GARDENING_TROWEL))
                assertEquals(25, f.player.inv.count("obj.bones"))
            }
        }
    }

    @Test fun `an empty watering can never animates or waters a patch`() {
        val f = Fixture()
        f.put(0, "obj.watering_can_0")
        val before = f.player.pendingSequence
        f.useOnPatch(0)
        assertEquals(before, f.player.pendingSequence)
        repeat(5) { f.tick() }
        assertFalse(f.state.watered)
        assertEquals(1, f.player.inv.count("obj.watering_can_0"))
        assertTrue(f.player.weakQueueList.isEmpty)
    }

    @Test fun `seed potting refuses empty pots missing trowels and low levels without losing items`() {
        val recipe = FarmingSaplingData.all.first { it.id == "oak" }
        for (reason in listOf("empty", "tool", "level")) {
            val f = Fixture()
            val pot = if (reason == "empty") FarmingParams.EMPTY_PLANT_POT else FarmingParams.FILLED_PLANT_POT
            f.put(0, recipe.seed)
            f.put(1, pot)
            if (reason != "tool") f.put(2, FarmingParams.GARDENING_TROWEL)
            if (reason == "level") f.player.statMap.setCurrentLevel("stat.farming", 1)
            f.useItems(0, 1)
            assertEquals(1, f.player.inv.count(recipe.seed))
            assertEquals(1, f.player.inv.count(pot))
            assertEquals(0, f.player.inv.count(recipe.potSeed))
            assertTrue(f.client.messages.any { message ->
                when (reason) {
                    "empty" -> "fill the plant pot" in message.toString()
                    "tool" -> FarmingMessages.NEED_TROWEL in message.toString()
                    else -> "level" in message.toString()
                }
            }, reason)
        }
    }

    @Test fun `magic seed and filled pot work through the actual inventory use packet`() {
        val seeds = listOf("obj.magic_tree_seed") + item("obj.magic_tree_seed").countObj.orEmpty()
            .filter { it > 0 }.distinct().map { RSCM.getReverseMapping(RSCMType.OBJ, it) }
        for (seed in seeds) for (pot in FarmingParams.FILLED_PLANT_POTS) for (reverse in listOf(false, true)) {
            val f = Fixture()
            f.put(0, seed)
            f.put(1, pot)
            f.put(2, FarmingParams.GARDENING_TROWEL)
            f.usePacket(if (reverse) 1 else 0, if (reverse) 0 else 1)
            assertEquals(1, f.player.inv.count("obj.plantpot_magic_tree_seed"))
            assertFalse(f.client.messages.any { "Nothing interesting happens" in it.toString() })
        }
    }

    @Test fun `potting consumes the selected soil-filled pot and preserves the other variant`() {
        val magic = FarmingSaplingData.all.first { it.seed == "obj.magic_tree_seed" }
        for (pot in FarmingParams.FILLED_PLANT_POTS) {
            val other = FarmingParams.FILLED_PLANT_POTS.first { it != pot }
            val f = Fixture()
            f.put(0, magic.seed)
            f.put(1, pot)
            f.put(2, FarmingParams.GARDENING_TROWEL)
            f.put(3, other)
            f.usePacket(0, 1)
            assertEquals(0, f.player.inv.count(pot))
            assertEquals(1, f.player.inv.count(other))
            assertEquals(1, f.player.inv.count(magic.potSeed))
        }
    }

    @Test fun `alternative filled pots still require a trowel and the seed's Farming level`() {
        val magic = FarmingSaplingData.all.first { it.seed == "obj.magic_tree_seed" }
        for (pot in FarmingParams.FILLED_PLANT_POTS) for (missingTool in listOf(false, true)) {
            val f = Fixture()
            f.put(0, magic.seed)
            f.put(1, pot)
            if (!missingTool) {
                f.put(2, FarmingParams.GARDENING_TROWEL)
                f.player.statMap.setCurrentLevel("stat.farming", (magic.levelReq - 1).toByte())
            }
            f.usePacket(0, 1)
            assertEquals(1, f.player.inv.count(magic.seed))
            assertEquals(1, f.player.inv.count(pot))
            assertEquals(0, f.player.inv.count(magic.potSeed))
            assertTrue(f.client.messages.any {
                if (missingTool) FarmingMessages.NEED_TROWEL in it.toString()
                else "level" in it.toString()
            })
        }
    }

    @Test fun `every watered seedling grows into its matching sapling after the timer`() {
        for (recipe in FarmingSaplingData.all) {
            val f = Fixture()
            f.put(0, "obj.watering_can_1")
            f.put(1, recipe.potSeed)
            f.useOnSeedling()
            f.processInputQueues()
            repeat(3) { f.tick() }
            assertEquals(1, f.player.inv.count(recipe.potSeedWatered), recipe.id)
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                f.player, FarmingParams.VARP_SEEDLING_READY, FarmingClock.currentMinute().toInt() - 1,
            )
            repeat(FarmingParams.SEEDLING_QUEUE_TICKS + 1) { f.tick() }
            assertEquals(1, f.player.inv.count(recipe.sapling), recipe.id)
            assertEquals(0, f.player.inv.count(recipe.potSeedWatered), recipe.id)
            assertEquals(1, f.player.inv.count("obj.watering_can_0"), recipe.id)
        }
    }

    @Test fun `a patch that no longer needs water cannot consume a can dose`() {
        val f = Fixture()
        f.put(0, "obj.watering_can_8")
        f.useOnPatch(0)
        f.state.dead = true
        repeat(3) { f.tick() }
        assertFalse(f.state.watered)
        assertEquals(1, f.player.inv.count("obj.watering_can_8"))
        assertEquals(0, f.player.inv.count("obj.watering_can_7"))
    }

    @Test fun `missing harvest tools are reported immediately without scheduling a pick`() {
        for ((patch, crop) in listOf("farming_veg_patch_1" to "potato", "farming_herb_patch_1" to "guam")) {
            val f = Fixture(patch, crop)
            f.grow()
            f.put(0, "obj.secateurs")
            f.harvest()
            assertTrue(f.client.messages.any { FarmingMessages.NEED_SPADE in it.toString() })
            assertTrue(f.player.weakQueueList.isEmpty)
            assertEquals(1, f.state.lives)
        }
    }

    @Test fun `herbs need a spade but no secateurs and recheck the tool before yielding`() {
        val f = Fixture("farming_herb_patch_1", "guam")
        f.grow()
        f.put(0, "obj.spade")
        f.harvest()
        f.player.inv[0] = null
        repeat(3) { f.tick() }
        assertEquals(0, f.player.inv.count("obj.unidentified_guam"))
        assertTrue(f.client.messages.any { FarmingMessages.NEED_SPADE in it.toString() })
        f.put(0, "obj.spade")
        f.harvest()
        repeat(3) { f.tick() }
        assertEquals(1, f.player.inv.count("obj.unidentified_guam"))
    }

    @Test fun `the final limpwurt harvest retains the picking animation`() {
        val f = Fixture("farming_flower_patch_1", "limpwurt")
        f.grow()
        f.harvest()
        repeat(3) { f.tick() }
        assertEquals(1, f.player.inv.count("obj.limpwurt_root"))
        assertEquals("obj.limpwurt_root", f.completions.single().target)
        assertEquals(2282, f.player.pendingSequence.id)
        assertTrue(f.player.weakQueueList.isEmpty)
        assertEquals(null, f.state.cropId)
    }

    @Test fun `magic tree health and experience are awarded immediately and only once`() {
        val f = Fixture("farming_tree_patch_3", "magic")
        f.grow()
        val before = f.player.statMap.getXP("stat.farming")
        f.harvest()
        assertTrue(f.state.checkHealthClaimed)
        assertTrue(f.player.statMap.getXP("stat.farming") > before)
        assertTrue(f.player.weakQueueList.isEmpty)
        val checked = f.player.statMap.getXP("stat.farming")
        f.harvest()
        repeat(4) { f.tick() }
        assertEquals(checked, f.player.statMap.getXP("stat.farming"))
        assertEquals(1, f.client.messages.count { "perfect health" in it.toString() })
    }

    private class Fixture(patchName: String = "farming_veg_patch_1", cropName: String = "potato") {
        val events = EventBus()
        val completions = mutableListOf<PlayerActionCompleteEvent>()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.farming", 99)
            statMap.setCurrentLevel("stat.farming", 99)
        }
        private val patches = FarmingPatchRegistry()
        private val crops = FarmingCropRegistry(patches)
        private val random = object : GameRandom by DefaultGameRandom(1) {
            override fun of(maxExclusive: Int): Int = maxExclusive - 1
        }
        private val manager = FarmingManager(patches, crops, FarmingGrowth(random, crops),
            FarmingRenderer(crops, FarmingTransmitVars()), random, { emptySet() })
        private val bins = FarmingBinManager(FarmingBinRegistry(), FarmingBinRenderer(FarmingTransmitVars()))
        private val script = FarmingScript(manager, crops, unused<ObjRepository>(), XpModifiers(emptySet()),
            FarmingCompanionProtectionHook(manager, crops), events, bins, unused<CompostBinScript>())
        private val context = unused<ProtectedAccessContextFactory>().also {
            field(it, "eventBus", events)
            field(it, "random", random)
        }
        private val launcher = ProtectedAccessLauncher(context)
        private val queues = PlayerQueueProcessor(events, launcher)
        private val interactions = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val held = HeldUInteractions(events)
        private val patch = checkNotNull(patches.byKey(FarmingPatchKey(patchName)))
        private val type = checkNotNull(ServerCacheManager.getObject(checkNotNull(patch.locId)))
        private val loc = BoundLocInfo(LocInfo(2, CoordGrid(3201, 3200), LocEntity(type.id, 10, 0)), type)
        val state = player.farmingPatchesOrCreate().getOrCreate(patch.key).apply {
            cropId = cropName
            stage = 0
            lastGrowthMinute = FarmingClock.currentMinute()
        }

        init {
            events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun put(slot: Int, name: String) { player.inv[slot] = InvObj(item(name)) }

        fun grow() {
            state.stage = checkNotNull(crops.find(checkNotNull(state.cropId))).grownStage
            state.lives = 1
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.farmguild_hespori_seeds", 1)
        }

        fun harvest() {
            val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
            val event = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            assertTrue(launcher.launch(player) { assertTrue(events.publish(this, event)) })
        }

        fun useOnPatch(slot: Int) {
            assertTrue(launcher.launch(player) {
                val obj = checkNotNull(ServerCacheManager.getItem(player.inv[slot]!!.id))
                val event = with(interactions) { opTrigger(loc, loc, type, obj, slot) }
                assertTrue(events.publish(this, checkNotNull(event)))
            })
        }

        fun useOnSeedling() = useItems(0, 1)

        fun usePacket(first: Int, second: Int) {
            val component = "component.inventory:items".asRSCM()
            player.ui.overlays[Component(component)] = UserInterface(component ushr 16)
            player.ifSetEvents("component.inventory:items", player.inv.indices, IfEvent.TgtCom, IfEvent.Target)
            with(HeldUOpScript(events, held, launcher)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            IfButtonTHandler(events, launcher).handle(player, IfButtonT(
                CombinedId(component), first, player.inv[first]!!.id,
                CombinedId(component), second, player.inv[second]!!.id,
            ))
        }

        fun useItems(first: Int, second: Int) {
            assertTrue(launcher.launch(player) {
                held.interact(this, inv, checkNotNull(ServerCacheManager.getItem(inv[first]!!.id)), first,
                    checkNotNull(ServerCacheManager.getItem(inv[second]!!.id)), second)
            })
        }

        fun assertWaterAnimation() {
            assertEquals(2293, player.pendingSequence.id)
            assertEquals(139, checkNotNull(ServerCacheManager.getAnim(2293)).totalDelay)
            assertEquals(1, client.messages.count { FarmingParams.SYNTH_WATERING.asRSCM().toString() in it.toString() })
        }

        fun processInputQueues() { queues.process(player) }

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
