package org.rsmod.content.skills.agility.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.ObjStackability
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PyramidRewardTest {
    @Test
    fun `full inventory does not consume the optional top claim and can retry after freeing space`() {
        val player = Player()
        val inventory = inventory(InvObj(filler))
        assertFalse(player.claimPyramidTop { insertTop(inventory) })
        assertFalse(AgilityPyramidProgress.isTopClaimed(player))
        assertEquals(InvObj(filler), inventory[0])

        inventory[0] = null
        assertTrue(player.claimPyramidTop { insertTop(inventory) })
        assertTrue(AgilityPyramidProgress.isTopClaimed(player))
        assertEquals(InvObj(top), inventory[0])
    }

    @Test
    fun `repeated summit click cannot insert a second top`() {
        val player = Player()
        val inventory = inventory(null, null)
        assertTrue(player.claimPyramidTop { insertTop(inventory) })
        assertFalse(player.claimPyramidTop { error("Already claimed top must not attempt insertion") })
        assertEquals(InvObj(top), inventory[0])
        assertNull(inventory[1])

        AgilityPyramidProgress.resetTop(player)
        assertTrue(player.claimPyramidTop { insertTop(inventory) })
        assertEquals(InvObj(top), inventory[1])
    }

    @Test
    fun `Simon exchange in a full inventory uses the space released by sold tops`() {
        val inventory = inventory(InvObj(top), InvObj(top), InvObj(filler))
        assertTrue(transactions().transaction(autoCommit = true) {
            exchangePyramidTops(select(inventory), top.id, coins.id, 2)
        }.success)
        assertEquals(InvObj(coins, 20_000), inventory[0])
        assertNull(inventory[1])
        assertEquals(InvObj(filler), inventory[2])
    }

    @Test
    fun `coin overflow rolls back every sold top`() {
        val inventory = inventory(InvObj(coins, Int.MAX_VALUE), InvObj(top), InvObj(top))
        val before = inventory.objs.toList()
        assertFalse(transactions().transaction(autoCommit = true) {
            exchangePyramidTops(select(inventory), top.id, coins.id, 2)
        }.success)
        assertEquals(before, inventory.objs.toList())
    }

    @Test
    fun `missing top cannot produce partial payment or consume remaining top`() {
        val inventory = inventory(InvObj(top), null)
        val before = inventory.objs.toList()
        assertFalse(transactions().transaction(autoCommit = true) {
            exchangePyramidTops(select(inventory), top.id, coins.id, 2)
        }.success)
        assertEquals(before, inventory.objs.toList())
    }

    private fun insertTop(inventory: Inventory): Boolean = transactions().transaction(autoCommit = true) {
        val target = select(inventory)
        insert { into = target; obj = top.id; strictCount = 1 }
    }.success

    private fun inventory(vararg items: InvObj?) = Inventory(InventoryServerType(size = items.size), arrayOf(*items))
    private fun transactions() = InvTransactions(emptyMap(), emptyMap(), emptyMap(), setOf(coins.id), emptySet())

    private companion object {
        @JvmStatic
        @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
        val top = ItemServerType(id = 1)
        val coins = ItemServerType(id = 2, stacks = ObjStackability.Always)
        val filler = ItemServerType(id = 3)
    }
}
