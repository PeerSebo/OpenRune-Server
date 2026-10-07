package org.rsmod.api.shops.restock

import dev.openrune.types.InvStock
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.MapClock
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

class ShopRestockProcessTest {
    @Test
    fun `depleted stock returns at its interval and its slot is preserved`() {
        val clock = MapClock(9)
        val process = ShopRestockProcess(clock)
        val shop = inventory(listOf(InvStock(1, 1, 10)), listOf(0))
        process += shop
        process.process()
        assertEquals(0, shop[0]?.count)
        assertTrue(shop in process.modifiedShops)

        clock.cycle = 10
        process.process()
        assertEquals(1, shop[0]?.count)
        assertTrue(shop.modifiedSlots[0])
        assertFalse(shop in process.modifiedShops)
    }

    @Test
    fun `overstock and player sold goods normalize without disturbing permanent slots`() {
        val clock = MapClock(100)
        val process = ShopRestockProcess(clock)
        val shop = inventory(listOf(InvStock(1, 2, 100)), listOf(3, 1))
        process += shop
        process.process()
        assertEquals(2, shop[0]?.count)
        assertNull(shop[1])
        assertTrue(process.modifiedShops.isEmpty())
    }

    @Test
    fun `disabled restocking is never scheduled`() {
        val process = ShopRestockProcess(MapClock(100))
        val shop = inventory(listOf(InvStock(1, 5, 1)), listOf(1), restock = false)
        process += shop
        process.process()
        assertEquals(1, shop[0]?.count)
        assertTrue(process.modifiedShops.isEmpty())
        assertFalse(shop.hasModifiedSlots())
    }

    @Test
    fun `zero interval reward stock is unchanged and removed from the work set`() {
        val process = ShopRestockProcess(MapClock(100))
        val shop = inventory(listOf(InvStock(1, 5, 0)), listOf(1))
        process += shop
        process.process()
        assertEquals(1, shop[0]?.count)
        assertTrue(process.modifiedShops.isEmpty())
        assertFalse(shop.hasModifiedSlots())
    }

    @Test
    fun `unknown intervals use the fallback instead of restocking every tick`() {
        val clock = MapClock(99)
        val process = ShopRestockProcess(clock)
        val shop = inventory(listOf(InvStock(1, 2, -1)), listOf(0))
        process += shop
        process.process()
        assertEquals(0, shop[0]?.count)

        clock.cycle = 100
        process.process()
        assertEquals(1, shop[0]?.count)
        assertTrue(shop in process.modifiedShops)

        clock.cycle = 101
        process.process()
        assertEquals(1, shop[0]?.count)
    }

    @Test
    fun `a zero interval slot does not stop other slots from restocking`() {
        val clock = MapClock(1)
        val process = ShopRestockProcess(clock)
        val shop = inventory(listOf(InvStock(1, 10, 0), InvStock(2, 2, 1)), listOf(0, 0))
        process += shop
        process.process()
        assertEquals(0, shop[0]?.count)
        assertEquals(1, shop[1]?.count)
        assertTrue(shop in process.modifiedShops)

        clock.cycle = 2
        process.process()
        assertEquals(0, shop[0]?.count)
        assertEquals(2, shop[1]?.count)
        assertTrue(process.modifiedShops.isEmpty())
    }

    private fun inventory(stock: List<InvStock>, counts: List<Int>, restock: Boolean = true): Inventory {
        val type =
            InventoryServerType(
                id = 1,
                size = counts.size,
                stock = stock,
                flags = if (restock) InventoryServerType.RESTOCK_FLAG else 0,
            )
        val items = Array<InvObj?>(counts.size) { index ->
            InvObj(ItemServerType(id = index + 1), counts[index])
        }
        return Inventory(type, items)
    }
}
