package org.rsmod.content.minigames.fishingtrawler

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.math.floor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohRegionBuilder
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.minigames.fishingtrawler.scripts.TrawlerBailScript
import org.rsmod.content.minigames.fishingtrawler.scripts.TrawlerHullScript
import org.rsmod.content.minigames.fishingtrawler.scripts.TrawlerNetScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TrawlerRewardsTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach
    fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach
    fun restoreServices() {
        restore.asReversed().forEach { it() }
        restore.clear()
    }

    @Test
    fun `packed catalogues preserve every original fish level XP and outfit position`() {
        assertEquals(frozenCatch, TrawlerCatchTable.CASCADE)
        assertEquals(frozenOutfit, TrawlerRewardData.anglerPieces)
        assertEquals("obj.mantaray", TrawlerCatchTable.CASCADE.first().item)
        assertEquals(5, TrawlerCatchTable.JUNK_XP_TENTHS)
        assertEquals(150, TrawlerCatchTable.SUCCESS_LOW)
        assertEquals(200, TrawlerCatchTable.SUCCESS_HIGH)
        assertEquals(99, TrawlerCatchTable.MAX_LEVEL)
    }

    @Test
    fun `bailing removes water after its delay and emptying awards contribution once`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.phase = TrawlerPhase.SAILING
        voyage.waterLevel = 3
        f.player.inv[0] = InvObj(TrawlerConstants.BUCKET_EMPTY)
        f.bucket()
        assertEquals(3, voyage.waterLevel)
        assertEquals(1, f.player.inv.count(TrawlerConstants.BUCKET_EMPTY))
        f.advanceAction()
        assertEquals(2, voyage.waterLevel)
        assertEquals(1, f.player.inv.count(TrawlerConstants.BUCKET_FULL))
        assertEquals(0, voyage.contribution[1L] ?: 0)
        f.bucket()
        assertEquals(0, voyage.contribution[1L] ?: 0)
        f.advanceAction()
        assertEquals(TrawlerConstants.POINTS_BAIL, voyage.contribution[1L])
        assertEquals(1, f.player.inv.count(TrawlerConstants.BUCKET_EMPTY))
        f.advanceAction()
        assertEquals(TrawlerConstants.POINTS_BAIL, voyage.contribution[1L])
    }

    @Test
    fun `bailing cannot fill a bucket on a dry ship or outside sailing`() {
        for ((phase, water) in listOf(TrawlerPhase.LOBBY to 3, TrawlerPhase.SAILING to 0,
            TrawlerPhase.DOCKED to 3, TrawlerPhase.SINKING to 3)) {
            val f = Fixture()
            val voyage = f.join()
            voyage.phase = phase
            voyage.waterLevel = water
            f.player.inv[0] = InvObj(TrawlerConstants.BUCKET_EMPTY)
            f.bucket()
            assertFalse(f.player.activeCoroutine?.isSuspended == true)
            assertEquals(1, f.player.inv.count(TrawlerConstants.BUCKET_EMPTY))
            assertEquals(water, voyage.waterLevel)
        }
    }

    @Test
    fun `delayed bailing aborts if the voyage changes water disappears or the bucket moves`() {
        for (change in listOf("phase", "water", "bucket", "leave")) {
            val f = Fixture()
            val voyage = f.join()
            voyage.phase = TrawlerPhase.SAILING
            voyage.waterLevel = 3
            f.player.inv[0] = InvObj(TrawlerConstants.BUCKET_EMPTY)
            f.bucket()
            when (change) {
                "phase" -> voyage.phase = TrawlerPhase.DOCKED
                "water" -> voyage.waterLevel = 0
                "bucket" -> f.player.inv[0] = InvObj("obj.bronze_axe")
                "leave" -> f.player.coords = f.instances.leave(f.player, voyage.session, f.clock.cycle)
            }
            f.advanceAction()
            assertEquals(0, f.player.inv.count(TrawlerConstants.BUCKET_FULL))
            assertEquals(if (change == "water") 0 else 3, voyage.waterLevel)
            assertEquals(0, voyage.contribution[1L] ?: 0)
        }
    }

    @Test
    fun `emptying grants nothing after docking or when the full bucket is removed`() {
        for (dock in listOf(true, false)) {
            val f = Fixture()
            val voyage = f.join()
            voyage.phase = TrawlerPhase.SAILING
            f.player.inv[0] = InvObj(TrawlerConstants.BUCKET_FULL)
            f.bucket()
            if (dock) voyage.phase = TrawlerPhase.DOCKED else f.player.inv[0] = null
            f.advanceAction()
            assertEquals(0, f.player.inv.count(TrawlerConstants.BUCKET_EMPTY))
            assertEquals(0, voyage.contribution[1L] ?: 0)
        }
    }

    @Test
    fun `hull repair faces the leak and consumes paste only after the animation delay`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.phase = TrawlerPhase.SAILING
        voyage.leakState[0] = TrawlerConstants.HULL_STATE_LEAKING
        f.player.inv[0] = InvObj(TrawlerConstants.SWAMP_PASTE_ITEM)
        f.hull()
        assertEquals(1, f.player.inv.count(TrawlerConstants.SWAMP_PASTE_ITEM))
        assertEquals(f.player.coords.translateZ(1), f.player.pendingFaceSquare)
        f.advanceAction()
        assertEquals(TrawlerConstants.HULL_STATE_LEAKING, voyage.leakState[0])
        f.advanceAction()
        assertEquals(TrawlerConstants.HULL_STATE_PATCHED, voyage.leakState[0])
        assertEquals(0, f.player.inv.count(TrawlerConstants.SWAMP_PASTE_ITEM))
        assertEquals(TrawlerConstants.POINTS_LEAK_FILL, voyage.contribution[1L])
        assertEquals(0, f.player.statMap.getFineXP("stat.construction"))
    }

    @Test
    fun `a leak repaired during another repair attempt never consumes paste or awards points twice`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.phase = TrawlerPhase.SAILING
        voyage.leakState[0] = TrawlerConstants.HULL_STATE_LEAKING
        f.player.inv[0] = InvObj(TrawlerConstants.SWAMP_PASTE_ITEM)
        f.hull()
        f.manager.patchLeak(voyage, 0, f.clock.cycle)
        repeat(2) { f.advanceAction() }
        assertEquals(1, f.player.inv.count(TrawlerConstants.SWAMP_PASTE_ITEM))
        assertEquals(0, voyage.contribution[1L] ?: 0)
        f.hull()
        assertFalse(f.player.activeCoroutine?.isSuspended == true)
        assertEquals(1, f.player.inv.count(TrawlerConstants.SWAMP_PASTE_ITEM))
    }

    @Test
    fun `fish selection preserves each eligible cascade entry and exact success boundary`() {
        val f = Fixture()
        for (level in 1..99) {
            f.level(level)
            val eligible = frozenCatch.filter { level >= it.reqLevel }
            val rate = (1.0 + floor(150 * (99 - level) / 98.0 + 200 * (level - 1) / 98.0 + 0.5)) / 256.0
            for ((index, fish) in eligible.withIndex()) {
                repeat(index) { f.random.expectDouble(rate) }
                f.random.expectDouble(Math.nextDown(rate))
                assertEquals(fish, f.rollFish(), "level $level cascade index $index")
                f.random.assertConsumed()
            }
            repeat(eligible.size) { f.random.expectDouble(rate) }
            assertNull(f.rollFish(), "level $level equal-boundary draws must fail")
            f.random.assertConsumed()
        }
    }

    @Test
    fun `fish selection and claim eligibility use base Fishing rather than current level`() {
        val f = Fixture()
        f.level(15, 99)
        f.random.expectDouble(0.0)
        assertEquals("obj.raw_anchovies", f.rollFish()?.item)
        f.level(99, 1)
        f.random.expectDouble(0.0)
        assertEquals("obj.mantaray", f.rollFish()?.item)
        f.random.assertConsumed()

        val voyage = f.join()
        voyage.pendingFishReward[1L] = 1
        voyage.contribution[1L] = 50
        f.level(14, 99)
        f.collect()
        assertEquals(1, voyage.pendingFishReward[1L])
        assertEquals(0, f.xp())
        f.random.assertConsumed()
    }

    @Test
    fun `docking keeps catch rounding full meter bonus and reward availability tick`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.actionsPerformed[1L] = 7
        voyage.contribution[1L] = 255
        f.tick(199)
        assertEquals(TrawlerPhase.LOBBY, voyage.phase)
        assertTrue(voyage.pendingFishReward.isEmpty())
        f.tick(200)
        assertEquals(TrawlerPhase.SAILING, voyage.phase)
        voyage.nextLeakOpenTick = Int.MAX_VALUE
        voyage.nextTentacleAttackTick = Int.MAX_VALUE
        f.tick(699)
        assertTrue(voyage.pendingFishReward.isEmpty())
        assertEquals(0, f.xp())
        f.tick(700)
        assertEquals(TrawlerPhase.DOCKED, voyage.phase)
        assertEquals(10, voyage.totalCatch)
        assertEquals(15, voyage.pendingFishReward[1L])
        assertEquals(0, f.xp())
        assertEquals(0, f.player.inv.sumOf { it?.count ?: 0 })
        f.random.assertConsumed()
    }

    @Test
    fun `claim preserves draw order fish XP junk XP and one-time pending consumption`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.pendingFishReward[1L] = 3
        voyage.contribution[1L] = 50
        f.random.expectDouble(0.0)
        repeat(10) { f.random.expectDouble(0.99) }
        repeat(9) { f.random.expectDouble(0.99) }
        f.random.expectDouble(0.0)
        f.random.expectInt(12, 1)
        f.random.expectInt(5000, 1)
        f.collect()
        assertEquals(0, voyage.pendingFishReward[1L])
        assertEquals(1, f.player.inv.count("obj.mantaray"))
        assertEquals(1, f.player.inv.count("obj.raw_shrimp"))
        assertEquals(2, f.player.inv.sumOf { it?.count ?: 0 })
        assertEquals(702, f.xp())
        f.random.assertConsumed()
        f.collect()
        assertEquals(702, f.xp())
        f.random.assertConsumed()
    }

    @Test
    fun `shared catch divides before applying each full contribution bonus`() {
        val f = Fixture()
        val voyage = f.join()
        val other = Player().apply {
            uuid = 2L
            slotId = 2
            displayName = "Second trawler test"
            coords = CoordGrid(2671, 3172)
        }
        f.players[2] = other
        assertSame(voyage, f.join(other))
        voyage.actionsPerformed[1L] = 7
        voyage.actionsPerformed[2L] = 3
        voyage.contribution[1L] = 255
        voyage.contribution[2L] = 50
        f.tick(200)
        voyage.nextLeakOpenTick = Int.MAX_VALUE
        voyage.nextTentacleAttackTick = Int.MAX_VALUE
        f.tick(700)
        assertEquals(11, voyage.totalCatch)
        assertEquals(mapOf(1L to 7, 2L to 5), voyage.pendingFishReward)
        f.random.assertConsumed()
    }

    @Test
    fun `insufficient contribution keeps the catch without drawing any rewards`() {
        val f = Fixture()
        val voyage = f.join()
        voyage.pendingFishReward[1L] = 2
        voyage.contribution[1L] = 49
        f.collect()
        assertEquals(2, voyage.pendingFishReward[1L])
        assertEquals(0, f.xp())
        f.random.assertConsumed()
    }

    @Test
    fun `outfit selection preserves inventory and worn exclusions and remaining piece order`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(frozenOutfit[0])
        f.player.worn[4] = InvObj(frozenOutfit[1])
        f.player.invMap.getValue("inv.bank")[0] = InvObj(frozenOutfit[2])
        f.random.expectInt(12, 0)
        f.random.expectInt(2, 0)
        f.rollAngler()
        assertEquals(1, f.player.inv.count(frozenOutfit[2]))
        f.random.expectInt(12, 0)
        f.random.expectInt(1, 0)
        f.rollAngler()
        assertEquals(1, f.player.inv.count(frozenOutfit[3]))
        f.random.expectInt(12, 0)
        f.rollAngler()
        f.random.assertConsumed()
    }

    @Test
    fun `full inventory catch still drops once and grants its original XP`() {
        val f = Fixture()
        val voyage = f.join()
        repeat(28) { f.player.inv[it] = InvObj("obj.bronze_axe") }
        voyage.pendingFishReward[1L] = 1
        voyage.contribution[1L] = 255
        f.random.expectDouble(0.0)
        f.random.expectInt(12, 1)
        f.random.expectInt(2500, 1)
        f.collect()
        assertEquals(1, f.objRegistry.count())
        assertEquals(0, f.player.inv.count("obj.mantaray"))
        assertEquals(690, f.xp())
        assertEquals(0, voyage.pendingFishReward[1L])
        f.random.assertConsumed()
    }

    @Test
    fun `heron chance interpolation retains exact bounds and successful pet grant`() {
        val f = Fixture()
        for ((contribution, bound) in listOf(0 to 5000, 50 to 5000, 150 to 3781, 255 to 2500, 300 to 2500)) {
            f.random.expectInt(bound, 1)
            f.rollHeron(contribution)
        }
        f.random.expectInt(2500, 0)
        f.rollHeron(255)
        assertEquals(1, f.player.inv.count("obj.skillpetfish"))
        f.random.assertConsumed()
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    private class Draws : GameRandom {
        private val values = ArrayDeque<Any>()
        fun expectDouble(value: Double) { values.addLast(value) }
        fun expectInt(bound: Int, value: Int) { values.addLast(bound to value) }
        fun assertConsumed() { assertTrue(values.isEmpty(), "Not all expected random draws were made.") }
        override fun randomDouble(): Double {
            assertFalse(values.isEmpty(), "Unexpected floating-point draw.")
            return values.removeFirst() as Double
        }
        override fun of(maxExclusive: Int): Int {
            assertFalse(values.isEmpty(), "Unexpected integer draw.")
            val expected = values.removeFirst() as Pair<*, *>
            assertEquals(expected.first, maxExclusive, "Random draw bound or order changed.")
            return expected.second as Int
        }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected ranged draw.")
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normalLocs = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normalLocs, collision,
            storage, npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val instances = InstanceManager(RegionRepository(regions), npcRepo, players, events,
            InstanceAreaResolver(), clock, collision)
        val random = Draws()
        val manager = TrawlerManager(instances, npcRepo, players, random)
        val objRegistry = ObjRegistry(updates)
        val script = TrawlerNetScript(manager, ObjRepository(clock, objRegistry))
        val player = Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            displayName = "Trawler test"
            currentMapClock = 100
            coords = CoordGrid(2671, 3172)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
            statMap.setBaseLevel("stat.fishing", 99)
            statMap.setCurrentLevel("stat.fishing", 99)
        }

        init {
            players[1] = player
            with(TrawlerBailScript(manager)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun bucket() {
            val obj = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context()) {
                assertTrue(events.publish(this, HeldObjEvents.Op1(0, obj, type, inv)))
            })
        }

        fun hull() {
            val locRepo = LocRepository(clock, LocRegistry(storage, normalLocs,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            val data = PohDataStore()
            val poh = PohManager(RegionRepository(regions), locRepo, data,
                PohRegionBuilder(data), collision, events)
            val hullEvents = EventBus()
            with(TrawlerHullScript(manager, poh)) {
                ScriptContext(hullEvents, CheatCommandMap(), EngineQueueCache()).startup()
            }
            val base = checkNotNull(ServerCacheManager.getObject(TrawlerConstants.BASE_HULL_LOC.asRSCM()))
            val visible = checkNotNull(ServerCacheManager.getObject(TrawlerConstants.HULL_LEAK_TRANSFORM_LOC.asRSCM()))
            val coords = player.coords.translateZ(1)
            val shape = LocShape.CentrepieceStraight.id
            val angle = LocAngle.West.id
            val baseLoc = BoundLocInfo(LocInfo(2, coords, LocEntity(base.id, shape, angle)), base)
            val visibleLoc = BoundLocInfo(LocInfo(2, coords, LocEntity(visible.id, shape, angle)), visible)
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context()) {
                assertTrue(hullEvents.publish(this, LocEvents.Op1(baseLoc, visibleLoc, visible)))
            })
        }

        fun advanceAction() {
            clock.cycle++
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            player.advanceActiveCoroutine()
        }

        fun level(base: Int, current: Int = base) {
            player.statMap.setBaseLevel("stat.fishing", base.toByte())
            player.statMap.setCurrentLevel("stat.fishing", current.toByte())
        }

        fun join(joiningPlayer: Player = player): TrawlerVoyage {
            val result = manager.joinOrCreateVoyage(joiningPlayer, 100)
            assertTrue(result is InstanceManager.Result.Joined, result.toString())
            result as InstanceManager.Result.Joined
            joiningPlayer.coords = result.enter
            instances.finalizeEntry(joiningPlayer, result.session, 100)
            return checkNotNull(manager.voyageForPlayer(joiningPlayer))
        }

        fun tick(tick: Int) {
            clock.cycle = tick
            player.currentMapClock = tick
            manager.tick(tick)
        }

        fun xp() = player.statMap.getFineXP("stat.fishing")
        fun rollFish() = invoke("rollFish", access()) as TrawlerCatchTable.Fish?
        fun rollAngler() = invoke("rollAnglerPiece", access())
        fun rollHeron(contribution: Int) = invoke("rollHeronPet", access(), contribution)
        fun collect() { assertEquals(Unit, invoke("collectReward", access(), null)) }

        private fun invoke(name: String, vararg args: Any?) = script.javaClass.declaredMethods
            .single { it.name == name }.apply { isAccessible = true }.invoke(script, *args)

        private fun access() = ProtectedAccess(player, GameCoroutine(), context())

        private fun context() = ProtectedAccessContext(
            getRandom = { random }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { collision }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        )
    }

    companion object {
        private fun fixture(name: String) = checkNotNull(TrawlerRewardsTest::class.java.getResourceAsStream(name))
            .bufferedReader().use { it.readLines() }
        private val frozenCatch = fixture("/trawler-catch-before.tsv").map { line ->
            val values = line.split('\t')
            TrawlerCatchTable.Fish(values[0], values[1].toInt(), values[2].toInt())
        }
        private val frozenOutfit = fixture("/trawler-outfit-before.tsv")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
