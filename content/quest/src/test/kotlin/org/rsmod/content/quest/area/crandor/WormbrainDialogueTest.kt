package org.rsmod.content.quest.area.crandor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.rewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WormbrainDialogueTest {
    private val restored = mutableListOf<() -> Unit>()

    @BeforeAll
    fun setup() {
        ServerCacheManager.init(241).close()
        for ((owner, name) in listOf(
            "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
            "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
        )) {
            val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
            val old = field.get(null)
            restored += { field.set(null, old) }
        }
        val oldStorage = InvVirtualStorageHolder.instance
        restored += { InvVirtualStorageHolder.instance = oldStorage }
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll
    fun restore() { restored.asReversed().forEach { it() } }

    @Test
    fun `fresh and early quest players only see ordinary Wormbrain topics on either talk route`() {
        for (approach in listOf(false, true)) {
            for ((stage, briefed, instructions) in listOf(
                Triple(DragonSlayerStage.NotStarted, 0, 0),
                Triple(DragonSlayerStage.NotStarted, 1, 0),
                Triple(DragonSlayerStage.Started, 0, 0),
                Triple(DragonSlayerStage.OziachSpoken, 0, 0),
                Triple(DragonSlayerStage.OziachSpoken, 1, 1),
            )) {
                val f = Fixture()
                f.state(stage, briefed, instructions)
                f.start(approach)
                f.advance()
                assertFalse(f.output().contains(MAP_OPTION), "Premature map topic at $stage/$briefed/$instructions")
                assertTrue(f.output().contains("What are you in for?"))
                assertTrue(f.output().contains("Sorry, thought this was a zoo."))
                f.advance(choice = 1)
                f.finish()
                assertFalse(f.output().contains("10,000"))
                assertEquals(0, f.player.inv.count(DragonSlayerRefs.MapPartLozar))
            }
        }
    }

    @Test
    fun `Lozar clue enables the map topic and a normal purchase on either talk route`() {
        for (approach in listOf(false, true)) {
            val f = Fixture()
            f.state(DragonSlayerStage.OziachSpoken, 1, 0)
            f.player.inv[0] = item(DragonSlayerRefs.Coins, 10_000)
            f.openPurchase(approach)
            f.finish()
            assertEquals(1, f.player.inv.count(DragonSlayerRefs.MapPartLozar))
            assertEquals(0, f.player.inv.count(DragonSlayerRefs.Coins))
            assertEquals(DragonSlayerStage.OziachSpoken, f.quest.getQuestStage(f.player))
        }
    }

    @Test
    fun `completing Dragon Slayer restores ordinary dialogue even when the clue was learned`() {
        val f = Fixture()
        f.state(DragonSlayerStage.Complete, 1, 0)
        f.start()
        f.advance()
        assertFalse(f.output().contains(MAP_OPTION))
        f.advance(choice = 2)
        f.finish()
        assertEquals(0, f.player.inv.count(DragonSlayerRefs.MapPartLozar))
    }

    @Test
    fun `a stale map menu cannot continue quest dialogue after a reset`() {
        val f = Fixture()
        f.state(DragonSlayerStage.OziachSpoken, 1, 0)
        f.start()
        f.advance()
        assertTrue(f.output().contains(MAP_OPTION))
        f.state(DragonSlayerStage.NotStarted, 0, 0)
        f.advance(choice = 1)
        f.finish()
        assertFalse(f.output().contains("What you do for Wormbrain?"))
    }

    @Test
    fun `purchase rechecks stage and clue before touching coins`() {
        for ((stage, briefed, instructions) in listOf(
            Triple(DragonSlayerStage.NotStarted, 0, 0),
            Triple(DragonSlayerStage.Complete, 1, 0),
            Triple(DragonSlayerStage.OziachSpoken, 1, 1),
        )) {
            val f = Fixture()
            f.state(DragonSlayerStage.OziachSpoken, 1, 0)
            f.player.inv[0] = item(DragonSlayerRefs.Coins, 10_000)
            f.openPurchase()
            f.state(stage, briefed, instructions)
            f.finish()
            assertEquals(10_000, f.player.inv.count(DragonSlayerRefs.Coins))
            assertEquals(0, f.player.inv.count(DragonSlayerRefs.MapPartLozar))
        }
    }

    @Test
    fun `eligible purchase still requires enough coins and refunds an inventory overflow`() {
        val poor = Fixture()
        poor.state(DragonSlayerStage.OziachSpoken, 1, 0)
        poor.player.inv[0] = item(DragonSlayerRefs.Coins, 9_999)
        poor.openPurchase()
        poor.finish()
        assertEquals(9_999, poor.player.inv.count(DragonSlayerRefs.Coins))
        assertEquals(0, poor.player.inv.count(DragonSlayerRefs.MapPartLozar))

        val full = Fixture()
        full.state(DragonSlayerStage.OziachSpoken, 1, 0)
        full.player.inv.objs.fill(item("obj.logs"))
        full.player.inv[0] = item(DragonSlayerRefs.Coins, 10_001)
        val before = full.player.inv.objs.toList()
        full.openPurchase()
        full.finish()
        assertEquals(before, full.player.inv.objs.toList())
        assertEquals(0, full.player.inv.count(DragonSlayerRefs.MapPartLozar))
    }

    private class Fixture {
        val quest = Quest(
            id = 23, key = DragonSlayerRefs.QuestKey, rowID = 31, displayName = "Dragon Slayer I",
            mapElement = null, startCoord = null, maxSteps = DragonSlayerStage.Complete,
            questPoints = 2, questVarp = DragonSlayerRefs.QuestVarp,
            rewards = rewards {}, itemDisplay = null,
        )
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3011, 3192)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val events = EventBus()
        private val random = DefaultGameRandom(1)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getRandom = { random }, getEventBus = { events }, getAlignment = { TextAlignment() },
        )
        private val coroutine = GameCoroutine("wormbrain-dialogue-test")
        private var result: Result<Unit>? = null

        init {
            val clock = MapClock(100)
            val collision = CollisionFlagMap()
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normalLocs = LocRegistryNormal(updates, collision, storage)
            val npcs = NpcList()
            val npcRegistry = NpcRegistry(npcs, collision, events)
            val regions = RegionRegistry(
                RegionListSmall(), RegionListLarge(), normalLocs, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
            )
            val locs = LocRepository(clock, LocRegistry(
                storage, normalLocs, LocRegistryRegion(updates, collision, storage, regions),
            ), regions)
            val music = MusicPlayer::class.java
                .getDeclaredConstructor(GameRandom::class.java, MusicRepository::class.java)
                .apply { isAccessible = true }.newInstance(random, MusicRepository(random))
            val cutscene = DragonSlayerCutscene(
                quest, RegionRepository(regions), NpcRepository(clock, npcRegistry, npcs),
                ObjRepository(clock, ObjRegistry(updates)), locs, WorldRepository(updates), music,
            )
            with(DragonSlayerDialogue(quest, cutscene, Shops(events))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).register()
            }
        }

        fun state(stage: Int, briefed: Int, instructions: Int) {
            quest.jumpToStage(player, stage)
            VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_champion_explained_elvarg", briefed)
            VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_instructions_wormbrain", instructions)
        }

        fun output(): String = client.messages.joinToString("\n")

        fun start(approach: Boolean = false) {
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val npc = Npc(DragonSlayerRefs.Wormbrain, CoordGrid(3013, 3190))
            val block: suspend () -> Unit = {
                val dispatched = if (approach) events.publish(access, NpcEvents.Ap1(npc))
                    else events.publish(access, NpcEvents.Op1(npc))
                assertTrue(dispatched)
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(outcome: Result<Unit>) { result = outcome }
            })
        }

        fun advance(choice: Int = 1) {
            result?.getOrThrow()
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class), "Unexpected dialogue suspension")
            val button = when {
                player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                player.ui.containsModal("interface.objectbox") -> "component.objectbox:universe"
                player.ui.containsModal("interface.messagebox") -> "component.messagebox:continue"
                else -> error("Unexpected dialogue interface")
            }
            coroutine.resumeWith(ResumePauseButtonInput(button, if (button.endsWith(":options")) choice else -1))
            result?.getOrThrow()
        }

        fun openPurchase(approach: Boolean = false) {
            start(approach)
            advance()
            assertTrue(output().contains(MAP_OPTION))
            advance(choice = 1)
            advance()
            advance()
            advance(choice = 3)
            advance()
            advance()
            advance(choice = 2)
        }

        fun finish() {
            var steps = 0
            while (result == null && steps++ < 30) advance()
            checkNotNull(result) { "Conversation did not finish" }.getOrThrow()
            player.activeCoroutine = null
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
        const val MAP_OPTION = "I believe you've got a piece of map that I need."
        fun item(type: String, count: Int = 1) =
            InvObj(checkNotNull(ServerCacheManager.getItem(type.asRSCM())), count)
    }
}
