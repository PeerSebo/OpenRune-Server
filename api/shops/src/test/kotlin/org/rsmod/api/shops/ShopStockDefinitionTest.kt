package org.rsmod.api.shops

import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.InvStock
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.game.MapClock

class ShopStockDefinitionTest {
    @Test fun `profiles preserve client capacity but own their stock and pricing`() {
        val template = InventoryServerType(id = 12, size = 40, stock = listOf(InvStock(99, 1, 1)))
        val first = definition("First", listOf(InvStock(1, 5, 10))).createInventory(template)
        val second = definition("Second", listOf(InvStock(1, 8, 20))).createInventory(template)
        assertEquals(template.id, first.type.id)
        assertEquals(40, first.size)
        assertEquals(InvScope.Shared, first.type.scope)
        assertEquals(InvStackType.Always, first.type.stack)
        assertTrue(first.type.restock)
        assertFalse(first.type.protect)
        assertFalse(first.type.allStock)
        assertEquals(1250, first.type.sellMultiplier)
        assertEquals(400, first.type.buyMultiplier)
        assertEquals(10, first.type.delta)
        first[0] = first[0]!!.copy(count = 4)
        assertEquals(8, second[0]?.count)
        assertEquals(99, template.stock.single().obj)
        assertEquals("", template.name)

        val process = ShopRestockProcess(MapClock(10))
        process += first
        process.process()
        assertEquals(5, first[0]?.count)
        assertEquals(8, second[0]?.count)
    }

    @Test fun `general store profile has room to receive nonstandard stock`() {
        val profile = ShopStockDefinition("General", "inv.generalshop1", listOf(InvStock(1, 5, 10)), 1300, 400, 30, allStock = true)
        val inventory = profile.createInventory(InventoryServerType(id = 12, size = 40))
        assertTrue(inventory.type.allStock)
        assertEquals(39, inventory.freeSpace())
    }

    @Test fun `profile snapshots supplied stock and rejects duplicate or invalid rows`() {
        val source = mutableListOf(InvStock(1, 5, 10))
        val profile = definition("Stock", source)
        source.clear()
        assertEquals(1, profile.stock.size)
        assertThrows(IllegalArgumentException::class.java) { definition("", profile.stock) }
        assertThrows(IllegalArgumentException::class.java) { definition("Empty", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { definition("Duplicate", profile.stock + profile.stock) }
        assertThrows(IllegalArgumentException::class.java) { definition("Negative", listOf(InvStock(1, -1, 10))) }
        assertThrows(IllegalArgumentException::class.java) { definition("Timer", listOf(InvStock(1, 1, -1))) }
        assertThrows(IllegalArgumentException::class.java) {
            ShopStockDefinition("Bad price", "inv.generalshop1", profile.stock, -1, 400, 30)
        }
    }

    @Test fun `native layout cannot silently truncate a larger stock profile`() {
        val profile = definition("Too large", listOf(InvStock(1, 1, 10), InvStock(2, 1, 10)))
        assertThrows(IllegalArgumentException::class.java) { profile.createInventory(InventoryServerType(id = 12, size = 1)) }
    }

    private fun definition(name: String, stock: List<InvStock>) =
        ShopStockDefinition(name, "inv.generalshop1", stock, 1250, 400, 10)
}
