@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.mining

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.content.skills.mining.scripts.MiningEnhancerScript
import org.rsmod.content.skills.mining.scripts.MiningProductPrepareScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class MiningVarsTest {
    @Test
    fun `mining item identities round trip and null never aliases item zero`() {
        val player = Player()
        assertNull(player.lastMiningPickaxe)
        assertNull(player.preSmeltOre)
        player.lastMiningPickaxe = "obj.infernal_pickaxe"
        player.preSmeltOre = "obj.iron_ore"
        assertEquals("obj.infernal_pickaxe", player.lastMiningPickaxe)
        assertEquals("obj.iron_ore", player.preSmeltOre)
        player.preSmeltOre = null
        assertNull(player.preSmeltOre)
        assertEquals("obj.infernal_pickaxe", player.lastMiningPickaxe)
    }

    @Test
    fun `infernal preparation retains original ore until completion and next ordinary ore stays unchanged`() {
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.smithing", 99)
        }
        val events = EventBus()
        with(MiningProductPrepareScript(AlwaysZero)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        with(MiningEnhancerScript(AlwaysZero, events)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val row = MiningRocksRow(DbHelper.row("dbrow.mining_ironrock"))
        val source = SkillingProductSource.Mining(
            BoundLocInfo(CoordGrid(3200, 3200), LocEntity(row.rockObject.first().id, 10, 0), 2, 1, 1, 0),
            row,
        )
        val product = SkillingProduct(player, "stat.mining", "obj.iron_ore", source = source)
        player.lastMiningPickaxe = "obj.infernal_pickaxe"
        events.publish(SkillingProductPrepareEvent(product))
        assertEquals("obj.iron_bar", product.item)
        assertEquals("obj.iron_ore", player.preSmeltOre)
        events.publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product(
            "stat.mining", product.item, 1, 35.0, source,
        )))
        assertNull(player.preSmeltOre)
        player.lastMiningPickaxe = "obj.dragon_pickaxe"
        val next = SkillingProduct(player, "stat.mining", "obj.iron_ore", source = source)
        events.publish(SkillingProductPrepareEvent(next))
        assertEquals("obj.iron_ore", next.item)
        assertNull(player.preSmeltOre)
    }

    @Test
    fun `packed mining identities remain server only and are excluded from saved sessions`() {
        val player = Player()
        player.lastMiningPickaxe = "obj.infernal_pickaxe"
        player.preSmeltOre = "obj.iron_ore"
        for (symbol in listOf("mining_last_pickaxe", "mining_pre_smelt_ore")) {
            val type = checkNotNull(ServerCacheManager.getVarp("varp.$symbol".asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
        val reconnected = Player()
        reconnected.vars.backing.putAll(player.vars.backing.filter { (id, _) ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        assertNull(reconnected.lastMiningPickaxe)
        assertNull(reconnected.preSmeltOre)
    }

    private object AlwaysZero : GameRandom {
        override fun of(maxExclusive: Int) = 0
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
        override fun randomDouble() = 0.0
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
