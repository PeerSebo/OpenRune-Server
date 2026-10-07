package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import java.util.IdentityHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.shops.Shops
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.shop.Shop
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ConditionalShopkeeperScriptTest {
    @Test
    fun `Martin requires base levels and permits 99 Thieving without Agility`() {
        val f = Fixture()
        for ((thieving, agility, allowed) in listOf(
            Triple(49, 50, false), Triple(50, 49, false),
            Triple(50, 50, true), Triple(99, 1, true),
        )) {
            val player = player(thieving, agility)
            player.statMap.setCurrentLevel("stat.thieving", 99)
            player.statMap.setCurrentLevel("stat.agility", 99)
            assertEquals(allowed, f.trade(player, "npc.roguesden_trader") != null)
        }
        val drained = player(50, 50)
        drained.statMap.setCurrentLevel("stat.thieving", 1)
        drained.statMap.setCurrentLevel("stat.agility", 1)
        assertNotNull(f.trade(drained, "npc.roguesden_trader"))
    }

    @Test
    fun `Martin chooses the appropriate cape and bundles its hood`() {
        val f = Fixture()
        for ((thieving, other99, inventory) in listOf(
            Triple(98, false, "inv.roguesden_shop"),
            Triple(99, false, "inv.roguesden_shop_skillcape"),
            Triple(99, true, "inv.roguesden_shop_skillcape_trimmed"),
        )) {
            val player = player(thieving, 50)
            if (other99) player.statMap.setBaseLevel("stat.attack", 99)
            val shop = checkNotNull(f.trade(player, "npc.roguesden_trader"))
            assertEquals(inventory, shop.inv.internalName)
            assertEquals(100.0, shop.sellPercentage)
            assertEquals(60.0, shop.buyPercentage)
            assertEquals(2.0, shop.changePercentage)
            assertTrue(shop.inv.type.allStock)
            assertTrue("obj.roguesden_stethoscope".asRSCM() in shop.unsellableItems)
            if (thieving == 99) {
                val cape = if (other99) "obj.skillcape_thieving_trimmed" else "obj.skillcape_thieving"
                assertEquals(cape.asRSCM(), shop.inv[11]?.id)
                assertTrue(cape.asRSCM() in shop.unsellableItems)
                assertEquals(listOf(InvObj("obj.skillcape_thieving_hood")), shop.purchaseExtras[cape.asRSCM()])
            } else {
                assertNull(shop.inv[11])
            }
        }
    }

    @Test
    fun `Martin stocks are shared within each tier and isolated across iron and cape tiers`() {
        val f = Fixture()
        val stocks = IdentityHashMap<Inventory, Boolean>()
        for (iron in listOf(false, true)) {
            for (tier in 0..2) {
                fun customer() = player(if (tier == 0) 50 else 99, 50).apply {
                    gamemode = if (iron) 1 else 0
                    if (tier == 2) statMap.setBaseLevel("stat.attack", 99)
                }
                val first = checkNotNull(f.trade(customer(), "npc.roguesden_trader"))
                val second = checkNotNull(f.trade(customer(), "npc.roguesden_trader"))
                assertSame(first.inv, second.inv)
                stocks[first.inv] = true
            }
        }
        assertEquals(6, stocks.size)
    }

    @Test
    fun `Noterazzo shares discounted stock across skull states and account types`() {
        val f = Fixture()
        var shared: Inventory? = null
        for (iron in listOf(false, true)) {
            for (skull in listOf(null, 0)) {
                val player = player(1, 1).apply {
                    gamemode = if (iron) 1 else 0
                    skullIcon = skull
                }
                val shop = checkNotNull(f.trade(player, "npc.noterazzo"))
                assertEquals("inv.taxfree", shop.inv.internalName)
                assertEquals(90.0, shop.sellPercentage)
                assertEquals(60.0, shop.buyPercentage)
                assertEquals(3.0, shop.changePercentage)
                assertTrue(shop.inv.type.allStock)
                if (shared == null) shared = shop.inv else assertSame(shared, shop.inv)
            }
        }
    }

    private fun player(thieving: Int, agility: Int) = Player().apply {
        coords = CoordGrid(3200, 3200)
        currentMapClock = 100
        processedMapClock = 100
        inv = Inventory.create("inv.inv")
        statMap.setBaseLevel("stat.thieving", thieving.toByte())
        statMap.setCurrentLevel("stat.thieving", thieving.toByte())
        statMap.setBaseLevel("stat.agility", agility.toByte())
        statMap.setCurrentLevel("stat.agility", agility.toByte())
    }

    private class Fixture {
        private val events = EventBus()
        private val interactions = NpcInteractions(events)

        init {
            with(ConditionalShopkeeperScript(Shops(events))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun trade(player: Player, merchant: String): Shop? {
            val npc = Npc(merchant, player.coords.translateZ(1))
            val event = checkNotNull(interactions.opTrigger(player, npc, InteractionOp.Op3))
            val coroutine = GameCoroutine("conditional-shop-test")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getNpcInteractions = { interactions },
            )
            val access = ProtectedAccess(player, coroutine, context)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
            return player.openedShop
        }
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
