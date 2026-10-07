package org.rsmod.content.skills.thieving.scripts

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PickpocketTimingTest {
    @Test
    fun `spam clicking after idle pays on distinct ticks without losing the buffered attempt`() {
        val f = Fixture()
        f.attempt()
        repeat(20) { f.attempt() }
        assertEquals(0, f.xp())
        f.tick()
        assertEquals(8, f.xp())
        assertTrue(f.player.isAccessProtected)
        f.tick()
        assertEquals(8, f.xp())
        f.tick()
        assertEquals(16, f.xp())
        assertEquals(2, f.player.inv.count(checkNotNull(ThievingNpcs.manWoman.pouch)))
    }

    @Test
    fun `an attempt resumed before queue processing still waits a tick for its reward`() {
        val f = Fixture()
        f.player.actionDelay = 102
        f.attempt()
        f.tick()
        assertEquals(0, f.xp())
        f.tick()
        assertEquals(0, f.xp())
        assertEquals(104, f.player.actionDelay)
        f.tick()
        assertEquals(8, f.xp())
    }

    @Test
    fun `reward settlement does not cancel a different suspended action`() {
        val f = Fixture()
        f.attempt()
        val action = GameCoroutine("other-action")
        f.player.activeCoroutine = action
        f.player.delay(5)
        var finished = false
        val block: suspend () -> Unit = {
            ProtectedAccess(f.player, action, f.context).delay(5)
            finished = true
        }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        })
        f.tick()
        assertEquals(8, f.xp())
        assertSame(action, f.player.activeCoroutine)
        assertFalse(finished)
        repeat(4) { f.tick() }
        assertTrue(finished)
        assertEquals(8, f.xp())
    }

    @Test
    fun `a pending reward does not close or cancel a newly opened dialogue`() {
        val f = Fixture()
        f.attempt()
        val dialogue = GameCoroutine("dialogue")
        f.player.activeCoroutine = dialogue
        val block: suspend () -> Unit = { dialogue.pause(ResumePauseButtonInput::class) }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        })
        f.tick()
        assertEquals(8, f.xp())
        assertSame(dialogue, f.player.activeCoroutine)
        assertTrue(dialogue.isAwaiting(ResumePauseButtonInput::class))
    }

    @Test
    fun `continuously clicking cannot bunch rewards or exceed the two tick attempt cadence`() {
        val f = Fixture()
        f.attempt()
        var previous = 0
        repeat(16) {
            repeat(10) { f.attempt() }
            f.tick()
            val gained = f.xp() - previous
            assertTrue(gained == 0 || gained == 8, "Tick ${f.player.currentMapClock}: $gained XP")
            previous = f.xp()
        }
        assertEquals(64, f.xp())
    }

    private class Fixture {
        val events = EventBus()
        val player = Player().apply {
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            statMap.setCurrentLevel("stat.thieving", 99)
            statMap.setBaseLevel("stat.thieving", 99)
        }
        private val collision = CollisionFlagMap().apply { allocateIfAbsent(3200, 3200, 0) }
        private val clock = MapClock(100)
        private val list = NpcList()
        private val registry = NpcRegistry(list, collision, events)
        private val repository = NpcRepository(clock, registry, list)
        private val npc = Npc("npc.man", CoordGrid(3200, 3201)).apply {
            currentMapClock = 100
            processedMapClock = 100
        }
        private val random = object : GameRandom by DefaultGameRandom(1) {
            override fun randomDouble() = 0.0
            override fun of(maxExclusive: Int) = maxExclusive - 1
        }
        private val interactions = NpcInteractions(events)
        private val factory = unused<ProtectedAccessContextFactory>().also {
            field(it, "random", random)
            field(it, "eventBus", events)
            field(it, "npcList", list)
        }
        private val access = ProtectedAccessLauncher(factory)
        val context = factory.create()
        private val queues = PlayerQueueProcessor(events, access)

        init {
            repository.add(npc, Int.MAX_VALUE)
            val script = PickpocketScript(unused<ObjRepository>(), XpModifiers(emptySet()),
                InvisibleLevels(emptySet()), RockyPetManager(repository, clock, collision), access)
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun attempt() {
            if (player.isAccessProtected) return
            assertTrue(access.launch(player) {
                val op = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op3))
                assertTrue(events.publish(this, op))
            })
        }

        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
            if (player.isNotDelayed) player.advanceActiveCoroutine()
            queues.process(player)
        }

        fun xp() = player.statMap.getXP("stat.thieving")
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun cache() {
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

        @JvmStatic @AfterAll fun restoreGlobals() { restore.asReversed().forEach { it() } }

        private fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }

        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            return unsafe.allocateInstance(T::class.java) as T
        }
    }
}
