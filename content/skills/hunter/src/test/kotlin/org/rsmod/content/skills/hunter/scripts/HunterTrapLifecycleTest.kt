@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.scripts

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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
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
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.ActiveTrap
import org.rsmod.content.skills.hunter.HunterTrapManager
import org.rsmod.content.skills.hunter.TrapState
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.HunterPreyTable
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.content.skills.hunter.configs.TrapFamily
import org.rsmod.content.skills.hunter.configs.TrapLocs
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HunterTrapLifecycleTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun loadCache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restoreServices() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `ordinary prey walks to the armed box before the trap closes`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        f.advance(3); f.manager.tick()
        assertEquals(TrapState.SET, trap.state)
        assertFalse(npc.hidden)
        assertTrue(npc.routeDestination.isNotEmpty())
        npc.coords = f.coords
        f.advance(1); f.manager.tick()
        assertEquals(TrapState.TRAPPING, trap.state)
        assertTrue(npc.hidden)
        f.advance(2); f.manager.tick()
        assertEquals(TrapState.FULL, trap.state)
    }

    @Test fun `unreachable prey is not pulled through a wall`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        f.collision.add(f.coords.x + 1, f.coords.z, 0, org.rsmod.routefinder.flag.CollisionFlag.LOC)
        f.advance(3); f.manager.tick()
        assertEquals(TrapState.SET, trap.state)
        assertTrue(npc.routeDestination.isEmpty())
        assertFalse(npc.hidden)
    }

    @Test fun `trap approach suppresses wandering then restores the previous mode on timeout`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        npc.mode = dev.openrune.types.NpcMode.Wander
        f.advance(3); f.manager.tick()
        assertEquals(dev.openrune.types.NpcMode.None, npc.mode)
        assertTrue(npc.routeDestination.isNotEmpty())
        f.advance(10); f.manager.tick()
        assertEquals(dev.openrune.types.NpcMode.Wander, npc.mode)
        assertTrue(npc.routeDestination.isEmpty())
        assertEquals(TrapState.SET, trap.state)
    }

    @Test fun `another NPC controller taking over an approach keeps its mode and route`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        npc.mode = dev.openrune.types.NpcMode.Wander
        f.advance(3); f.manager.tick()
        npc.mode = dev.openrune.types.NpcMode.PlayerEscape
        npc.walk(f.coords.translateX(6))
        f.manager.release(trap)
        assertEquals(dev.openrune.types.NpcMode.PlayerEscape, npc.mode)
        assertTrue(npc.routeDestination.isNotEmpty())
    }

    @Test fun `a scripted route replacing the approach keeps None mode and its destination`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        npc.mode = dev.openrune.types.NpcMode.Wander
        f.advance(3); f.manager.tick()
        val scripted = f.coords.translateX(6)
        npc.walk(scripted)
        f.advance(1); f.manager.tick()
        assertEquals(dev.openrune.types.NpcMode.None, npc.mode)
        assertEquals(scripted, npc.routeDestination.peekLast())
        assertEquals(TrapState.SET, trap.state)
    }

    @Test fun `dismantling releases a walking prey reservation without completing a stale catch`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val npc = f.prey(f.coords.translateX(2))
        f.advance(3); f.manager.tick()
        f.manager.release(trap)
        assertTrue(npc.routeDestination.isEmpty())
        npc.coords = f.coords
        f.advance(5); f.manager.tick()
        assertFalse(npc.hidden)
        assertNull(f.manager.trapAt(f.coords))
        val replacement = f.lay()
        f.manager.finishLay(f.player, replacement)
        f.advance(3); f.manager.tick()
        assertEquals(TrapState.TRAPPING, replacement.state)
    }

    @Test fun `bait and smoking cannot consume twice and salt is used only when arming`() {
        val f = Fixture()
        val trap = f.lay()
        f.player.inv[0] = f.item("obj.anti_odour_salt")
        f.manager.finishLay(f.player, trap)
        assertTrue(trap.smoked)
        assertEquals(0, f.player.inv.count("obj.anti_odour_salt"))
        f.player.inv[0] = f.item("obj.bowl_spicytomato")
        f.player.inv[1] = f.item("obj.bowl_spicytomato")
        assertTrue(org.rsmod.content.skills.hunter.HunterTrapEnhancements.apply(f.player, trap, "obj.bowl_spicytomato"))
        assertFalse(org.rsmod.content.skills.hunter.HunterTrapEnhancements.apply(f.player, trap, "obj.bowl_spicytomato"))
        assertEquals(1, f.player.inv.count("obj.bowl_spicytomato"))
        f.player.inv[2] = f.item("obj.torch_lit")
        assertFalse(org.rsmod.content.skills.hunter.HunterTrapEnhancements.apply(f.player, trap, "obj.torch_lit"))
        assertEquals(1, f.player.inv.count("obj.torch_lit"))
    }

    @Test fun `magic box tile reservations cover ordinary and net trap front tiles`() {
        val f = Fixture()
        assertTrue(f.manager.reserveExternalTrap(f.coords))
        assertFalse(f.manager.reserveExternalTrap(f.coords))
        assertThrows(IllegalStateException::class.java) { f.lay() }
        assertFalse(f.manager.canLayNetTrap(f.coords.translateX(-1), LocAngle.East, LocShape.CentrepieceStraight))
        f.manager.releaseExternalTrap(f.coords)
        assertNotNull(f.lay())
        assertFalse(f.manager.reserveExternalTrap(f.coords))
    }

    @ParameterizedTest
    @CsvSource("1,2", "19,2", "20,2", "39,2", "40,3", "60,4", "80,5", "99,5")
    fun `post sweep up trap limits include two at level one`(level: Int, cap: Int) {
        assertEquals(cap, HunterParams.maxConcurrentTraps(level, false, false))
        assertEquals(cap + 1, HunterParams.maxConcurrentTraps(level, true, true))
        assertEquals(cap, HunterParams.maxConcurrentTraps(level, true, false))
    }

    @Test fun `net failure restores the tree and returns components exactly once`() {
        val f = Fixture()
        val trap = f.lay(TrapFamily.NET_TRAP)
        f.manager.finishLay(f.player, trap)
        f.managerCall("enterFailing", trap)
        f.advance(HunterParams.TRANSITION_TICKS)
        f.manager.tick()
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(TrapLocs.saplingBare("swamp").asRSCM(), f.locs.findAll(trap.coords).single().id)
        assertEquals(1, f.dropped(HunterParams.ROPE))
        assertEquals(1, f.dropped(HunterParams.SMALL_FISHING_NET))
        f.advance(HunterParams.SET_LIFETIME_TICKS)
        f.locTimers()
        f.manager.tick()
        assertEquals(1, f.dropped(HunterParams.ROPE))
        assertEquals(1, f.dropped(HunterParams.SMALL_FISHING_NET))
    }

    @Test fun `expired box trap drops its tool and frees the trap slot`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        f.advance(HunterParams.SET_LIFETIME_TICKS)
        f.locTimers()
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(1, f.dropped(HunterParams.BOX_TRAP_OBJ))
        assertEquals(0, f.manager.ownedTrapCount(f.player))
        f.locTimers()
        assertEquals(1, f.dropped(HunterParams.BOX_TRAP_OBJ))
    }

    @Test fun `old check cannot collect a replacement catch on the same tile`() {
        val f = Fixture()
        val old = f.fullBox()
        val stale = f.task(old, TrapAction.CHECK)
        f.manager.release(old)
        val replacement = f.fullBox()
        val animation = EntitySeq(879, 0, 5)
        f.player.pendingSequence = animation
        f.complete(stale)
        assertEquals(animation, f.player.pendingSequence)
        assertSame(replacement, f.manager.trapAt(replacement.coords))
        assertEquals(0, f.player.statMap.getFineXP(HunterParams.HUNTER_STAT))
        assertEquals(0, f.player.inv.count { it != null })
    }

    @Test fun `old lay completion cannot arm a replacement or refund twice`() {
        val f = Fixture()
        val old = f.lay()
        f.manager.cancelLay(old)
        val replacement = f.lay()
        val animation = EntitySeq(879, 0, 5)
        f.player.pendingSequence = animation
        f.completeLay(old)
        assertEquals(animation, f.player.pendingSequence)
        assertSame(replacement, f.manager.trapAt(replacement.coords))
        assertEquals(TrapState.LAYING, replacement.state)
        assertEquals(1, f.dropped(HunterParams.BOX_TRAP_OBJ))
        assertEquals(0, f.player.inv.count { it != null })
    }

    @Test fun `checking twice pays the catch and tool only once`() {
        val f = Fixture()
        val trap = f.fullBox()
        val task = f.task(trap, TrapAction.CHECK)
        f.complete(task)
        f.complete(task)
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(1, f.player.inv.count(HunterParams.BOX_TRAP_OBJ))
        assertEquals(1, f.player.inv.count(trap.prey!!.loot.single()))
        assertEquals((trap.prey!!.xp * 10).toInt(), f.player.statMap.getFineXP(HunterParams.HUNTER_STAT))
    }

    @Test fun `full inventory check preserves all catch and equipment as ground drops`() {
        val f = Fixture()
        f.player.inv.objs.fill(f.item("obj.logs"))
        val trap = f.fullBox()
        f.complete(f.task(trap, TrapAction.CHECK))
        assertEquals(28, f.player.inv.count("obj.logs"))
        assertEquals(1, f.dropped(HunterParams.BOX_TRAP_OBJ))
        assertEquals(1, f.dropped(trap.prey!!.loot.single()))
        assertEquals((trap.prey!!.xp * 10).toInt(), f.player.statMap.getFineXP(HunterParams.HUNTER_STAT))
    }

    @Test fun `each bird snare gives bones meat and the rolled feather quantity`() {
        for (prey in HunterPreyTable.ALL.filter { it.family == TrapFamily.BIRD_SNARE }) {
            val f = Fixture()
            val trap = f.lay(TrapFamily.BIRD_SNARE)
            f.manager.finishLay(f.player, trap)
            trap.prey = prey
            f.managerCall("enterFull", trap)
            f.complete(f.task(trap, TrapAction.CHECK))
            assertEquals(1, f.player.inv.count("obj.bones"))
            assertEquals(1, f.player.inv.count("obj.spit_raw_bird_meat"))
            assertEquals(10, f.player.inv.count(prey.lootCounts.keys.single()))
            assertEquals(1, f.player.inv.count(HunterParams.BIRD_SNARE_OBJ))
        }
    }

    @Test fun `sabre toothed deadfall catch includes bones without refunding carved logs`() {
        val f = Fixture()
        val trap = f.lay(TrapFamily.DEADFALL)
        f.manager.finishLay(f.player, trap)
        trap.prey = HunterPreyTable.ALL.first { it.npc == "npc.huntingbeast_sabreteeth" }
        f.managerCall("enterFull", trap)
        f.complete(f.task(trap, TrapAction.CHECK))
        assertEquals(1, f.player.inv.count("obj.bones"))
        assertEquals(1, f.player.inv.count("obj.huntingbeast_sabreteeth"))
        assertEquals(0, f.player.inv.count("obj.logs"))
    }

    @Test fun `reset collects once and starts a new lay before arming`() {
        val f = Fixture()
        val old = f.fullBox()
        val task = f.task(old, TrapAction.RESET)
        f.complete(task)
        val replacement = checkNotNull(f.manager.trapAt(old.coords))
        assertNotSame(old, replacement)
        assertEquals(TrapState.LAYING, replacement.state)
        assertEquals(0, f.player.inv.count(HunterParams.BOX_TRAP_OBJ))
        assertEquals(1, f.player.inv.count(old.prey!!.loot.single()))
        f.complete(task)
        assertSame(replacement, f.manager.trapAt(old.coords))
        assertEquals(1, f.player.inv.count(old.prey!!.loot.single()))
        f.advance(HunterParams.LAY_TICKS)
        f.completeLay(replacement)
        assertEquals(TrapState.SET, replacement.state)
        assertEquals(f.clock.cycle, replacement.armedCycle)
    }

    @Test fun `stale state generation cannot dismantle a trap after it springs`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        val task = f.task(trap, TrapAction.DISMANTLE)
        f.managerCall("enterFailing", trap)
        f.advance(HunterParams.TRANSITION_TICKS)
        f.manager.tick()
        assertEquals(TrapState.FAILED, trap.state)
        f.complete(task)
        assertSame(trap, f.manager.trapAt(trap.coords))
        assertEquals(0, f.player.inv.count(HunterParams.BOX_TRAP_OBJ))
    }

    @ParameterizedTest
    @CsvSource("true,0", "false,1")
    fun `abandoned deadfall refunds only its actually consumed log`(preserved: Boolean, expected: Int) {
        val f = Fixture()
        val materials = if (preserved) emptyList() else listOf("obj.oak_logs")
        val trap = f.lay(TrapFamily.DEADFALL, materials)
        f.advance(HunterParams.LAY_TICKS + 2)
        f.manager.tick()
        f.completeLay(trap)
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(expected, f.dropped("obj.oak_logs"))
        assertEquals(0, f.dropped("obj.logs"))
    }

    @Test fun `logout returns armed equipment and releases all reservations`() {
        val f = Fixture()
        val trap = f.lay()
        f.manager.finishLay(f.player, trap)
        f.manager.onPlayerLogout(f.player)
        f.manager.onPlayerLogout(f.player)
        assertEquals(1, f.dropped(HunterParams.BOX_TRAP_OBJ))
        assertNull(f.manager.trapAt(trap.coords))
    }

    @Test fun `laying net reserves its front tile before the animation finishes`() {
        val f = Fixture()
        val trap = f.lay(TrapFamily.NET_TRAP)
        val front = trap.coords.translateX(1)
        assertSame(trap, f.manager.trapAt(front))
        assertThrows(IllegalStateException::class.java) {
            f.manager.beginLay(f.player, TrapFamily.BOX_TRAP, front, LocAngle.East, LocShape.CentrepieceStraight)
        }
        assertSame(trap, f.manager.trapAt(trap.coords))
        assertTrue(f.manager.finishLay(f.player, trap))
        assertSame(trap, f.manager.trapAt(front))
    }

    @Test fun `existing ground trap blocks a net reservation that would overlap it`() {
        val f = Fixture()
        val box = f.lay()
        val anchor = box.coords.translateX(-1)
        assertFalse(f.manager.canLayNetTrap(anchor, LocAngle.East, LocShape.CentrepieceStraight))
        assertEquals(TrapState.LAYING, box.state)
    }

    @Test fun `caught net retains its footprint and blocked reset returns tools without overwriting`() {
        val f = Fixture()
        val trap = f.lay(TrapFamily.NET_TRAP)
        f.manager.finishLay(f.player, trap)
        f.managerCall("clearNetTrapFrontLoc", trap)
        trap.prey = HunterPreyTable.netTrapPrey(NetTrapColour.SWAMP)
        f.managerCall("enterFull", trap)
        val front = trap.coords.translateX(1)
        assertSame(trap, f.manager.trapAt(front))
        f.locs.add(front, TrapLocs.BOX_SET, Int.MAX_VALUE, LocAngle.East, LocShape.CentrepieceStraight)
        f.complete(f.task(trap, TrapAction.RESET))
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(TrapLocs.BOX_SET.asRSCM(), f.locs.findAll(front).single().id)
        assertEquals(1, f.player.inv.count(HunterParams.ROPE))
        assertEquals(1, f.player.inv.count(HunterParams.SMALL_FISHING_NET))
        assertEquals(1, f.player.inv.count("obj.green_salamander"))
    }

    @ParameterizedTest
    @CsvSource("BOX_TRAP", "NET_TRAP")
    fun `world loc appearing during setup is preserved and consumed tools are refunded`(family: TrapFamily) {
        val f = Fixture()
        val trap = f.lay(family)
        val occupied = if (family == TrapFamily.NET_TRAP) trap.coords.translateX(1) else trap.coords
        f.locs.add(occupied, TrapLocs.BOX_FAILED, Int.MAX_VALUE, LocAngle.East, LocShape.CentrepieceStraight)
        f.completeLay(trap)
        assertNull(f.manager.trapAt(trap.coords))
        assertEquals(TrapLocs.BOX_FAILED.asRSCM(), f.locs.findAll(occupied).single().id)
        for (tool in HunterParams.consumedTools(family)) assertEquals(1, f.dropped(tool))
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val collision = CollisionFlagMap().apply {
            for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) allocateIfAbsent(x, z, 0)
        }
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
            override fun randomDouble() = 0.99
        }
        val player = Player().apply {
            uuid = 1L
            observerUUID = 1L
            coords = CoordGrid(3199, 3200)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel(HunterParams.HUNTER_STAT, 99)
            statMap.setCurrentLevel(HunterParams.HUNTER_STAT, 99)
        }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val normal = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
        )
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val objRepo = ObjRepository(clock, ObjRegistry(updates))
        val world = WorldRepository(updates)
        val manager = HunterTrapManager(locs, NpcRepository(clock, npcRegistry, npcs), objRepo,
            PlayerRepository(PlayerRegistry(players, collision, ZonePlayerActivityBitSet(), events)),
            world, players, clock, random, AreaChecker(regions, AreaIndex()), org.rsmod.api.route.RayCastValidator(collision))
        val script = HunterTrapScript(manager, locs, objRepo, XpModifiers(emptySet()), random)
        val coords = CoordGrid(3200, 3200)

        fun prey(at: CoordGrid) = org.rsmod.game.entity.Npc("npc.hunting_chinchompa", at).apply { hitpoints = 10; npcRepo.add(this, Int.MAX_VALUE) }

        fun item(symbol: String) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())))
        fun dropped(symbol: String) = listOf(coords, player.coords).distinct().flatMap { objRepo.findAll(it).toList() }.filter { it.type == symbol.asRSCM() }.sumOf { it.count }
        fun lay(family: TrapFamily = TrapFamily.BOX_TRAP, materials: List<String> = HunterParams.consumedTools(family)): ActiveTrap =
            manager.beginLay(player, family, coords, LocAngle.East, LocShape.CentrepieceStraight,
                if (family == TrapFamily.NET_TRAP) NetTrapColour.SWAMP else null, materials)
        fun fullBox(): ActiveTrap = lay().also {
            manager.finishLay(player, it)
            it.prey = HunterPreyTable.ALL.first { prey -> prey.npc == "npc.hunting_chinchompa" }
            managerCall("enterFull", it)
        }
        fun task(trap: ActiveTrap, action: TrapAction) = HunterTrapTask(trap, trap.generation, action)
        fun complete(task: HunterTrapTask) { callScript("completeAction", task) }
        fun completeLay(trap: ActiveTrap) { callScript("completeLay", HunterLayTask(trap)) }
        fun callScript(name: String, task: Any) {
            HunterTrapScript::class.java.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(script, access(), task)
        }
        fun managerCall(name: String, trap: ActiveTrap) {
            HunterTrapManager::class.java.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(manager, trap)
        }
        fun locTimers() {
            LocRepository::class.java.declaredMethods.single { it.name.startsWith("processDurations") }.apply { isAccessible = true }.invoke(locs)
        }
        fun advance(ticks: Int) {
            clock.cycle += ticks
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
        }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { collision }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") },
            getPlayerInteractions = { error("unused") }, getHeldInteractions = { error("unused") },
            getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") },
            getTeleportValidator = { error("unused") }, getWorldRepo = { world }, getHitModifier = { error("unused") },
        ))
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }
}
