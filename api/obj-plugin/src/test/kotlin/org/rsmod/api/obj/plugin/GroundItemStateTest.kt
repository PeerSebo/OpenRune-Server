package org.rsmod.api.obj.plugin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.api.player.hook.PlayerObjTakeRedirector
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroundItemStateTest {
    private var oldTransactions: Any? = null
    private val transactionField by lazy { Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt").getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true } }

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun transactions() { oldTransactions = transactionField.get(null); transactionField.set(null, InvTransactions.from()) }

    @AfterEach fun restore() { transactionField.set(null, oldTransactions) }

    @Test fun `charged whistle survives real ground registration and pickup exactly once`() {
        val f = Fixture()
        val held = item("obj.hg_quetzalwhistle_perfected", vars = 73)
        val ground = Obj.fromOwner(f.player, f.player.coords, held)
        assertTrue(f.repo.add(ground, 100))
        f.take(ground)
        assertEquals(held, f.player.inv[0])
        assertEquals(0, f.registry.count())
        f.take(ground)
        assertEquals(1, f.player.inv.count("obj.hg_quetzalwhistle_perfected"))
    }

    @Test fun `late full inventory leaves the original charged item on the ground`() {
        val f = Fixture()
        val ground = Obj.fromOwner(f.player, f.player.coords, item("obj.hg_quetzalwhistle_basic", vars = 5))
        f.repo.add(ground, 100)
        f.player.inv.objs.fill(item("obj.logs"))
        f.take(ground)
        assertEquals(1, f.registry.count())
        assertEquals(5, f.repo.findAll(f.player.coords).single().vars)
        f.player.inv[4] = null
        f.take(ground)
        assertEquals(5, f.player.inv[4]?.vars)
        assertEquals(0, f.registry.count())
    }

    @Test fun `manual inventory drop preserves charges before the player picks it back up`() {
        val f = Fixture()
        val original = item("obj.hg_quetzalwhistle_perfected", vars = 37)
        f.player.inv[0] = original
        val market = object : MarketPrices { override fun get(type: dev.openrune.types.ItemServerType): Int = type.cost }
        val klass = Class.forName("org.rsmod.api.player.interact.HeldDropOp")
        val dropper = klass.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(EventBus(), f.repo, market, GroundItemDropResolver(emptySet()))
        klass.declaredMethods.single { it.name == "drop" }.apply { isAccessible = true }
            .invoke(dropper, f.player, f.player.inv, 0, original, ServerCacheManager.getItem(original.id))
        assertNull(f.player.inv[0])
        val ground = f.repo.findAll(f.player.coords).single()
        assertEquals(37, ground.vars)
        f.take(ground)
        assertEquals(original, f.player.inv[0])
    }

    @Test fun `splitting nonstackable death drops preserves original ownership and item state`() {
        val f = Fixture()
        val victim = Player().apply { uuid = 2; observerUUID = 2; coords = f.player.coords }
        val ground = Obj.fromPvp(f.player, victim, item("obj.hg_quetzalwhistle_enhanced", count = 2, vars = 15))
        f.repo.add(ground, 100)
        val parts = f.repo.findAll(f.player.coords).toList()
        assertEquals(2, parts.size)
        for (part in parts) { assertEquals(15, part.vars); assertEquals(2L, part.ownerId); assertEquals(1L, part.receiverId); assertEquals(1, part.count) }
    }

    @Test fun `same item stacks with different server state cannot merge on ground`() {
        val f = Fixture()
        f.repo.add(Obj.fromOwner(f.player, f.player.coords, item("obj.coins", 10, 1)), 100)
        f.repo.add(Obj.fromOwner(f.player, f.player.coords, item("obj.coins", 20, 2)), 100)
        assertEquals(2, f.registry.count())
        f.repo.add(Obj.fromOwner(f.player, f.player.coords, item("obj.coins", 30, 1)), 100)
        assertEquals(2, f.registry.count())
        assertEquals(40, f.repo.findAll(f.player.coords).single { it.vars == 1 }.count)
    }

    @Test fun `redirect refusing a full inventory restores the original charged ground item`() {
        val refused = object : PlayerObjTakeRedirectHook {
            override fun redirects(player: Player, obj: Obj, objType: dev.openrune.types.ItemServerType) = true
            override fun take(player: Player, obj: Obj, objType: dev.openrune.types.ItemServerType) = false
        }
        val f = Fixture(setOf(refused))
        f.player.inv.objs.fill(item("obj.logs"))
        val ground = Obj.fromOwner(f.player, f.player.coords, item("obj.hg_quetzalwhistle_basic", vars = 11))
        assertTrue(f.repo.add(ground, 100))
        f.take(ground)
        assertSame(ground, f.repo.findAll(f.player.coords).single())
        assertEquals(11, ground.vars)
        f.player.inv[0] = null
        f.take(ground)
        assertEquals(11, f.player.inv[0]?.vars)
        assertEquals(0, f.registry.count())
    }

    private class Fixture(redirects: Set<PlayerObjTakeRedirectHook> = emptySet()) {
        val player = Player().apply {
            uuid = 1; observerUUID = 1; coords = CoordGrid(3200, 3200)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        val registry = ObjRegistry(ZoneUpdateMap())
        val repo = ObjRepository(MapClock(100), registry)
        val plugin = ObjTakePlugin(repo, PlayerObjTakeValidator(emptySet()), PlayerObjTakeRedirector(redirects))
        fun take(obj: Obj) { ObjTakePlugin::class.java.declaredMethods.single { it.name == "takeClose" }.apply { isAccessible = true }.invoke(plugin, player, obj, ServerCacheManager.getItem(obj.type)) }
    }
    private fun item(symbol: String, count: Int = 1, vars: Int = 0) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())), count, vars)
}
