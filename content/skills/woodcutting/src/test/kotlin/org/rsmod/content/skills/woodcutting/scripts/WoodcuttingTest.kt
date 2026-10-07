package org.rsmod.content.skills.woodcutting.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ObjectServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeActivelyCutTicks
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeDespawnTime
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLastCut
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLocId
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLogs
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeStump
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeXp
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WoodcuttingTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @BeforeEach
    fun installInventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach
    fun restoreInventoryServices() {
        restore.asReversed().forEach { it() }
        restore.clear()
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource("attempt,full", "attempt,no axe", "attempt,low level", "cut,full", "cut,no axe", "cut,low level")
    fun `failed requirements stop the old animation without scheduling another chop`(entry: String, reason: String) {
        val fixture = Fixture()
        when (reason) {
            "full" -> fixture.player.inv.objs.fill(fixture.item("obj.logs"))
            "no axe" -> fixture.player.inv.fillNulls()
            "low level" -> fixture.player.statMap.setCurrentLevel("stat.woodcutting", 1)
        }
        if (reason == "full") fixture.player.worn[3] = fixture.item("obj.bronze_axe")
        fixture.player.actionDelay = fixture.clock.cycle - 1
        fixture.player.skillAnimDelay = fixture.clock.cycle - 1
        fixture.invoke(entry)
        fixture.assertNoAwardOrContinuation()
        assertEquals(0, fixture.random.successRolls)
        assertEquals(0, fixture.random.integerRolls)
    }

    @Test
    fun `a stale tree interaction cannot produce a log after another player depletes it`() {
        val fixture = Fixture()
        fixture.locs.change(fixture.tree, fixture.type.treeStump, 20)
        fixture.invoke()
        fixture.assertNoAwardOrContinuation(expectedTreeId = fixture.type.treeStump.id)
        assertEquals(0, fixture.random.successRolls)
    }

    @ParameterizedTest(name = "product hook {0}")
    @CsvSource("cancel", "too many logs")
    fun `rejected product leaves an exhausted tree intact with no rewards or continuation`(hook: String) {
        val fixture = Fixture()
        val inventoryBefore = fixture.player.inv.objs.toList()
        val timer = fixture.exhaustTree()
        fixture.events.subscribeUnbound(SkillingProductPrepareEvent::class.java) {
            if (hook == "cancel") product.cancelled = true else product.count = 29
        }
        fixture.invoke()
        fixture.assertNoAwardOrContinuation()
        assertEquals(inventoryBefore, fixture.player.inv.objs.toList())
        assertSame(timer, fixture.timer())
        assertEquals(1, fixture.random.successRolls)
        assertEquals(0, fixture.random.integerRolls, "Rejected logs must not roll a nest, pet or depletion")
    }

    @Test
    fun `a separately resolved visual type does not invalidate the stored base loc`() {
        // Base loc and resolved type are separate interaction arguments; preserve their identity contract.
        val fixture = Fixture(baseLoc = "loc.tree2")
        assertTrue(fixture.tree.id != fixture.type.id)
        fixture.events.subscribeUnbound(SkillingProductPrepareEvent::class.java) { product.depletes = false }
        fixture.invoke()
        fixture.assertOneLog()
        assertEquals(fixture.tree.entity, fixture.locs.findExact(fixture.tree.coords, fixture.tree.shape)?.entity)
        assertEquals(InteractionOp.Op3, (fixture.player.interaction as InteractionLocOp).op)
    }

    @Test
    fun `success rolls remain four world ticks apart throughout consecutive chop invocations`() {
        val fixture = Fixture()
        val awardTicks = mutableListOf<Int>()
        fixture.events.subscribeUnbound(SkillingProductPrepareEvent::class.java) {
            awardTicks += fixture.clock.cycle
            product.depletes = false
        }
        for (tick in 100..108) {
            fixture.clock.cycle = tick
            fixture.player.currentMapClock = tick
            fixture.player.processedMapClock = tick
            fixture.invoke()
            assertEquals(InteractionOp.Op3, (fixture.player.interaction as InteractionLocOp).op)
        }
        assertEquals(listOf(100, 104, 108), awardTicks)
        assertEquals(3, fixture.random.successRolls)
        assertEquals(3, fixture.player.inv.count { it?.id == fixture.type.treeLogs.id })
        assertEquals((fixture.type.treeXp * 30).toInt(), fixture.player.statMap.getFineXP("stat.woodcutting"))
    }

    @Test
    fun `starting a chop cannot shorten a longer delay owned by another action`() {
        val fixture = Fixture()
        fixture.player.actionDelay = 120
        fixture.player.skillAnimDelay = 99
        fixture.invoke("attempt")
        assertEquals(120, fixture.player.actionDelay)
        assertEquals(103, fixture.player.skillAnimDelay)
        assertEquals(InteractionOp.Op1, (fixture.player.interaction as InteractionLocOp).op)
        assertEquals(0, fixture.random.successRolls)
        assertEquals(0, fixture.player.statMap.getFineXP("stat.woodcutting"))
    }

    @Test
    fun `a nondepleting product hook preserves an exhausted tree and continues chopping`() {
        val fixture = Fixture()
        val timer = fixture.exhaustTree()
        fixture.events.subscribeUnbound(SkillingProductPrepareEvent::class.java) { product.depletes = false }
        fixture.invoke()
        fixture.assertOneLog()
        assertSame(timer, fixture.timer())
        assertEquals(fixture.tree.entity, fixture.locs.findExact(fixture.tree.coords, fixture.type)?.entity)
        assertEquals(InteractionOp.Op3, (fixture.player.interaction as InteractionLocOp).op)
    }

    @Test
    fun `committed final log changes the tree once and removes its timer without requeuing`() {
        val fixture = Fixture()
        fixture.exhaustTree()
        fixture.invoke()
        fixture.assertOneLog()
        assertEquals(fixture.type.treeStump.id, fixture.locs.findAll(fixture.tree.coords).single().id)
        assertEquals(fixture.tree.angleId, fixture.locs.findAll(fixture.tree.coords).single().angleId)
        assertEquals(fixture.tree.shapeId, fixture.locs.findAll(fixture.tree.coords).single().shapeId)
        assertNull(fixture.timer())
        assertNull(fixture.player.interaction)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)

        // A queued/stale click on the old full type cannot repeat its award.
        fixture.player.actionDelay = fixture.clock.cycle
        fixture.invoke()
        fixture.assertOneLog()
        assertNull(fixture.player.interaction)
    }

    @ParameterizedTest(name = "orphan timer: {0}")
    @CsvSource("replaced tree", "unknown type")
    fun `a newly created timer for a missing tree safely removes itself`(reason: String) {
        val fixture = Fixture()
        val timer = fixture.exhaustTree()
        when (reason) {
            "replaced tree" -> fixture.locs.change(fixture.tree, fixture.type.treeStump, 20)
            "unknown type" -> timer.treeLocId = Int.MAX_VALUE
        }
        with(fixture.script) { timer.treeDespawnTick() }
        assertNull(fixture.timer())
    }

    @Test
    fun `a new tree at the same location gets a fresh timer instead of inheriting depletion`() {
        val fixture = Fixture()
        val oldTimer = fixture.exhaustTree()
        oldTimer.treeLocId = "loc.tree2".asRSCM()
        assertFalse(fixture.script.isTreeDespawnRequired(fixture.tree))
        fixture.script.treeSwingDespawnTick(fixture.tree, fixture.type)
        val newTimer = checkNotNull(fixture.timer())
        assertNotSame(oldTimer, newTimer)
        assertEquals(fixture.tree.id, newTimer.treeLocId)
        assertEquals(0, newTimer.treeActivelyCutTicks)
        assertEquals(fixture.clock.cycle, newTimer.treeLastCut)
        assertEquals(fixture.type.treeDespawnTime, newTimer.durationStart)
        assertFalse(fixture.script.isTreeDespawnRequired(fixture.tree))
    }

    @Test
    fun `active chopping accumulates once per world tick and idle time restores the tree`() {
        val fixture = Fixture()
        fixture.script.treeSwingDespawnTick(fixture.tree, fixture.type)
        val timer = checkNotNull(fixture.timer())
        fixture.script.treeSwingDespawnTick(fixture.tree, fixture.type)
        assertEquals(0, timer.treeActivelyCutTicks)

        fixture.tickTimer(timer)
        assertEquals(1, timer.treeActivelyCutTicks)
        fixture.script.treeSwingDespawnTick(fixture.tree, fixture.type)
        fixture.tickTimer(timer)
        assertEquals(2, timer.treeActivelyCutTicks)
        fixture.tickTimer(timer)
        assertEquals(1, timer.treeActivelyCutTicks)
        fixture.tickTimer(timer)
        assertNull(fixture.timer())
        assertEquals(fixture.tree.entity, fixture.locs.findExact(fixture.tree.coords, fixture.type)?.entity)
    }

    private class Fixture(baseLoc: String = "loc.oaktree") {
        val clock = MapClock(100)
        val events = EventBus()
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val random = ChopRandom()
        val type: ObjectServerType = checkNotNull(ServerCacheManager.getObject("loc.oaktree".asRSCM()))
        val tree = BoundLocInfo(
            LocInfo(2, CoordGrid(3200, 3200), LocEntity(baseLoc.asRSCM(), 10, 1)),
            type.width, type.length, type.forceApproachFlags,
        )
        val player = Player().apply {
            coords = CoordGrid(3199, 3200)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            inv[0] = item("obj.bronze_axe")
            statMap.setBaseLevel("stat.woodcutting", 99)
            statMap.setCurrentLevel("stat.woodcutting", 99)
            actionDelay = clock.cycle
            skillAnimDelay = clock.cycle + 4
            pendingSequence = EntitySeq(879, 0, 5)
        }
        private val collision = CollisionFlagMap().apply {
            for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) allocateIfAbsent(x, z, 0)
        }
        private val controllers = ControllerList()
        private val controllerRegistry = ControllerRegistry(clock, controllers)
        private val controllerRepo = ControllerRepository(controllerRegistry, controllers)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, events), controllerRegistry, ZonePlayerActivityBitSet(),
        )
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        private val objRegistry = ObjRegistry(updates)
        private val objRepo = ObjRepository(clock, objRegistry)
        private val playerRepo = PlayerRepository(PlayerRegistry(PlayerList(), collision, ZonePlayerActivityBitSet(), events))
        val script = Woodcutting(locs, controllerRepo, playerRepo, XpModifiers(emptySet()), InvisibleLevels(emptySet()), clock, objRepo)
        private val interactions = LocInteractions(BoundValidator(collision), events)
        private var completedProducts = 0

        init {
            storage.mapLocs[ZoneKey.from(tree.coords), LocZoneKey(ZoneGrid.from(tree.coords), tree.layer)] = tree.entity
            events.subscribeUnbound(SkillingActionCompleteEvent::class.java) { completedProducts++ }
        }

        fun item(symbol: String) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())))

        fun invoke(entry: String = "cut") {
            with(script) {
                if (entry == "attempt") access().attempt(tree, type) else access().cut(tree, type)
            }
        }

        fun timer(): Controller? = controllerRepo.findExact(tree.coords, "controller.woodcutting_tree_duration")

        fun exhaustTree(): Controller {
            script.treeSwingDespawnTick(tree, type)
            return checkNotNull(timer()).apply { treeActivelyCutTicks = durationStart }
        }

        fun tickTimer(timer: Controller) {
            clock.tick()
            with(script) { timer.treeDespawnTick() }
        }

        fun assertNoAwardOrContinuation(expectedTreeId: Int = tree.id) {
            assertEquals(0, player.statMap.getFineXP("stat.woodcutting"))
            assertEquals(0, completedProducts)
            assertEquals(0, objRegistry.count())
            assertEquals(expectedTreeId, locs.findAll(tree.coords).single().id)
            assertEquals(EntitySeq.ZERO, player.pendingSequence)
            assertNull(player.interaction)
        }

        fun assertOneLog() {
            assertEquals(1, player.inv.count { it?.id == type.treeLogs.id })
            assertEquals((type.treeXp * 10).toInt(), player.statMap.getFineXP("stat.woodcutting"))
            assertEquals(1, completedProducts)
            assertEquals(0, objRegistry.count())
        }

        private fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random },
            getEventBus = { events },
            getNpcList = { error("unused") },
            getPlayerList = { error("unused") },
            getCollision = { collision },
            getAreaChecker = { error("unused") },
            getAlignment = { error("unused") },
            getLocInteractions = { interactions },
            getNpcInteractions = { error("unused") },
            getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") },
            getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") },
            getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") },
            getHitModifier = { error("unused") },
        ))
    }

    private class ChopRandom : GameRandom {
        var integerRolls = 0
        var successRolls = 0
        override fun of(maxExclusive: Int): Int { integerRolls++; return maxExclusive - 1 }
        override fun of(minInclusive: Int, maxInclusive: Int): Int { integerRolls++; return maxInclusive }
        override fun randomDouble(): Double { successRolls++; return 0.0 }
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }
}
