package org.rsmod.content.other.pouches

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.other.pouches.lootingbag.*
import org.rsmod.content.other.pouches.runepouch.*
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(org.rsmod.annotations.InternalApi::class)
class PouchDeathHookTest {
    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test fun `safe duel death preserves both containers and all contents`() {
        val f = Fixture()
        f.hook.beforeDrops(f.context(recentPvpDamage = true), f.handling(Int.MAX_VALUE))
        assertEquals(10, f.player.lootingBag.physicalCount("obj.bronze_arrow"))
        assertEquals(100, f.player.readRunePouch(3).counts[0])
        assertTrue(f.repo.findAll(f.player.coords).none())
        assertTrue(f.player.hasLootingBag())
    }

    @Test fun `ordinary death releases bag at effective recovery coordinate once`() {
        val f = Fixture()
        val recovery = CoordGrid(3200, 3200, 0)
        val context = f.context().copy(coords = recovery)
        f.hook.beforeDrops(context, f.handling(3))
        assertTrue(f.player.lootingBag.isEmpty())
        assertFalse(f.player.attr.has(LOOTING_BAG_ATTR))
        assertEquals(10, f.repo.findAll(recovery).single().count)
        assertEquals(100, f.player.readRunePouch(3).counts[0])
        f.hook.beforeDrops(context, f.handling(3))
        assertEquals(10, f.repo.findAll(recovery).single().count)
    }

    private class Fixture {
        val player = Player().apply {
            slotId = 1
            uuid = 1L
            observerUUID = 1L
            assignUid()
            coords = CoordGrid(3365, 3275, 0)
            currentMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            inv[0] = InvObj(LootingBags.CLOSED)
            inv[1] = InvObj(RunePouches.REGULAR)
            lootingBag[0] = InvObj("obj.bronze_arrow", 10)
            saveLootingBag()
            val contents = RunePouchContents(IntArray(3), IntArray(3))
            contents.add(checkNotNull(RunePouchRunes.byObj(InvObj("obj.airrune").id)).compactId, 100)
            writeRunePouch(contents)
        }
        val repo = ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))
        val hook = PouchDeathHook(repo, MapClock(100), GroundItemDropResolver(emptySet()))
        fun context(recentPvpDamage: Boolean = false) = PlayerDeathContext(player, player.coords,
            false, 0, false, false, false, false, recentPvpDamage, 0, null)
        fun handling(keep: Int) = PlayerDeathHandling(keep, player, 300, 100, false,
            UntradeableHandling.KEEP, gravestone = false)
    }
}
