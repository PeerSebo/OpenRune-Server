package org.rsmod.content.areas.city.rimmington.npcs

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
class PhialsExchangeTest {
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

    @Test fun `exchange charges only for notes actually converted`() {
        val player = player(10, 17)
        exchange(player, Int.MAX_VALUE)
        assertEquals(2, player.inv.count("obj.coins"))
        assertEquals(7, player.inv.count("obj.cert_iron_ore"))
        assertEquals(3, player.inv.count("obj.iron_ore"))
    }

    @Test fun `whole note and coin stacks free space in a full inventory`() {
        val player = player(2, 10)
        fill(player)
        assertNotNull(exchange(player, 2))
        assertEquals(2, player.inv.count("obj.iron_ore"))
        assertEquals(0, player.inv.count("obj.coins"))
        assertEquals(0, player.inv.count("obj.cert_iron_ore"))
    }

    @Test fun `insufficient space is atomic`() {
        val player = player(10, 100)
        fill(player)
        val before = player.inv.objs.toList()
        assertNull(exchange(player, 5))
        assertEquals(before, player.inv.objs.toList())
    }

    @Test fun `changed selected slot cannot consume unrelated notes or coins`() {
        val player = player(10, 100)
        player.inv[0] = InvObj(item("obj.bones"))
        val before = player.inv.objs.toList()
        assertNull(exchange(player, 5))
        assertEquals(before, player.inv.objs.toList())
    }

    private fun player(notes: Int, coins: Int) = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        inv[0] = InvObj(item("obj.cert_iron_ore"), notes)
        inv[1] = InvObj(item("obj.coins"), coins)
        client = object : Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
    }

    private fun fill(player: Player) {
        for (slot in 2..27) player.inv[slot] = InvObj(item("obj.bones"))
    }

    private fun exchange(player: Player, count: Int): String? = with(PhialsScript()) {
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        access.exchangeNotes(0, item("obj.cert_iron_ore"), count)
    }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
}
