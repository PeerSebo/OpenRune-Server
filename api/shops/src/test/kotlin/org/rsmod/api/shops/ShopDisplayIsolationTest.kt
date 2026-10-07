package org.rsmod.api.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvStock
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifCloseModal
import org.rsmod.api.script.onIfClose
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShopDisplayIsolationTest {
    @Test fun `switching independent stores with the same layout replaces the old transmission before its close callback`() {
        val events = EventBus()
        var closes = 0
        with(ScriptContext(events, CheatCommandMap(), EngineQueueCache())) {
            onIfClose("interface.shopmain") {
                closes++
                player.openedShop?.let { shop ->
                    player.stopInvTransmit(shop.inv)
                    player.openedShop = null
                }
            }
        }
        val shops = Shops(events)
        val first = stock("First", 5)
        val second = stock("Second", 9)
        val shopper = player()
        first.open(shopper, shops)
        assertSame(first.inventory, shopper.openedShop?.inv)
        second.open(shopper, shops)
        assertEquals(1, closes)
        assertSame(second.inventory, shopper.openedShop?.inv)
        assertSame(second.inventory, shopper.invMap[second.displayTemplate])
        assertTrue(shopper.transmittedInvAddQueue.contains(second.inventory.type.id))
        assertEquals(9, shopper.openedShop?.inv?.get(0)?.count)
        shopper.ifCloseModal("interface.shopmain", events)
        assertEquals(2, closes)
        assertNull(shopper.openedShop)
        assertNull(shopper.invMap[second.displayTemplate])
        assertFalse(shopper.transmittedInvAddQueue.contains(second.inventory.type.id))
    }

    @Test fun `shoppers share a store but separate stores using its layout never share stock`() {
        val shops = Shops(EventBus())
        val first = stock("First", 5)
        val second = stock("Second", 9)
        val customer = player()
        val sameStoreCustomer = player()
        val otherStoreCustomer = player()
        first.open(customer, shops)
        first.open(sameStoreCustomer, shops)
        second.open(otherStoreCustomer, shops)
        first.inventory[0] = first.inventory[0]!!.copy(count = 4)
        assertSame(customer.openedShop?.inv, sameStoreCustomer.openedShop?.inv)
        assertNotSame(customer.openedShop?.inv, otherStoreCustomer.openedShop?.inv)
        assertEquals(4, sameStoreCustomer.invMap[first.displayTemplate]?.get(0)?.count)
        assertEquals(9, otherStoreCustomer.invMap[first.displayTemplate]?.get(0)?.count)
        assertEquals(first.inventory.type.id, second.inventory.type.id)
        for ((observer, quantity) in listOf(sameStoreCustomer to 4, otherStoreCustomer to 9)) {
            UpdateInventory.updateInvFull(observer, checkNotNull(observer.invMap[first.displayTemplate]))
            val packet = (observer.client as RecordingClient).messages.filterIsInstance<UpdateInvFull>().single()
            assertEquals(first.inventory.type.id, packet.inventoryId)
            assertEquals(InventoryObject(0, "obj.bronze_dagger".asRSCM(), quantity), packet.getObject(0))
        }
    }

    private fun stock(name: String, quantity: Int) = ShopStockDefinition(
        name, "inv.generalshop1", listOf(InvStock("obj.bronze_dagger".asRSCM(), quantity, 100)),
        sellMultiplier = 1000, buyMultiplier = 400, delta = 20,
    )

    private fun player() = Player().apply { client = RecordingClient(); inv = Inventory.create("inv.inv") }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
