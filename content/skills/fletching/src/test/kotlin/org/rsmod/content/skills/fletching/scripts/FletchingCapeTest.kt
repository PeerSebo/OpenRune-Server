package org.rsmod.content.skills.fletching.scripts

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
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FletchingCapeTest {
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

    @Test fun `cape grants only three daily rewards`() {
        val player = player()
        repeat(4) { search(player) }
        assertEquals(3, player.inv.count("obj.xbows_grapple_tip_bolt_mithril_rope"))
        assertEquals(3, player.inv.count("obj.xbows_crossbow_bronze"))
    }

    @Test fun `failed reward preserves the daily allowance and inventory`() {
        val player = player()
        for (slot in 0..26) player.inv[slot] = InvObj(item("obj.bones"))
        val before = player.inv.objs.toList()
        search(player)
        assertEquals(before, player.inv.objs.toList())
        for (slot in 0..26) player.inv[slot] = null
        repeat(3) { search(player) }
        assertEquals(3, player.inv.count("obj.xbows_crossbow_bronze"))
    }

    @Test fun `daily allowance remains tied to saved player variables`() {
        val player = player()
        repeat(3) { search(player) }
        for (slot in 0..27) player.inv[slot] = null
        val reloaded = player()
        for ((id, value) in player.vars.backing) reloaded.vars.backing.put(id, value)
        search(reloaded)
        assertEquals(0, reloaded.inv.count("obj.xbows_crossbow_bronze"))
    }

    private fun player() = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        client = object : Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
    }

    private fun search(player: Player) = with(FletchingCapeScript()) {
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        access.search()
    }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
}
