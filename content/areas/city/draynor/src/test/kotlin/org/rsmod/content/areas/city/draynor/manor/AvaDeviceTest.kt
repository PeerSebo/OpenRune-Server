@file:OptIn(org.rsmod.annotations.InternalApi::class)
package org.rsmod.content.areas.city.draynor.manor

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("QuestRequirements")
class AvaDeviceTest {
    @Test
    fun `accumulator purchase is atomic and publishes success without changing quest progress`() = optional {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.coins", 999)
        f.player.inv[1] = InvObj("obj.steel_arrow", 75)
        assertTrue(f.buy())
        assertEquals(0, f.player.inv.count("obj.coins"))
        assertEquals(0, f.player.inv.count("obj.steel_arrow"))
        assertEquals(1, f.player.inv.count("obj.anma_50_reward"))
        assertEquals("obj.anma_50_reward", f.events.single().target)
        assertEquals(0, f.player.vars["varbit.anma_main"])
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test
    fun `attractor plus steel arrows upgrades without charging coins`() = optional {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.anma_30_reward")
        f.player.inv[1] = InvObj("obj.steel_arrow", 75)
        assertTrue(f.buy())
        assertEquals(0, f.player.inv.count("obj.anma_30_reward"))
        assertEquals(1, f.player.inv.count("obj.anma_50_reward"))
    }

    @Test
    fun `ranged boost cannot bypass base level requirement`() = optional {
        val f = Fixture()
        f.player.statMap.setBaseLevel("stat.ranged", 49)
        f.player.statMap.setCurrentLevel("stat.ranged", 99)
        f.player.inv[0] = InvObj("obj.coins", 999)
        f.player.inv[1] = InvObj("obj.steel_arrow", 75)
        assertFalse(f.buy())
        assertEquals(999, f.player.inv.count("obj.coins"))
        assertEquals(75, f.player.inv.count("obj.steel_arrow"))
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `missing arrows leave coins and attractor untouched`() = optional {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.coins", 999)
        f.player.inv[1] = InvObj("obj.steel_arrow", 74)
        f.player.inv[2] = InvObj("obj.anma_30_reward")
        assertFalse(f.buy())
        assertEquals(999, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.inv.count("obj.anma_30_reward"))
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `full inventory rolls back both payment stacks and publishes no completion`() = optional {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.coins", 2000)
        f.player.inv[1] = InvObj("obj.steel_arrow", 100)
        for (slot in 2..27) f.player.inv[slot] = InvObj("obj.bucket_empty")
        assertFalse(f.buy())
        assertEquals(2000, f.player.inv.count("obj.coins"))
        assertEquals(100, f.player.inv.count("obj.steel_arrow"))
        assertEquals(0, f.player.inv.count("obj.anma_50_reward"))
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `respect progress mode retains Animal Magnetism service requirement`() {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(mode = QuestRequirementMode.RespectProgress))
            val f = Fixture()
            f.player.inv[0] = InvObj("obj.coins", 999)
            f.player.inv[1] = InvObj("obj.steel_arrow", 75)
            assertFalse(f.buy())
            assertEquals(999, f.player.inv.count("obj.coins"))
            assertTrue(f.events.isEmpty())
        } finally { QuestRequirements.install(previous) }
    }

    private fun optional(block: () -> Unit) {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(mode = QuestRequirementMode.Optional))
            block()
        } finally { QuestRequirements.install(previous) }
    }

    private class Fixture {
        val player = Player().apply {
            client = SilentClient()
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            statMap.setBaseLevel("stat.ranged", 50)
        }
        val events = mutableListOf<PlayerActionCompleteEvent>()
        private val bus = EventBus().apply {
            subscribeUnbound(PlayerActionCompleteEvent::class.java) { events += this }
        }
        fun buy(): Boolean {
            val coroutine = GameCoroutine("ava-device-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine,
                ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }))
            return with(Ava()) { access.buy(AvaDevices.all[1]) }
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
