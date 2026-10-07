package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("ServerCacheManager")
@ResourceLock("cachedInventoryTransactions")
class VenatorShardTest {
    @Test
    fun `full inventory preserves the shard stack when assembly has no room`() {
        val player = player(6)
        val before = player.inv.objs.toList()
        assertFalse(VenatorShardScript().assembleShards(player))
        assertEquals(before, player.inv.objs.toList())
    }

    @Test
    fun `consuming exactly five shards frees a slot for the bow`() {
        val player = player(5)
        assertTrue(VenatorShardScript().assembleShards(player))
        assertEquals(InvObj("obj.venator_bow_uncharged"), player.inv[0])
        assertEquals(27, player.inv.objs.filterNotNull().count { it.id == InvObj("obj.knife").id })
    }

    @Test
    fun `insufficient shards cannot partially consume ingredients`() {
        val player = player(4)
        val before = player.inv.objs.toList()
        assertFalse(VenatorShardScript().assembleShards(player))
        assertEquals(before, player.inv.objs.toList())
    }

    private fun player(shards: Int): Player = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), Array(28) { InvObj("obj.knife") })
        inv[0] = InvObj("obj.venator_shard", shards)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
                .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
                .set(null, InvTransactions.from())
        }
    }
}
