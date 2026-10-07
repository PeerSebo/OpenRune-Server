package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.StallShopAccess
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShopkeeperScriptTest {
    @Test
    fun `crafting and gem conversations open their existing stock and can be declined`() {
        for (merchant in listOf("npc.rommik", "npc.herquin")) {
            val binding = ShopkeeperCatalog.shops.single { merchant in it.merchants }
            for (accept in listOf(true, false)) {
                val fixture = Fixture(merchant, ordinaryDialogue = true)
                fixture.start(InteractionOp.Op1)
                fixture.finish(if (accept) 1 else 2)
                fixture.assertCompleted()
                if (accept) {
                    val opened = checkNotNull(fixture.player.openedShop)
                    assertEquals(binding.inventory, opened.inv.internalName)
                    assertEquals(binding.allowSelling, opened.allowSelling)
                    assertEquals(binding.infiniteStock, opened.infiniteStock)
                    assertTrue(opened.inv.isNotEmpty())
                    assertTrue(fixture.player.ui.containsModal("interface.shopmain"))
                } else {
                    assertNull(fixture.player.openedShop)
                }
            }
        }
    }

    @Test
    fun `every mapped trade action dispatches and opens its own stocked inventory`() {
        val bindings = ShopkeeperScript(Shops(EventBus())).resolveBindings()
        val tradeActions = bindings.sumOf { (type, _) ->
            (0..4).count { type.actions.getOpOrNull(it).equals("Trade", ignoreCase = true) }
        }
        println("Mapped shop validation: $tradeActions native Trade actions across ${bindings.size} NPC definitions.")
        assertTrue(ShopkeeperCatalog.shops.size >= 140)
        for ((type, binding) in bindings) {
            for (slot in 0..4) {
                if (!type.actions.getOpOrNull(slot).equals("Trade", ignoreCase = true)) continue
                val fixture = Fixture(type)
                fixture.start(InteractionOp.entries[slot])
                fixture.assertCompleted()
                val opened = checkNotNull(fixture.player.openedShop)
                assertEquals(binding.inventory, opened.inv.internalName, "npc=${type.id}, slot=$slot")
                binding.stock?.let { stock ->
                    assertSame(stock.inventory, opened.inv)
                    assertEquals(stock.stock, opened.inv.type.stock)
                    assertEquals(stock.name, opened.inv.type.name)
                }
                assertTrue(opened.inv.size > 0, binding.inventory)
                assertTrue(opened.inv.isNotEmpty(), binding.inventory)
                assertEquals("currency.standard_gp", opened.currency)
                assertEquals(binding.infiniteStock, opened.infiniteStock)
                assertEquals(binding.allowSelling, opened.allowSelling)
                assertTrue(fixture.player.ui.containsModal("interface.shopmain"))
                assertTrue(fixture.player.ui.containsModal("interface.shopside"))
            }
        }
    }

    @Test
    fun `talking can browse the same shop and declining opens no shop`() {
        for (accept in listOf(true, false)) {
            val fixture = Fixture("npc.cassie")
            fixture.start(InteractionOp.Op1)
            fixture.advance()
            fixture.advance(if (accept) 1 else 2)
            fixture.assertCompleted()
            if (accept) {
                assertEquals("inv.shieldshop", fixture.player.openedShop?.inv?.internalName)
            } else {
                assertNull(fixture.player.openedShop)
            }
        }
    }

    @Test
    fun `bespoke trade handlers retain priority in either startup order`() {
        for (catalogFirst in listOf(true, false)) {
            val events = EventBus()
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val script = ShopkeeperScript(Shops(events))
            if (catalogFirst) with(script) { context.startup() }
            with(context) { onOpNpc3("npc.cassie") { } }
            if (!catalogFirst) with(script) { context.startup() }
            val event = NpcInteractions(events).opTrigger(
                Player(), Npc("npc.cassie", CoordGrid(2979, 3383)), InteractionOp.Op3,
            )
            assertInstanceOf(NpcEvents.Op3::class.java, event)
        }
    }

    @Test
    fun `a transformed shopkeeper keeps a bespoke handler on its parent`() {
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(ShopkeeperScript(Shops(events))) { context.startup() }
        with(context) { onOpNpc3("npc.fortis_shop_seamstress") { } }
        val event = NpcInteractions(events).opTrigger(
            Player(), Npc("npc.fortis_shop_seamstress", CoordGrid(3200, 3201)), InteractionOp.Op3,
        )
        assertInstanceOf(NpcEvents.Op3::class.java, event)
        assertEquals("npc.fortis_shop_seamstress".asRSCM().toLong(), event?.id)
    }

    @Test
    fun `trade-only service slots do not replace other actions or unlock non-trading variants`() {
        val sawmill = Fixture("npc.poh_sawmill_opp")
        assertNull(sawmill.interaction(InteractionOp.Op3))
        assertInstanceOf(NpcUnimplementedEvents.Op4::class.java, sawmill.interaction(InteractionOp.Op4))
        val hunter = Fixture("npc.hunting_shop_owner_yanille")
        assertNull(hunter.interaction(InteractionOp.Op3))
        assertInstanceOf(NpcUnimplementedEvents.Op5::class.java, hunter.interaction(InteractionOp.Op5))
        val script = ShopkeeperScript(Shops(EventBus()))
        for ((type, _) in script.resolveBindings()) {
            if (type.id == "npc.deepfin_dwarf_durrik_1op".asRSCM()) continue
            if ((0..4).any { type.actions.getOpOrNull(it).equals("Trade", ignoreCase = true) }) continue
            if (type.multiNpc.isNotEmpty()) continue
            val fixture = Fixture(type)
            assertNull(fixture.interaction(InteractionOp.Op1), "Non-trading variant ${type.id}")
            assertNull(fixture.interaction(InteractionOp.Op3), "Non-trading variant ${type.id}")
        }
        assertNull(Fixture("npc.oziach").interaction(InteractionOp.Op3))
        assertNull(Fixture("npc.tzhaar_merchant_equipment").interaction(InteractionOp.Op3))
    }

    @Test
    fun `counter approach trades at two tiles and keeps approaching from farther away`() {
        for (distance in listOf(2, 3, 20)) {
            val fixture = Fixture("npc.baker_merchant_ardougne")
            fixture.player.coords = fixture.npc.coords.translateZ(distance)
            fixture.start(InteractionOp.Op3, approach = true)
            fixture.assertCompleted()
            assertEquals(distance == 2, fixture.player.openedShop != null)
        }
    }

    @Test
    fun `tea seller requires normal adjacent reach for talking and trading`() {
        val fixture = Fixture("npc.tea_seller")
        for (op in listOf(InteractionOp.Op1, InteractionOp.Op3)) {
            assertNull(fixture.apInteraction(op))
            assertNotNull(fixture.interaction(op))
        }
        fixture.start(InteractionOp.Op3)
        fixture.assertCompleted()
        assertEquals("inv.teashop", fixture.player.openedShop?.inv?.internalName)
    }

    @Test
    fun `counter approach does not bypass an existing bespoke trade handler`() {
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(ShopkeeperScript(Shops(events))) { context.startup() }
        with(context) { onOpNpc3("npc.tea_seller") { } }
        assertNull(NpcInteractions(events).apTrigger(
            Player(), Npc("npc.tea_seller", CoordGrid(3200, 3201)), InteractionOp.Op3,
        ))
    }

    @Test
    fun `native and profile bar counters retain their two tile native trade reach`() {
        val bindings = ShopkeeperScript(Shops(EventBus())).resolveBindings()
        for (key in listOf("inv.blurberrybar", "profile.lovakengj_bartender")) {
            val type = bindings.first { (type, binding) ->
                binding.key == key && (0..4).any { type.actions.getOpOrNull(it).equals("Trade", ignoreCase = true) }
            }.first
            val slot = (0..4).first { type.actions.getOpOrNull(it).equals("Trade", ignoreCase = true) }
            for (distance in listOf(2, 3)) {
                val fixture = Fixture(type)
                fixture.player.coords = fixture.npc.coords.translateZ(distance)
                fixture.start(InteractionOp.entries[slot], approach = true)
                fixture.assertCompleted()
                assertEquals(distance == 2, fixture.player.openedShop != null, key)
            }
        }
    }

    @Test
    fun `gardeners sell unlimited supplies without replacing protection actions`() {
        val fixture = Fixture("npc.farming_gardener_tree_1")
        assertNull(fixture.interaction(InteractionOp.Op3))
        fixture.start(InteractionOp.Op5)
        fixture.assertCompleted()
        val shop = checkNotNull(fixture.player.openedShop)
        assertEquals("inv.farmer_supplies", shop.inv.internalName)
        assertTrue(shop.infiniteStock)
        assertFalse(shop.allowSelling)
    }

    @Test
    fun `Port Roberts merchants keep trading after theft`() {
        val fixture = Fixture("npc.port_roberts_silver_trader")
        StallShopAccess.record(fixture.player, "loc.port_roberts_market_stall_silver", fixture.npc.coords)
        fixture.start(InteractionOp.Op3)
        fixture.assertCompleted()
        assertEquals("inv.port_roberts_silver_trader", fixture.player.openedShop?.inv?.internalName)
    }

    @Test
    fun `stall owners refuse recent thieves without blocking another market or account`() {
        val fixture = Fixture("npc.tea_seller")
        VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", 100)
        StallShopAccess.record(fixture.player, "loc.tea_stall", fixture.npc.coords)
        fixture.start(InteractionOp.Op3)
        fixture.assertCompleted()
        assertNull(fixture.player.openedShop)
        assertTrue(StallShopAccess.canTrade(Player(), fixture.npc.coords, setOf("loc.tea_stall")))
        assertTrue(StallShopAccess.canTrade(fixture.player, CoordGrid(1000, 1000), setOf("loc.tea_stall")))
        assertTrue(StallShopAccess.canTrade(fixture.player, fixture.npc.coords, setOf("loc.seed_stall")))
        VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", 1_099)
        assertFalse(StallShopAccess.canTrade(fixture.player, fixture.npc.coords, setOf("loc.tea_stall")))
        VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", 1_100)
        assertTrue(StallShopAccess.canTrade(fixture.player, fixture.npc.coords, setOf("loc.tea_stall")))
    }

    @Test
    fun `bakers and spice merchants enforce their own refusal duration`() {
        for ((merchant, stall, duration) in listOf(
            Triple("npc.baker_merchant_ardougne", "loc.cakethiefstall", 1_500),
            Triple("npc.spice_merchant_ardougne", "loc.spicethiefstall", 600),
        )) {
            val fixture = Fixture(merchant)
            StallShopAccess.record(fixture.player, stall, fixture.npc.coords)
            VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", duration - 1)
            fixture.start(InteractionOp.Op3)
            fixture.assertCompleted()
            assertNull(fixture.player.openedShop)
            VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", duration)
            fixture.start(InteractionOp.Op3)
            fixture.assertCompleted()
            assertNotNull(fixture.player.openedShop)
        }
    }

    @Test
    fun `both island greengrocers retain fifteen hundred tick theft refusal on the native trade action`() {
        for ((merchant, stall) in listOf(
            "npc.misc_veg_monger" to "loc.misc_veg_market",
            "npc.etc_veg_monger" to "loc.etc_veg_market",
        )) {
            val fixture = Fixture(merchant)
            StallShopAccess.record(fixture.player, stall, fixture.npc.coords)
            VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", 1499)
            fixture.start(InteractionOp.Op3)
            fixture.assertCompleted()
            assertNull(fixture.player.openedShop)
            VarPlayerIntMapSetter.set(fixture.player, "varp.playtime", 1500)
            fixture.start(InteractionOp.Op3)
            fixture.assertCompleted()
            assertNotNull(fixture.player.openedShop)
        }
    }

    @Test
    fun `Durrik unlocks trade only when the player asks to browse`() {
        for (accept in listOf(true, false)) {
            val fixture = Fixture("npc.deepfin_dwarf_durrik")
            assertNull(fixture.interaction(InteractionOp.Op3))
            fixture.start(InteractionOp.Op1)
            fixture.advance()
            fixture.advance(if (accept) 1 else 2)
            fixture.assertCompleted()
            if (accept) {
                assertEquals("inv.deepfin_dwarf_durrik_shop", fixture.player.openedShop?.inv?.internalName)
                assertNotNull(fixture.interaction(InteractionOp.Op3))
            } else {
                assertNull(fixture.player.openedShop)
                assertNull(fixture.interaction(InteractionOp.Op3))
            }
        }
    }

    @Test
    fun `Roavar exchanges five coins for mead without charging when it cannot fit`() {
        for ((coins, full, success) in listOf(
            Triple(4, false, false), Triple(5, false, true),
            Triple(10, true, false), Triple(5, true, true),
        )) {
            val fixture = Fixture("npc.werewolfinnkeeper")
            if (full) {
                for (slot in 0 until fixture.player.inv.size) {
                    fixture.player.inv[slot] = InvObj("obj.spade")
                }
            }
            fixture.player.inv[0] = InvObj("obj.coins", coins)
            fixture.start(InteractionOp.Op3)
            fixture.assertCompleted()
            assertEquals(coins - if (success) 5 else 0, fixture.player.inv.count("obj.coins"))
            assertEquals(if (success) 1 else 0, fixture.player.inv.count("obj.moonlight_mead"))
            assertNull(fixture.player.openedShop)
        }
    }

    private class Fixture(type: NpcServerType, ordinaryDialogue: Boolean = false) {
        constructor(symbol: String, ordinaryDialogue: Boolean = false) :
            this(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), ordinaryDialogue)

        val player = Player().apply {
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
        }
        val npc = Npc(type, CoordGrid(3200, 3201))
        private val events = EventBus()
        private val interactions = NpcInteractions(events)
        private val coroutine = GameCoroutine("shopkeeper-test")
        private var result: Result<Unit>? = null

        init {
            if (ordinaryDialogue) {
                val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
                with(RommikDialogue(Shops(events))) { context.startup() }
                with(HerquinDialogue(Shops(events))) { context.startup() }
            }
            with(RoavarScript()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            with(ShopkeeperScript(Shops(events))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun interaction(op: InteractionOp) = interactions.opTrigger(player, npc, op)

        fun apInteraction(op: InteractionOp) = interactions.apTrigger(player, npc, op)

        fun start(op: InteractionOp, approach: Boolean = false) {
            val context = ProtectedAccessContextFactory.empty().copy(
                getRandom = { DefaultGameRandom(1) },
                getEventBus = { events },
                getAlignment = { TextAlignment() },
                getNpcInteractions = { interactions },
            )
            val access = ProtectedAccess(player, coroutine, context)
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = {
                val event = checkNotNull(
                    if (approach) interactions.apTrigger(player, npc, op) else interaction(op)
                ) { "No handler for npc=${npc.id}, op=$op" }
                assertTrue(events.publish(access, event))
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun advance(choice: Int = 1) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            val component = when {
                player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                else -> error("Unexpected shopkeeper dialogue interface")
            }
            coroutine.resumeWith(ResumePauseButtonInput(
                component, if (component.endsWith(":options")) choice else -1,
            ))
            result?.getOrThrow()
        }

        fun assertCompleted() = checkNotNull(result).getOrThrow()

        fun finish(choice: Int) {
            var steps = 0
            while (coroutine.isSuspended && steps++ < 10) advance(choice)
            assertFalse(coroutine.isSuspended)
        }
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
