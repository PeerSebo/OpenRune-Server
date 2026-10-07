package org.rsmod.content.areas.city.ardougne.npcs

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
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.other.pets.cats.CatColour
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatExchangeTest {
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

    @Test fun `held adult cat frees its slot for the rune payment`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(item(f.cat.obj))
        for (slot in 1..27) f.player.inv[slot] = InvObj(item("obj.bones"))
        assertTrue(f.exchange.sellHeld(f.player, f.cat))
        assertEquals(100, f.player.inv.count("obj.deathrune"))
        assertEquals(0, f.player.inv.count(f.cat.obj))
    }

    @Test fun `the easy diary doubles the offered rune payment`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(item(f.cat.obj))
        VarPlayerIntMapSetter.set(
            f.player, "varbit.ardougne_diary_easy_complete", 1,
        )
        assertTrue(f.exchange.sellHeld(f.player, f.cat))
        assertEquals(200, f.player.inv.count("obj.deathrune"))
    }

    @Test fun `a kitten and a changed inventory cannot receive payment`() {
        val f = Fixture()
        val kitten = CatPet(
            CatStage.KITTEN,
            CatColour.DEFAULT,
        )
        f.player.inv[0] = InvObj(item(kitten.obj))
        val before = f.player.inv.objs.toList()
        assertFalse(f.exchange.sellHeld(f.player, kitten))
        assertFalse(f.exchange.sellHeld(f.player, f.cat))
        assertEquals(before, f.player.inv.objs.toList())
    }

    @Test fun `a full backpack preserves the following cat and its growth`() {
        val f = Fixture()
        val npc = f.care.spawn(f.player, f.cat)
        for (slot in 0..27) f.player.inv[slot] = InvObj(item("obj.bones"))
        assertFalse(f.exchange.sellFirstAvailable(f.player))
        assertSame(npc, f.care.following(f.player))
        assertEquals(f.cat, f.care.followingPet(f.player))
        f.player.inv[0] = null
        assertTrue(f.exchange.sellFirstAvailable(f.player))
        assertNull(f.care.following(f.player))
        assertNull(f.care.followingPet(f.player))
        assertEquals(100, f.player.inv.count("obj.deathrune"))
        assertFalse(f.exchange.sellFirstAvailable(f.player))
        assertEquals(100, f.player.inv.count("obj.deathrune"))
    }

    private class Fixture {
        val clock = MapClock(100)
        val flags = CollisionFlagMap()
        val npcs = NpcList()
        val repo = NpcRepository(
            clock, NpcRegistry(npcs, flags, EventBus()), npcs,
        )
        val care = CatPetManager(
            repo, clock, DefaultGameRandom(1), flags,
        )
        val exchange = CatExchange(care)
        val cat = CatPet(
            CatStage.CAT,
            CatColour.DEFAULT,
        )
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            slotId = 1
            uuid = 1L
            assignUid()
            coords = CoordGrid(3200, 3200)
            client = object : Client<Any, Any> {
                override fun write(message: Any) {}
                override fun close() {}
                override fun read(player: Player) {}
                override fun flush() {}
                override fun flushHighPriority() {}
                override fun unregister(service: Any, player: Player) {}
            }
        }
        init {
            for (x in 3192..3208 step 8) for (z in 3192..3208 step 8) flags.allocateIfAbsent(x, z, 0)
        }
    }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
}
