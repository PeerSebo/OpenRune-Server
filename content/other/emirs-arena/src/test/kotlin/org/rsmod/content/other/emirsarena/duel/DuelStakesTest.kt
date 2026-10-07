package org.rsmod.content.other.emirsarena.duel

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
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
class DuelStakesTest {
    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test fun `winner receives both native stake inventories once`() {
        val f = Fixture()
        val winner = player(1)
        val loser = player(2)
        f.stakes.stakeInv(winner)[0] = InvObj("obj.coins", 400)
        f.stakes.stakeInv(loser)[0] = InvObj("obj.coins", 600)
        f.stakes.award(winner, loser)
        assertEquals(1000, winner.inv.physicalCount("obj.coins"))
        assertFalse(f.stakes.hasStake(winner))
        assertFalse(f.stakes.hasStake(loser))
        f.stakes.award(winner, loser)
        assertEquals(1000, winner.inv.physicalCount("obj.coins"))
    }

    @Test fun `awarding an item preserves its per-item state`() {
        val f = Fixture()
        val winner = player(1)
        val loser = player(2)
        val type = ServerCacheManager.getItemTypes().first { it.name == "Toxic blowpipe" && !it.canCert }
        val item = InvObj(type, vars = 123)
        f.stakes.stakeInv(loser)[0] = item
        f.stakes.award(winner, loser)
        assertEquals(item, winner.inv[0])
    }

    @Test fun `full backpack recovery keeps escrow until space is available`() {
        val f = Fixture()
        val player = player(1)
        for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bronze_sword")
        val stake = f.stakes.stakeInv(player)
        stake[0] = InvObj("obj.coins", 500)
        f.stakes.recover(player)
        assertEquals(500, stake.physicalCount("obj.coins"))
        assertTrue(f.repo.findAll(player.coords).none())
        player.inv[0] = null
        f.stakes.recover(player)
        assertTrue(stake.isEmpty())
        assertEquals(500, player.inv.physicalCount("obj.coins"))
    }

    @Test fun `full backpack reward drops to the winner while clearing escrow`() {
        val f = Fixture()
        val winner = player(1)
        val loser = player(2)
        for (slot in winner.inv.indices) winner.inv[slot] = InvObj("obj.bronze_sword")
        f.stakes.stakeInv(loser)[0] = InvObj("obj.coins", 500)
        f.stakes.award(winner, loser)
        assertTrue(f.stakes.stakeInv(loser).isEmpty())
        assertEquals(500, f.repo.findAll(winner.coords).single().count)
    }

    private fun player(id: Int) = Player().apply {
        slotId = id
        uuid = id.toLong()
        observerUUID = id.toLong()
        accountId = id
        characterId = id
        assignUid()
        coords = CoordGrid(3365, 3275, 0)
        currentMapClock = 100
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
    }

    private class Fixture {
        val repo = ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))
        val stakes = DuelStakes(EventBus(), repo)
    }
}
