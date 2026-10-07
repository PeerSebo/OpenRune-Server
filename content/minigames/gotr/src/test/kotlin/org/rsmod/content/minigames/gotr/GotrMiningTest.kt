package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.skilling.SkillingAwardResult
import org.rsmod.api.player.skilling.awardSkillingProduct
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.content.minigames.gotr.scripts.GotrMiningScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GotrMiningTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `huge remains fill the last available slots rather than rejecting the batch`() {
        for (free in 1..5) {
            val player = player(free)
            val product = prepare(player, "loc.gotr_essence_tier_3", "obj.gotr_guardian_essence")
            assertEquals(free, product.count)
            assertFalse(product.cancelled)
            assertEquals(free, player.vars["varbit.gotr_mining_count"])
        }
    }

    @Test fun `full inventory cannot award phantom mining progress`() {
        val player = player(0)
        val product = prepare(player, "loc.gotr_essence_tier_3", "obj.gotr_guardian_essence")
        assertTrue(product.cancelled)
        assertEquals(0, player.vars["varbit.gotr_mining_count"])
    }

    @Test fun `stackable fragments retain their batch with a full inventory`() {
        val player = player(0)
        player.inv[0] = InvObj("obj.gotr_guardian_fragment", 10)
        val product = prepare(player, "loc.gotr_essence_tier_2", "obj.gotr_guardian_fragment")
        assertEquals(4, product.count)
        assertFalse(product.cancelled)
    }

    @Test fun `finished rounds and nonmembers cannot recreate cleaned materials or earn experience`() {
        for (playing in listOf(false, true)) {
            for ((rock, item) in listOf(
                "loc.gotr_essence_tier_1" to "obj.gotr_guardian_fragment",
                "loc.gotr_essence_tier_3" to "obj.gotr_guardian_essence",
            )) {
                val player = player(28)
                VarPlayerIntMapSetter.set(player, "varbit.gotr_is_playing", if (playing) 1 else 0)
                VarPlayerIntMapSetter.set(player, "varbit.gotr_player_has_finished_game", if (playing) 1 else 0)
                val product = product(player, rock, item).apply { experience = 10.0 }

                assertEquals(SkillingAwardResult.Cancelled, player.awardSkillingProduct(events(), product))

                assertTrue(player.inv.isEmpty())
                assertEquals(0, player.statMap.getFineXP("stat.mining"))
                assertEquals(0, player.vars["varbit.gotr_mining_count"])
                assertFalse(product.depletes)
            }
        }
    }

    private fun player(free: Int) = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        repeat(28 - free) { inv[it] = InvObj("obj.bronze_pickaxe") }
        VarPlayerIntMapSetter.set(this, "varbit.gotr_is_playing", 1)
    }

    private fun events(): EventBus {
        val events = EventBus()
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
            override fun randomDouble() = 0.99
        }
        with(GotrMiningScript(random)) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        return events
    }

    private fun prepare(player: Player, rock: String, item: String): SkillingProduct =
        product(player, rock, item).also { events().publish(SkillingProductPrepareEvent(it)) }

    private fun product(player: Player, rock: String, item: String): SkillingProduct {
        val type = checkNotNull(ServerCacheManager.getObject(rock.asRSCM()))
        val loc = BoundLocInfo(LocInfo(2, CoordGrid(3592, 9503), LocEntity(type.id, 10, 0)), type)
        val row = MiningRocksRow.all().single { row -> row.rockObject.any { it.id == type.id } }
        return SkillingProduct(player, "stat.mining", item, source = SkillingProductSource.Mining(loc, row))
    }
}
