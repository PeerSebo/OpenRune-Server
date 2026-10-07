package org.rsmod.content.interfaces.trading

import dev.openrune.ServerCacheManager
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerAccountCommit
import org.rsmod.game.entity.PlayerTradeSocial
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class TradeSafetyTest {
    @Test fun `offering and removing never change either real inventory`() {
        val f = Fixture(); f.open()
        f.offer(f.a, 0, IfButtonOp.Op4)
        assertEquals(100, f.a.inv[0]?.count)
        assertEquals(200, f.b.inv[0]?.count)
        assertEquals(100, f.session().offers[0].items().single().count)
        f.button(f.a, TradeScreenAction.Remove, 0, IfButtonOp.Op2, f.a.inv[0]?.id)
        assertEquals(95, f.session().offers[0].items().single().count)
        f.manager.cancel(f.a)
        assertNull(f.manager.session(f.b))
        assertEquals(100, f.a.inv[0]?.count)
        assertEquals(0, f.saves)
    }

    @Test fun `changed offers reset acceptance and same-tick acceptance cannot advance`() {
        val f = Fixture(); f.open(); f.offer(f.a, 0)
        f.button(f.a, TradeScreenAction.AcceptOffer)
        assertFalse(f.session().accepted.any { it })
        f.tick(); f.button(f.a, TradeScreenAction.AcceptOffer)
        assertTrue(f.session().accepted[0])
        f.offer(f.b, 0)
        assertFalse(f.session().accepted.any { it })
        f.button(f.b, TradeScreenAction.AcceptOffer)
        assertEquals(TradeStage.Offer, f.session().stage)
        assertEquals(0, f.saves)
    }

    @Test fun `confirm requires both players and repeated clicks cannot exchange twice`() {
        val f = Fixture(); f.open(); f.offer(f.a, 0, IfButtonOp.Op4); f.confirm()
        f.button(f.a, TradeScreenAction.AcceptConfirm); f.button(f.b, TradeScreenAction.AcceptConfirm)
        assertEquals(0, f.saves, "First-screen clicks cannot also confirm in the same tick")
        f.tick(); f.button(f.a, TradeScreenAction.AcceptConfirm)
        assertEquals(0, f.saves)
        f.button(f.b, TradeScreenAction.AcceptConfirm)
        repeat(10) { f.button(f.a, TradeScreenAction.AcceptConfirm); f.button(f.b, TradeScreenAction.AcceptConfirm) }
        assertEquals(1, f.saves)
        assertEquals(300, f.b.inv[0]?.count)
        assertTrue(f.a.inv.isEmpty())
        f.manager.cancel(f.a)
        assertEquals(TradeStage.Committing, f.session().stage)
        assertFalse(f.a.canProcess)
        f.manager.onPostTick(f.a)
        assertNotNull(f.manager.session(f.a))
        f.commit!!.result = true
        f.manager.onPostTick(f.a)
        assertNull(f.manager.session(f.a)); assertNull(f.b.pendingAccountCommit)
        assertTrue(f.a.canProcess)
    }

    @Test fun `preparation refusal restores both inventories and closes both screens`() {
        val f = Fixture(refuse = true); f.open(); f.offer(f.a, 0, IfButtonOp.Op4); f.confirm()
        f.tick(); f.button(f.a, TradeScreenAction.AcceptConfirm); f.button(f.b, TradeScreenAction.AcceptConfirm)
        assertEquals(100, f.a.inv[0]?.count); assertEquals(200, f.b.inv[0]?.count)
        assertNull(f.manager.session(f.a)); assertNull(f.manager.session(f.b))
    }

    @Test fun `movement disconnect inventory changes combat and logout cancel before transfer`() {
        val changes: List<(Fixture) -> Unit> = listOf(
            { it.a.coords = CoordGrid(3205, 3200) },
            { it.b.clientDisconnected.set(true) },
            { it.a.inv[0] = InvObj("obj.coins", 101) },
            { org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(it.a, "varp.lastcombat", it.a.currentMapClock) },
            { it.a.manualLogout = true },
            { it.a.tradeSocial = PlayerTradeSocial(2, emptySet(), emptySet()) },
        )
        for (change in changes) {
            val f = Fixture(); f.open(); f.offer(f.a, 0); change(f); f.manager.onPostTick(f.b)
            assertNull(f.manager.session(f.a)); assertEquals(200, f.b.inv[0]?.count); assertEquals(0, f.saves)
        }
    }

    @Test fun `ironmen cannot initiate receive or finish normal trades`() {
        for (mode in 1..3) {
            val first = Fixture(); first.a.gamemode = mode
            first.manager.request(first.a, first.b); first.manager.request(first.b, first.a)
            assertNull(first.manager.session(first.a)); assertEquals(0, first.saves)
            val second = Fixture(); second.open(); second.a.gamemode = mode; second.manager.onPostTick(second.b)
            assertNull(second.manager.session(second.a)); assertEquals(0, second.saves)
        }
    }

    @Test fun `spoofed item ids invalid slots and untradeable items cannot be offered`() {
        val f = Fixture(); f.a.inv[1] = InvObj("obj.amulet_of_ghostspeak"); f.open()
        f.button(f.a, TradeScreenAction.Offer, -1, IfButtonOp.Op4, f.a.inv[0]?.id)
        f.button(f.a, TradeScreenAction.Offer, 100, IfButtonOp.Op4, f.a.inv[0]?.id)
        f.button(f.a, TradeScreenAction.Offer, 0, IfButtonOp.Op4, f.a.inv[1]?.id)
        f.offer(f.a, 1)
        assertTrue(f.session().offers[0].items().isEmpty()); assertEquals(0, f.saves)
    }

    @Test fun `closing offer or confirmation returns unchanged inventories`() {
        for (confirmation in listOf(false, true)) {
            val f = Fixture(); f.open(); f.offer(f.a, 0)
            if (confirmation) f.confirm()
            f.manager.closed(f.a, confirmation)
            assertNull(f.manager.session(f.b)); assertEquals(100, f.a.inv[0]?.count); assertEquals(0, f.saves)
        }
    }

    @Test fun `full inventories may exchange occupied slots but cannot accept extra items`() {
        val a = TradeOffer(List(28) { InvObj("obj.bones") })
        val b = TradeOffer(listOf(InvObj("obj.coins", 10)) + List(27) { null })
        b.change(0, 10, true)
        assertNull(TradePlan.build(a, b, { true }, { it == b.original[0]!!.id }))
        a.change(0, 1, true)
        val plan = checkNotNull(TradePlan.build(a, b, { true }, { it == b.original[0]!!.id }))
        assertEquals(10, plan.first.single { it?.id == b.original[0]!!.id }?.count)
        assertEquals(1, plan.second.filterNotNull().single().count)
    }

    @Test fun `integer overflow and untradeable transfers are refused without mutation`() {
        val a = TradeOffer(listOf(InvObj("obj.coins", Int.MAX_VALUE)))
        val b = TradeOffer(listOf(InvObj("obj.coins", 1)))
        b.change(0, 1, true)
        assertNull(TradePlan.build(a, b, { true }, { true }))
        a.change(0, Int.MAX_VALUE, true)
        assertNull(TradePlan.build(a, b, { false }, { true }))
        assertEquals(Int.MAX_VALUE, a.original[0]?.count)
    }

    @Test fun `offering all matches separate slots and caps native offer totals`() {
        val bones = TradeOffer(List(5) { InvObj("obj.bones") })
        assertTrue(bones.addMatching(2, 5))
        assertEquals(5, bones.display().single().count)
        assertTrue(bones.remaining().all { it == null })
        assertFalse(bones.addMatching(2, Int.MAX_VALUE))
        val coins = TradeOffer(listOf(InvObj("obj.coins", Int.MAX_VALUE), InvObj("obj.coins", Int.MAX_VALUE)))
        assertTrue(coins.addMatching(0, Int.MAX_VALUE))
        assertFalse(coins.addMatching(1, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, coins.display().single().count)
        assertEquals(Int.MAX_VALUE, coins.remaining()[1]?.count)
    }

    @Test fun `random exchanges conserve every item id and variant`() {
        val random = Random(99172)
        repeat(3000) {
            fun contents(): List<InvObj?> = List(28) {
                if (random.nextInt(4) == 0) null else
                    InvObj(if (random.nextBoolean()) "obj.coins" else "obj.bones",
                        if (random.nextBoolean()) 1 else random.nextInt(1, 20), random.nextInt(2))
            }
            val a = TradeOffer(contents()); val b = TradeOffer(contents())
            for (offer in listOf(a, b)) repeat(40) {
                offer.change(random.nextInt(-1, 30), random.nextInt(-2, 100), random.nextBoolean())
            }
            val plan = TradePlan.build(a, b, { true }, { it == InvObj("obj.coins").id })
            if (plan != null) assertEquals(totals(a.original + b.original), totals(plan.first + plan.second))
        }
    }

    private fun totals(items: List<InvObj?>) = items.filterNotNull().groupBy { it.id to it.vars }
        .mapValues { (_, same) -> same.sumOf { it.count.toLong() } }

    private class Fixture(private val refuse: Boolean = false) {
        val a = player("Trade A", 100, 3200)
        val b = player("Trade B", 200, 3201)
        var saves = 0
        var commit: Commit? = null
        val manager = TradeManager(TradePersistence { first, second, _ ->
            saves++
            if (refuse) null else Commit(listOf(first, second)).also {
                commit = it; first.pendingAccountCommit = it; second.pendingAccountCommit = it
            }
        }, EventBus(), object : MarketPrices { override fun get(type: dev.openrune.types.ItemServerType): Int = 1 })
        fun session() = checkNotNull(manager.session(a))
        fun open() { manager.request(b, a); manager.request(a, b); checkNotNull(manager.session(a)); tick() }
        fun tick() { a.currentMapClock++; b.currentMapClock++ }
        fun offer(player: Player, slot: Int, op: IfButtonOp = IfButtonOp.Op1) =
            button(player, TradeScreenAction.Offer, slot, op, player.inv[slot]?.id)
        fun confirm() { tick(); button(a, TradeScreenAction.AcceptOffer); button(b, TradeScreenAction.AcceptOffer) }
        fun button(player: Player, action: TradeScreenAction, slot: Int = -1, op: IfButtonOp = IfButtonOp.Op1, id: Int? = null) = runBlocking {
            manager.button(ProtectedAccess(player, GameCoroutine("trade-test"), ProtectedAccessContextFactory.empty()), action, slot, op, id)
        }
        private fun player(name: String, coins: Int, x: Int) = Player().apply {
            displayName = name; coords = CoordGrid(x, 3200); currentMapClock = 100; processedMapClock = 100
            tradeSocial = PlayerTradeSocial(0, emptySet(), emptySet())
            inv = Inventory.create("inv.inv"); inv[0] = InvObj("obj.coins", coins)
        }
    }

    private class Commit(private val players: List<Player>) : PlayerAccountCommit {
        var result: Boolean? = null
        override val succeeded: Boolean? get() = result
        override fun release() { check(result == true); players.forEach { it.pendingAccountCommit = null } }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() } }
}
