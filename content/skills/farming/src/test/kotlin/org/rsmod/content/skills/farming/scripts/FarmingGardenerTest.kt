package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingServiceData
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingGardenerTest {
    @Test fun `tree and fruit tree gardeners charge exactly 200 coins and clear only their patch`() {
        for ((patch, crop) in listOf("farming_tree_patch_3" to "magic", "farming_fruit_tree_patch_1" to "apple")) {
            val f = Fixture(patch, crop)
            f.giveCoins(500)
            val unrelated = f.player.farmingPatchesOrCreate().getOrCreate(FarmingPatchKey("farming_tree_patch_2"))
            unrelated.cropId = "oak"
            unrelated.stage = 1
            f.pay()
            f.accept()
            assertEquals(300, f.player.inv.count("obj.coins"))
            assertNull(f.state.cropId)
            assertFalse(f.state.stump)
            assertFalse(f.state.paidProtected)
            assertEquals(FarmingConstants.RAKE_STAGES, f.state.rakeStage)
            assertEquals("oak", unrelated.cropId)
            assertTrue(f.client.messages.any { FarmingMessages.GARDENER_REMOVED_TREE in it.toString() })
        }
    }

    @Test fun `a checked tree stump can also be removed for 200 coins`() {
        val f = Fixture()
        f.state.stump = true
        f.giveCoins(200)
        f.pay()
        f.accept()
        assertNull(f.state.cropId)
        assertFalse(f.state.stump)
        assertEquals(0, f.player.inv.count("obj.coins"))
    }

    @Test fun `insufficient coins and declining never remove the tree or charge a partial fee`() {
        for (accept in listOf(false, true)) {
            val f = Fixture()
            f.giveCoins(if (accept) 199 else 500)
            f.pay()
            f.answer(if (accept) 1 else 2)
            assertEquals("magic", f.state.cropId)
            assertEquals(if (accept) 199 else 500, f.player.inv.count("obj.coins"))
            if (accept) assertTrue(f.client.messages.any { "I need 200 coins" in it.toString() })
        }
    }

    @Test fun `unchecked trees keep their health reward and cannot be removed for a fee`() {
        val f = Fixture()
        f.state.checkHealthClaimed = false
        f.giveCoins(200)
        f.pay()
        assertTrue(f.client.messages.any { FarmingMessages.GARDENER_CHECK_HEALTH in it.toString() })
        assertEquals("magic", f.state.cropId)
        assertEquals(200, f.player.inv.count("obj.coins"))
        assertFalse(f.state.checkHealthClaimed)
    }

    @Test fun `the tree and coins are rechecked after the confirmation dialogue`() {
        for (change in listOf("crop", "dead", "stage", "health", "coins", "empty")) {
            val f = Fixture()
            f.giveCoins(200)
            f.pay()
            f.continueOffer()
            when (change) {
                "crop" -> f.state.cropId = "oak"
                "dead" -> f.state.dead = true
                "stage" -> f.state.stage = 0
                "health" -> f.state.checkHealthClaimed = false
                "coins" -> f.player.inv[0] = null
                "empty" -> f.state.clearToEmpty()
            }
            val before = f.state.cropId
            f.choose(1)
            assertEquals(before, f.state.cropId, change)
            assertEquals(if (change == "coins") 0 else 200, f.player.inv.count("obj.coins"), change)
        }
    }

    @Test fun `a growing tree still offers its original crop protection payment`() {
        val f = Fixture()
        f.state.stage = 0
        f.state.checkHealthClaimed = false
        f.state.paidProtected = false
        f.pay()
        assertFalse(f.client.messages.any { FarmingMessages.GARDENER_REMOVE_OFFER in it.toString() })
        f.answer(2)
        assertEquals("magic", f.state.cropId)
        assertFalse(f.state.paidProtected)
    }

    private class Fixture(patchName: String = "farming_tree_patch_3", cropName: String = "magic") {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
        }
        private val events = EventBus()
        private val patches = FarmingPatchRegistry()
        private val crops = FarmingCropRegistry(patches).apply {
            registerAll(FarmingCrops.ALL.map { it.toApiCrop() })
        }
        private val random = DefaultGameRandom(1)
        private val manager = FarmingManager(patches, crops, FarmingGrowth(random, crops),
            FarmingRenderer(crops, FarmingTransmitVars()), random, { emptySet() })
        private val patch = checkNotNull(manager.patchByKey(FarmingPatchKey(patchName)))
        val state = player.farmingPatchesOrCreate().getOrCreate(patch.key).apply {
            cropId = cropName
            stage = checkNotNull(crops.find(cropName)).grownStage
            checkHealthClaimed = true
            plantXpPaid = true
            paidProtected = true
            lastGrowthMinute = FarmingClock.currentMinute()
        }
        private val gardener = FarmingServiceData.gardeners.single { patchName in it.patches }
        private val npc = Npc(gardener.npc, player.coords.translate(0, 1))
        private val coroutine = GameCoroutine("gardener-test")
        private var completion: Result<Unit>? = null

        init {
            with(FarmingGardenerScript(manager)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun giveCoins(count: Int) {
            player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())), count)
        }

        fun pay() {
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getAlignment = { TextAlignment() },
            )
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val event = checkNotNull(NpcInteractions(events).opTrigger(player, npc, InteractionOp.Op3))
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }

        fun continueOffer() {
            coroutine.resumeWith(ResumePauseButtonInput("component.chat_left:continue", -1))
            completion?.getOrThrow()
        }

        fun choose(option: Int) {
            coroutine.resumeWith(ResumePauseButtonInput("component.chatmenu:options", option))
            completion?.getOrThrow()
        }

        fun answer(option: Int) { continueOffer(); choose(option) }
        fun accept() = answer(1)
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
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun setup() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
