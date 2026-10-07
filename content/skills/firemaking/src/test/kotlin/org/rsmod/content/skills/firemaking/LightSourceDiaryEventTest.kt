@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.firemaking

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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.table.LightSourcesRow
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class LightSourceDiaryEventTest {
    @Test
    fun lightingEmitsOneSuccessFact() {
        val f = Fixture()
        f.light()
        assertEquals(1, f.player.inv.count("obj.cave_goblin_mining_helmet_lit"))
        assertEquals("obj.cave_goblin_mining_helmet_lit", f.completions.single().target)
        assertEquals(CoordGrid(3210, 9620), f.completions.single().coords)
        f.light()
        assertEquals(1, f.completions.size)
    }

    @Test
    fun lowFiremakingDoesNotEmitSuccess() {
        val f = Fixture()
        f.player.statMap.setCurrentLevel("stat.firemaking", 1)
        f.light()
        assertEquals(1, f.player.inv.count("obj.cave_goblin_mining_helmet_unlit"))
        assertTrue(f.completions.isEmpty())
    }

    private class Fixture {
        val source = LightSourcesRow.all().first {
            it.unlit.internalName == "obj.cave_goblin_mining_helmet_unlit"
        }
        val player = Player().apply {
            client = SilentClient()
            coords = CoordGrid(3210, 9620)
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            inv[0] = InvObj("obj.tinderbox")
            inv[1] = InvObj(source.unlit.internalName)
            statMap.setBaseLevel("stat.firemaking", 99)
            statMap.setCurrentLevel("stat.firemaking", 99)
        }
        val completions = mutableListOf<PlayerActionCompleteEvent>()
        private val events = EventBus()
        init {
            events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
            with(LightSourceEvents()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }
        fun light() {
            val coroutine = GameCoroutine("light-source-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine,
                ProtectedAccessContextFactory.empty().copy(getEventBus = { events }))
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = {
                HeldUInteractions(events).interact(access, player.inv,
                    checkNotNull(ServerCacheManager.getItem(player.inv[0]!!.id)), 0, source.unlit, 1)
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
            player.activeCoroutine = null
        }
    }
    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic
        @BeforeAll
        fun setup() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
