package org.rsmod.content.skills.herblore

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwampToadScriptTest {
    private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    private val storage = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private var oldVirtual: InvVirtualStorage? = null

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
        oldTransactions = transactions.get(null)
        oldStorage = storage.get(null)
        oldVirtual = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll fun restore() {
        transactions.set(null, oldTransactions)
        storage.set(null, oldStorage)
        InvVirtualStorageHolder.instance = oldVirtual
    }

    @Test fun `Remove-legs transforms only the selected toad with a full backpack`() = runBlocking {
        val f = Fixture()
        for (slot in 0 until 28) f.player.inv[slot] = InvObj("obj.bones")
        f.player.inv[0] = InvObj("obj.swamp_toad")
        f.player.inv[13] = InvObj("obj.swamp_toad")
        assertTrue(f.events.publish(f.access, f.event(13)))
        assertEquals(2150, f.player.inv[0]?.id)
        assertEquals(2152, f.player.inv[13]?.id)
        assertEquals(26, f.player.inv.count("obj.bones"))
        assertEquals(0, f.player.statMap.getFineXP("stat.herblore"))
    }

    @Test fun `a stale removal event cannot replace another item or duplicate the legs`() = runBlocking {
        val f = Fixture()
        f.player.inv[4] = InvObj("obj.swamp_toad")
        val old = f.event(4)
        assertTrue(f.events.publish(f.access, old))
        assertTrue(f.events.publish(f.access, old))
        assertEquals(1, f.player.inv.count("obj.toads_legs"))
        f.player.inv[4] = InvObj("obj.bones")
        assertTrue(f.events.publish(f.access, old))
        assertEquals(1, f.player.inv.count("obj.bones"))
        assertEquals(0, f.player.inv.count("obj.toads_legs"))
    }

    @Test fun `noted swamp toads have no removal registration`() = runBlocking {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.cert_swamp_toad", 5)
        assertFalse(f.events.publish(f.access, f.event(0)))
        assertEquals(5, f.player.inv.count("obj.cert_swamp_toad"))
    }

    private class Fixture {
        val events = EventBus()
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { error("unused") }, getEventBus = { events },
            getNpcList = { error("unused") }, getPlayerList = { error("unused") },
            getCollision = { error("unused") }, getAreaChecker = { error("unused") },
            getAlignment = { error("unused") }, getLocInteractions = { error("unused") },
            getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        ))

        init {
            with(SwampToadScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun event(slot: Int): HeldObjEvents.Op1 {
            val obj = checkNotNull(player.inv[slot])
            return HeldObjEvents.Op1(slot, obj, checkNotNull(ServerCacheManager.getItem(obj.id)), player.inv)
        }
    }
}
