package org.rsmod.content.skills.herblore

import dev.openrune.ServerCacheManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
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
class CleaningHerbsEventsTest {
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

    @Test fun `every herb cleans in the clicked slot with both full and partial inventories`() = runBlocking {
        for (row in HerbloreDefinitions.cleaningHerbs) {
            for (full in listOf(false, true)) {
                val f = Fixture()
                if (full) for (slot in 0 until 28) f.player.inv[slot] = InvObj("obj.bones")
                f.player.inv[2] = InvObj(row.input)
                f.player.inv[20] = InvObj(row.input)
                f.click(20)
                f.clean()
                assertEquals(row.input.id, f.player.inv[2]?.id, row.input.internalName)
                assertEquals(row.output.id, f.player.inv[20]?.id, row.input.internalName)
                assertEquals(if (full) 26 else 0, f.player.inv.count("obj.bones"))
                if (!full) assertEquals(null, f.player.inv[0])
                assertTrue(f.player.statMap.getFineXP("stat.herblore") > 0)
            }
        }
    }

    @Test fun `moving or replacing the clicked herb before cleaning leaves other herbs untouched`() = runBlocking {
        val row = HerbloreDefinitions.cleaningHerbs.first()
        for (replacement in listOf(null, InvObj("obj.bones"))) {
            val f = Fixture()
            f.player.inv[2] = InvObj(row.input)
            f.player.inv[20] = InvObj(row.input)
            f.click(20)
            f.player.inv[21] = f.player.inv[20]
            f.player.inv[20] = replacement
            f.clean()
            assertEquals(row.input.id, f.player.inv[2]?.id)
            assertEquals(row.input.id, f.player.inv[21]?.id)
            assertEquals(replacement, f.player.inv[20])
            assertEquals(0, f.player.inv.count(row.output.internalName))
            assertEquals(0, f.player.statMap.getFineXP("stat.herblore"))
        }
    }

    @Test fun `a repeated click on the same herb cannot clean another herb or award extra xp`() = runBlocking {
        val row = HerbloreDefinitions.cleaningHerbs.first()
        val f = Fixture()
        f.player.inv[2] = InvObj(row.input)
        f.player.inv[20] = InvObj(row.input)
        f.click(20)
        f.click(20)
        f.clean()
        val xp = f.player.statMap.getFineXP("stat.herblore")
        f.clean()
        assertEquals(row.input.id, f.player.inv[2]?.id)
        assertEquals(row.output.id, f.player.inv[20]?.id)
        assertEquals(1, f.player.inv.count(row.output.internalName))
        assertEquals(xp, f.player.statMap.getFineXP("stat.herblore"))
    }

    private class Fixture {
        val events = EventBus()
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            statMap.setBaseLevel("stat.herblore", 99)
            statMap.setCurrentLevel("stat.herblore", 99)
        }
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getRandom = { DefaultGameRandom(1) },
        ))

        init {
            with(CleaningHerbsEvents()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        suspend fun click(slot: Int) {
            val obj = checkNotNull(player.inv[slot])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            assertTrue(events.publish(access, HeldObjEvents.Op1(slot, obj, type, player.inv)))
        }

        suspend fun clean() {
            val iterator = checkNotNull(player.weakQueueList.iterator())
            val queue = iterator.next()
            iterator.remove()
            iterator.cleanUp()
            player.currentMapClock += queue.remainingCycles
            player.processedMapClock = player.currentMapClock
            assertTrue(events.publish(access, PlayerQueueEvents.Protected(queue.args, queue.id)))
        }
    }
}
