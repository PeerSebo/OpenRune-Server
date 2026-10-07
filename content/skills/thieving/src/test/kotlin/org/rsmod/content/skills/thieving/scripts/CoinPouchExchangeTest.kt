package org.rsmod.content.skills.thieving.scripts

import dev.openrune.definition.type.ObjStackability
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.select
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

class CoinPouchExchangeTest {
    @Test
    fun `opening one pouch leaves the rest and pays one value`() {
        val inv = inventory(InvObj(pouch, 28), null)
        assertTrue(exchange(inv, mapOf(pouch.id to 1), 3))
        assertEquals(InvObj(pouch, 27), inv[0])
        assertEquals(InvObj(coins, 3), inv[1])
    }

    @Test
    fun `opening all types frees a slot for coins in a full inventory`() {
        val inv = inventory(InvObj(pouch, 28), InvObj(otherPouch, 2), InvObj(filler))
        assertTrue(exchange(inv, mapOf(pouch.id to 28, otherPouch.id to 2), 184))
        assertEquals(InvObj(coins, 184), inv[0])
        assertNull(inv[1])
        assertEquals(InvObj(filler), inv[2])
    }

    @Test
    fun `opening one in a full inventory rolls back if the remaining pouches occupy the coin slot`() {
        val inv = inventory(InvObj(pouch, 28), InvObj(filler))
        val before = inv.objs.toList()
        assertFalse(exchange(inv, mapOf(pouch.id to 1), 3))
        assertEquals(before, inv.objs.toList())
    }

    @Test
    fun `coin overflow preserves every pouch and the existing coin stack`() {
        val inv = inventory(InvObj(pouch, 28), InvObj(otherPouch, 2), InvObj(coins, Int.MAX_VALUE - 1))
        val before = inv.objs.toList()
        assertFalse(exchange(inv, mapOf(pouch.id to 28, otherPouch.id to 2), 184))
        assertEquals(before, inv.objs.toList())
    }

    @Test
    fun `missing pouch cancels the entire exchange without partial payment`() {
        val inv = inventory(InvObj(pouch, 28), null)
        val before = inv.objs.toList()
        assertFalse(exchange(inv, mapOf(pouch.id to 28, otherPouch.id to 2), 184))
        assertEquals(before, inv.objs.toList())
    }

    private fun exchange(inv: Inventory, pouches: Map<Int, Int>, payout: Int): Boolean =
        InvTransactions(emptyMap(), emptyMap(), emptyMap(), setOf(pouch.id, otherPouch.id, coins.id), emptySet())
            .transaction(autoCommit = true) { exchangeCoinPouches(select(inv), pouches, coins.id, payout) }.success

    private fun inventory(vararg objs: InvObj?) = Inventory(InventoryServerType(size = objs.size), arrayOf(*objs))

    private companion object {
        val pouch = ItemServerType(id = 1, stacks = ObjStackability.Always)
        val otherPouch = ItemServerType(id = 2, stacks = ObjStackability.Always)
        val coins = ItemServerType(id = 3, stacks = ObjStackability.Always)
        val filler = ItemServerType(id = 4)
    }
}
