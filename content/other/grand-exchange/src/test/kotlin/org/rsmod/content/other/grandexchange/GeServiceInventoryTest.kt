package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeServiceInventoryTest {
    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test fun `native offer status buttons receive click masks on their dynamic parent`() {
        val packets = mutableListOf<Any>()
        val player = player(1).apply {
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { packets += message }
            }
        }
        Fixture().service.onOffersOpened(player)
        val nativeStatus = "component.ge_offers:details_status".asRSCM()
        val mask = packets.filterIsInstance<IfSetEventsV2>().single {
            (it.interfaceId shl 16 or it.componentId) == nativeStatus
        }
        assertEquals(0, mask.start)
        assertEquals(1, mask.end)
        assertEquals(IfEvent.Op1.bitmask, (mask.events2.toLong() shl 32) or (mask.events1.toLong() and 0xffffffffL))
        assertEquals(nativeStatus, GeConfig.COMP_DETAILS_STATUS.asRSCM())
    }

    @Test fun `real offer and collection flow conserves items and charges only the seller tax`() {
        val f = Fixture()
        val seller = player(1)
        val buyer = player(2)
        seller.inv[0] = InvObj("obj.coal", 1)
        buyer.inv[0] = InvObj("obj.coins", 500)
        f.confirm(seller, GeOfferType.Sell, price = 100)
        f.confirm(buyer, GeOfferType.Buy, price = 120)
        assertEquals(0, seller.inv.physicalCount("obj.coal"))
        assertEquals(380, buyer.inv.physicalCount("obj.coins"))
        f.service.collectAll(buyer, toBank = false)
        f.service.collectAll(seller, toBank = false)
        assertEquals(1, buyer.inv.physicalCount("obj.coal"))
        assertEquals(400, buyer.inv.physicalCount("obj.coins"))
        assertEquals(98, seller.inv.physicalCount("obj.coins"))
        assertEquals(0, f.exchange.offerCount)
        f.service.collectAll(seller, toBank = false)
        assertEquals(98, seller.inv.physicalCount("obj.coins"))
    }

    @Test fun `insufficient funds never creates an offer or removes partial coins`() {
        val f = Fixture()
        val buyer = player(1)
        buyer.inv[0] = InvObj("obj.coins", 50)
        f.confirm(buyer, GeOfferType.Buy, price = 100)
        assertEquals(50, buyer.inv.physicalCount("obj.coins"))
        assertEquals(0, f.exchange.offerCount)
    }

    @Test fun `full backpack leaves collection escrow intact until an item fits`() {
        val f = Fixture()
        val buyer = player(1)
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 1)
        val offer = f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 100, 1)
        for (slot in buyer.inv.indices) buyer.inv[slot] = InvObj("obj.bronze_sword")
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, GeCollectionMode.Item)
        assertEquals(1, offer.collectItems)
        buyer.inv[0] = null
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, GeCollectionMode.Item)
        assertEquals(1, buyer.inv.physicalCount("obj.coal"))
        assertNull(f.exchange.offer(1, 0))
    }

    @Test fun `default collection notes multiple items and cannot collect them twice`() {
        val f = Fixture()
        val buyer = player(1)
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 5)
        f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 100, 5)
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, GeCollectionMode.Default)
        val collected = buyer.inv.filterNotNull().single()
        assertTrue(ServerCacheManager.getItemOrDefault(collected.id).isCert)
        assertEquals(5, collected.count)
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, GeCollectionMode.Default)
        assertEquals(5, buyer.inv.filterNotNull().single().count)
        assertNull(f.exchange.offer(1, 0))
    }

    @Test fun `unnoted collection leaves the remainder in escrow when only two items fit`() {
        val f = Fixture()
        val buyer = player(1)
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 5)
        val offer = f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 100, 5)
        for (slot in 2 until buyer.inv.size) buyer.inv[slot] = InvObj("obj.bronze_sword")
        val mode = GrandExchangeScript(f.service).collectMode(IfButtonOp.Op2)
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, mode)
        assertEquals(2, buyer.inv.physicalCount("obj.coal"))
        assertEquals(3, offer.collectItems)
        buyer.inv[2] = null
        f.service.collectSlot(buyer, 0, GeConfig.COLLECT_BOX_ITEMS, GeCollectionMode.Note)
        assertEquals(3, buyer.inv[2]?.count)
        assertTrue(ServerCacheManager.getItemOrDefault(buyer.inv[2]!!.id).isCert)
        assertNull(f.exchange.offer(1, 0))
    }

    @Test fun `coin stack overflow leaves escrow untouched`() {
        val f = Fixture()
        val seller = player(1)
        f.exchange.place(2, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 100, 1)
        val offer = f.exchange.place(1, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 1)
        seller.inv[0] = InvObj("obj.coins", Int.MAX_VALUE)
        f.service.collectSlot(seller, 0, GeConfig.COLLECT_BOX_COINS, GeCollectionMode.Default)
        assertEquals(Int.MAX_VALUE, seller.inv.physicalCount("obj.coins"))
        assertEquals(98, offer.collectCoins)
    }

    @Test fun `bank collection bypasses a full backpack and keeps items unnoted`() {
        val f = Fixture()
        val buyer = player(1)
        VarPlayerIntMapSetter.set(buyer, "varbit.bank_capacity", 100)
        for (slot in buyer.inv.indices) buyer.inv[slot] = InvObj("obj.bronze_sword")
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 5)
        f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 120, 5)
        f.service.collectAll(buyer, toBank = true)
        val bank = buyer.invMap.getOrPut("inv.bank")
        assertEquals(5, bank.physicalCount("obj.coal"))
        assertEquals(100, bank.physicalCount("obj.coins"))
        assertEquals(28, buyer.inv.physicalCount("obj.bronze_sword"))
        assertNull(f.exchange.offer(1, 0))
        f.service.collectAll(buyer, toBank = true)
        assertEquals(5, bank.physicalCount("obj.coal"))
        assertEquals(100, bank.physicalCount("obj.coins"))
    }

    @Test fun `full bank leaves items and price refund in escrow`() {
        val f = Fixture()
        val buyer = player(1)
        VarPlayerIntMapSetter.set(buyer, "varbit.bank_capacity", 1)
        buyer.invMap.getOrPut("inv.bank")[0] = InvObj("obj.bronze_sword")
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 5)
        val offer = f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 120, 5)
        f.service.collectAll(buyer, toBank = true)
        assertEquals(5, offer.collectItems)
        assertEquals(100, offer.collectCoins)
        assertEquals(0, buyer.invMap.getOrPut("inv.bank").physicalCount("obj.coal"))
        assertEquals(0, buyer.invMap.getOrPut("inv.bank").physicalCount("obj.coins"))
    }

    @Test fun `full bank accepts an existing stack but leaves a refund needing a new slot`() {
        val f = Fixture()
        val buyer = player(1)
        VarPlayerIntMapSetter.set(buyer, "varbit.bank_capacity", 1)
        val bank = buyer.invMap.getOrPut("inv.bank")
        bank[0] = InvObj("obj.coal", 2)
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 5)
        val offer = f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 120, 5)
        f.service.collectAll(buyer, toBank = true)
        assertEquals(7, bank.physicalCount("obj.coal"))
        assertEquals(0, offer.collectItems)
        assertEquals(100, offer.collectCoins)
        assertEquals(0, bank.physicalCount("obj.coins"))
    }

    @Test fun `native offer display transmits 64 bit price packets and tax vars`() {
        val packets = mutableListOf<Any>()
        val seller = player(1).apply {
            processedMapClock = 1
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { packets += message }
            }
        }
        val f = Fixture()
        f.exchange.place(1, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, Int.MAX_VALUE, 1)
        VarPlayerIntMapSetter.set(seller, GeConfig.TAX_VARPS[0], 7)
        packets.clear()
        f.service.refresh(seller, 0)
        val update = packets.filterIsInstance<UpdateStockMarketSlotV2>().single()
        assertEquals(0, update.slot)
        assertEquals(UpdateStockMarketSlotV2.SetStockMarketSlot(
            status = f.exchange.offer(1, 0)!!.clientStatus(), obj = InvObj("obj.coal").id,
            price = Int.MAX_VALUE.toLong(), count = 1, completedCount = 0, completedGold = 0L,
        ), update.update)
        assertTrue(packets.any { it == VarpLong(GeConfig.TAX_VARPS[0].asRSCM(), 0L) })
    }

    @Test fun `native GE long vars remain separate from music and legacy fees`() {
        val symbols = listOf(GeConfig.VARP_PRICE) + GeConfig.TAX_VARPS +
            (0 until GeConfig.SLOT_COUNT).map(GeConfig::itemSinkPriceVarp)
        assertEquals(17, symbols.size)
        assertEquals(17, symbols.map { it.asRSCM() }.toSet().size)
        symbols.forEach { symbol ->
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertTrue(type.longValue, symbol)
            assertEquals(dev.openrune.types.varp.VarpLifetime.Temp, type.scope, symbol)
        }
    }

    @Test fun `native history script receives long coin values after its four integer arguments`() {
        val packets = mutableListOf<Any>()
        val buyer = player(1).apply {
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { packets += message }
            }
        }
        val f = Fixture()
        f.exchange.place(2, "seller", 0, GeOfferType.Sell, InvObj("obj.coal").id, 100, 1)
        f.exchange.place(1, "buyer", 0, GeOfferType.Buy, InvObj("obj.coal").id, 100, 1)
        val historyContext = org.rsmod.api.player.protect.ProtectedAccessContextFactory.empty().copy(
            getEventBus = { EventBus() },
        )
        f.service.openHistory(ProtectedAccess(buyer, GameCoroutine(), historyContext))
        val line = packets.filterIsInstance<RunClientScript>().single { it.id == GeConfig.CS2_HISTORY_ADD_LINE }
        assertEquals(listOf(0, InvObj("obj.coal").id, GeOfferType.Buy.clientId, 1, 100L, 0L), line.values)
        assertTrue(line.values.take(4).all { it is Int })
        assertTrue(line.values.drop(4).all { it is Long })
    }

    private fun player(id: Int) = Player().apply {
        accountId = id
        characterId = id
        displayName = "player$id"
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
    }

    private class Fixture {
        val items = GeItemData.fromLines(listOf("453\t13000\t150\tCoal"))
        val exchange = GeExchange(items) { 100L }
        val service = GeService(exchange, items, PlayerList())
        fun confirm(player: Player, type: GeOfferType, price: Int) {
            VarPlayerIntMapSetter.set(player, GeConfig.VARBIT_SELECTED_SLOT, 1)
            VarPlayerIntMapSetter.set(player, GeConfig.VARP_ITEM, InvObj("obj.coal").id)
            VarPlayerIntMapSetter.set(player, GeConfig.VARBIT_TYPE, type.clientId)
            VarPlayerIntMapSetter.set(player, GeConfig.VARBIT_QUANTITY, 1)
            VarPlayerIntMapSetter.set(player, GeConfig.VARP_PRICE, price)
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) as Unsafe }
            val context = unsafe.allocateInstance(ProtectedAccessContext::class.java) as ProtectedAccessContext
            service.confirm(ProtectedAccess(player, GameCoroutine(), context))
        }
    }
}
