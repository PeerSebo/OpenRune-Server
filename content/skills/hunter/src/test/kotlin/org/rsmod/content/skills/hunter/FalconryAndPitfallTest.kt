@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
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
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.FalconryPrey
import org.rsmod.content.skills.hunter.configs.PitfallPrey
import org.rsmod.content.skills.hunter.configs.PitfallSpot
import org.rsmod.content.skills.hunter.configs.QuetzalDestination
import org.rsmod.content.skills.hunter.configs.QuetzalWhistle
import org.rsmod.content.skills.hunter.scripts.QuetzalWhistleScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FalconryAndPitfallTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `renting charges five hundred and safely unequips all incompatible slots`() {
        val f = Fixture()
        f.player.inv[0] = f.item("obj.coins", 500)
        for (slot in listOf(3, 5, 9)) f.player.worn[slot] = f.item("obj.logs")
        assertTrue(f.falcons.rent(f.player))
        assertEquals(0, f.player.inv.count("obj.coins"))
        assertEquals(3, f.player.inv.count("obj.logs"))
        assertEquals(1, f.player.worn.count(FalconryService.FULL_GLOVE))
        assertNull(f.player.worn[5]); assertNull(f.player.worn[9])
    }

    @Test fun `equipment space failure consumes neither fee nor equipment`() {
        val f = Fixture()
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[0] = f.item("obj.coins", 600)
        f.player.worn[3] = f.item("obj.bronze_sword")
        assertFalse(f.falcons.rent(f.player))
        assertEquals(600, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.worn.count("obj.bronze_sword"))
    }

    @Test fun `investment survives session cleanup and eliminates future rental fees`() {
        val f = Fixture()
        f.player.inv[0] = f.item("obj.coins", 500_000)
        assertTrue(f.falcons.rent(f.player, invest = true))
        f.falcons.cleanup(f.player)
        assertEquals(true, f.player.falconryInvested)
        assertTrue(f.falcons.rent(f.player))
        assertFalse(f.falcons.rent(f.player, invest = true))
    }

    @Test fun `legacy investment gives free rental before any login handler runs`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf("hunter_falconry_invested" to true))
        assertTrue(f.falcons.rent(f.player))
        assertTrue(f.player.falconryInvested)
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test fun `walking and full inventory cannot cancel committed falcon flight or create ground loot`() {
        val f = Fixture()
        f.rent()
        f.player.inv.objs.fill(f.item("obj.logs"))
        val target = f.npc(FalconryPrey.SPOTTED.npc, CoordGrid(2375, 3600))
        assertTrue(f.falcons.launch(f.player, target))
        f.player.coords = CoordGrid(2374, 3601)
        f.advance(2)
        f.falcons.tick()
        val falcon = checkNotNull(f.falcons.rental(f.player)?.catch).falcon
        assertTrue(f.falcons.retrieve(f.player, falcon))
        assertEquals(1040, f.xp())
        assertEquals(28, f.player.inv.count("obj.logs"))
        assertEquals(0, f.objRegistry.count())
        assertEquals(listOf(FalconryPrey.SPOTTED.npc), f.catchEvents)
        assertFalse(f.falcons.retrieve(f.player, falcon))
        assertEquals(1040, f.xp())
    }

    @Test fun `falcon target reservation and successful catch belong to one hunter`() {
        val f = Fixture()
        f.rent()
        val other = f.player(2).apply { inv[0] = f.item("obj.coins", 500) }
        assertTrue(f.falcons.rent(other))
        val npc = f.npc(FalconryPrey.SPOTTED.npc, CoordGrid(2375, 3600))
        assertTrue(f.falcons.launch(f.player, npc))
        assertFalse(f.falcons.launch(other, npc))
        f.advance(2); f.falcons.tick()
        val caught = checkNotNull(f.falcons.rental(f.player)?.catch).falcon
        assertFalse(f.falcons.retrieve(other, caught))
        assertTrue(f.falcons.retrieve(f.player, caught))
        assertEquals(0, other.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `unretrieved falcon times out without XP and can be reclaimed for free`() {
        val f = Fixture()
        f.rent()
        assertTrue(f.falcons.launch(f.player, f.npc(FalconryPrey.SPOTTED.npc)))
        f.advance(2); f.falcons.tick()
        val falcon = checkNotNull(f.falcons.rental(f.player)?.catch).falcon
        f.advance(FalconryService.CATCH_TIMEOUT); f.falcons.tick()
        assertFalse(falcon.isSlotAssigned)
        assertEquals(0, f.xp())
        assertTrue(f.falcons.rent(f.player))
        assertEquals(1, f.player.worn.count(FalconryService.FULL_GLOVE))
    }

    @Test fun `logout releases flight reservations and stale callbacks cannot affect replacement rentals`() {
        val f = Fixture()
        f.rent()
        val npc = f.npc(FalconryPrey.SPOTTED.npc)
        f.falcons.launch(f.player, npc)
        val old = checkNotNull(f.falcons.rental(f.player))
        val flight = checkNotNull(old.flight)
        f.falcons.cleanup(f.player)
        f.rent()
        f.falcons.finishFlight(f.player, old, flight)
        assertFalse(npc.hidden)
        assertTrue(f.falcons.launch(f.player, npc))
    }

    @Test fun `retrieval after external equipment change preserves the replacement weapon`() {
        val f = Fixture()
        f.rent()
        f.falcons.launch(f.player, f.npc(FalconryPrey.SPOTTED.npc))
        f.advance(2); f.falcons.tick()
        val falcon = checkNotNull(f.falcons.rental(f.player)?.catch).falcon
        f.player.worn[3] = f.item("obj.bronze_sword")
        assertFalse(f.falcons.retrieve(f.player, falcon))
        assertEquals(1, f.player.worn.count("obj.bronze_sword"))
        assertEquals(0, f.xp())
    }

    @Test fun `failed hunt has a visible return flight and cannot be rented over`() {
        val f = Fixture()
        f.rent()
        f.random.roll = 255
        val target = f.npc(FalconryPrey.DASHING.npc)
        assertTrue(f.falcons.launch(f.player, target))
        f.advance(2); f.falcons.tick()
        assertNotNull(f.falcons.rental(f.player)?.returning)
        assertEquals(0, f.player.worn.count(FalconryService.FULL_GLOVE))
        assertFalse(f.falcons.rent(f.player))
        assertFalse(f.falcons.launch(f.player, target))
        f.advance(2); f.falcons.tick()
        assertEquals(1, f.player.worn.count(FalconryService.FULL_GLOVE))
        assertNull(f.falcons.rental(f.player)?.returning)
        assertEquals(0, f.xp())
    }

    @Test fun `cleanup during return leaves no equipment and cannot later recreate it`() {
        val f = Fixture()
        f.rent(); f.random.roll = 255
        f.falcons.launch(f.player, f.npc(FalconryPrey.DASHING.npc))
        f.advance(2); f.falcons.tick()
        f.falcons.cleanup(f.player)
        f.advance(20); f.falcons.tick()
        assertNull(f.falcons.rental(f.player))
        assertEquals(0, f.player.worn.count(FalconryService.FULL_GLOVE))
        assertEquals(0, f.player.inv.count(FalconryService.EMPTY_GLOVE))
    }

    @Test fun `each prey rewards its own loot and XP exactly once`() {
        for (prey in FalconryPrey.entries) {
            val f = Fixture()
            f.rent(); f.falcons.launch(f.player, f.npc(prey.npc))
            f.advance(2); f.falcons.tick()
            val caught = checkNotNull(f.falcons.rental(f.player)?.catch).falcon
            assertTrue(f.falcons.retrieve(f.player, caught))
            assertEquals((prey.xp * 10).toInt(), f.xp())
            for (loot in prey.loot) assertEquals(1, f.player.inv.count(loot))
            assertFalse(f.falcons.retrieve(f.player, caught))
        }
    }

    @Test fun `low Hunter cannot launch at higher tier prey`() {
        val f = Fixture()
        f.rent(); f.player.statMap.setCurrentLevel("stat.hunter", 43)
        assertFalse(f.falcons.launch(f.player, f.npc(FalconryPrey.DARK.npc)))
        assertFalse(f.falcons.launch(f.player, f.npc(FalconryPrey.DASHING.npc)))
        assertEquals(1, f.player.worn.count(FalconryService.FULL_GLOVE))
        assertNull(f.falcons.rental(f.player)?.flight)
    }

    @Test fun `equipment restrictions protect the rental and expire on return`() {
        val f = Fixture()
        val hook = FalconryEquipmentRestriction(f.falcons)
        val weapon = checkNotNull(ServerCacheManager.getItem("obj.bronze_sword".asRSCM()))
        val action = org.rsmod.api.player.hook.RestrictedAction.Equip(weapon)
        f.rent(); assertNotNull(hook.restriction(f.player, action))
        f.falcons.cleanup(f.player); assertNull(hook.restriction(f.player, action))
    }

    @Test fun `cleanup removes loaned gloves from every container while preserving other items`() {
        val f = Fixture()
        f.rent()
        val bank = f.player.invMap.getOrPut("inv.bank")
        bank[0] = f.item(FalconryService.EMPTY_GLOVE)
        bank[1] = f.item("obj.bronze_sword")
        f.falcons.cleanup(f.player)
        assertNull(bank[0]); assertNotNull(bank[1])
    }

    @Test fun `published falcon rates and required levels are preserved`() {
        assertEquals(0, FalconryPrey.SPOTTED.chance(42))
        assertEquals(149, FalconryPrey.SPOTTED.chance(43))
        assertEquals(256, FalconryPrey.SPOTTED.chance(80))
        assertEquals(146, FalconryPrey.DARK.chance(57))
        assertEquals(254, FalconryPrey.DARK.chance(99))
        assertEquals(143, FalconryPrey.DASHING.chance(69))
        assertEquals(206, FalconryPrey.DASHING.chance(99))
    }

    @Test fun `every native pit uses its own state bit and supports both full orientations`() {
        val f = Fixture()
        assertEquals(25, PitfallSpot.ALL.map { it.varbit }.distinct().size)
        for (spot in PitfallSpot.ALL) {
            val type = checkNotNull(ServerCacheManager.getObject(spot.loc.asRSCM()))
            assertEquals(2, type.width)
            assertEquals(2, type.length)
            assertNotNull(ServerCacheManager.getVarbit(spot.varbit.asRSCM()))
            f.pits.cleanup(f.player)
        }
    }

    @Test fun `pit setup reserves shared allowance without consuming logs until completion`() {
        val f = Fixture()
        f.player.statMap.setCurrentLevel("stat.hunter", 31)
        f.materials()
        val first = checkNotNull(f.pits.beginSet(f.player, f.pit(7)))
        val second = checkNotNull(f.pits.beginSet(f.player, f.pit(8)))
        assertEquals(2, f.manager.ownedTrapCount(f.player))
        assertNull(f.pits.beginSet(f.player, f.pit(9)))
        assertEquals(10, f.player.inv.count("obj.logs"))
        f.pits.cancelSet(first)
        assertFalse(f.pits.finishSet(first))
        assertEquals(1, f.manager.ownedTrapCount(f.player))
        assertTrue(f.pits.finishSet(second))
        assertEquals(9, f.player.inv.count("obj.logs"))
        assertFalse(f.pits.finishSet(second))
        assertEquals(9, f.player.inv.count("obj.logs"))
    }

    @Test fun `walking or losing level during setup cancels without consuming logs`() {
        val f = Fixture()
        f.materials()
        val trap = checkNotNull(f.pits.beginSet(f.player, f.pit(17)))
        f.player.coords = f.player.coords.translate(1, 0)
        assertFalse(f.pits.finishSet(trap))
        assertEquals(10, f.player.inv.count("obj.logs"))
        assertEquals(0, f.manager.ownedTrapCount(f.player))
        f.player.coords = trap.origin
        val replacement = checkNotNull(f.pits.beginSet(f.player, f.pit(17)))
        f.player.statMap.setCurrentLevel("stat.hunter", 71)
        assertFalse(f.pits.finishSet(replacement))
        assertEquals(0, f.player.vars[replacement.spot.varbit])
    }

    @Test fun `all five pitfall species award complete loot and XP only once on dismantle`() {
        for (prey in PitfallPrey.entries) {
            val f = Fixture()
            f.materials()
            val number = PitfallSpot.ALL.first { it.prey == prey }.number
            val loc = f.pit(number)
            val trap = checkNotNull(f.pits.beginSet(f.player, loc))
            assertTrue(f.pits.finishSet(trap))
            val npc = f.npc(prey.npc, f.player.coords.translate(0, -1))
            assertTrue(f.pits.tease(f.player, npc))
            val start = f.player.coords
            val end = checkNotNull(f.pits.crossing(f.player, loc))
            f.player.coords = end
            f.pits.crossed(f.player, loc, start, end)
            assertEquals(PitfallService.State.CATCHING, trap.state)
            assertEquals(0, f.xp())
            f.advance(2); f.pits.tick()
            assertTrue(f.pits.dismantle(f.player, loc), prey.name)
            assertFalse(f.pits.dismantle(f.player, loc))
            assertEquals((prey.xp * 10).toInt(), f.xp())
            assertEquals(1, f.player.inv.count("obj.big_bones"))
            assertEquals(1, f.player.inv.count(prey.meat))
            assertEquals(1, f.player.inv.count(prey.fur))
            prey.antler?.let { assertEquals(1, f.player.inv.count(it)) }
            assertEquals(listOf(prey.npc), f.catchEvents)
            assertEquals(0, f.manager.ownedTrapCount(f.player))
        }
    }

    @Test fun `failed big cat cannot be caught at the same pit twice consecutively`() {
        val f = Fixture()
        f.materials()
        f.random.roll = 99
        val loc = f.pit(7)
        val trap = checkNotNull(f.pits.beginSet(f.player, loc))
        f.pits.finishSet(trap)
        val npc = f.npc(PitfallPrey.LARUPIA.npc, f.player.coords.translate(0, -1))
        f.pits.tease(f.player, npc)
        val start = f.player.coords
        val end = checkNotNull(f.pits.crossing(f.player, loc))
        f.player.coords = end
        f.pits.crossed(f.player, loc, start, end)
        assertEquals(PitfallService.State.ARMED, trap.state)
        assertEquals(7, f.pits.chase(f.player)?.lastPit)
        f.random.roll = 0
        f.advance(2)
        npc.coords = start.translate(0, -1)
        f.pits.crossed(f.player, loc, start, end)
        assertEquals(PitfallService.State.ARMED, trap.state)
        assertFalse(npc.hidden)
    }

    @Test fun `blocked landing and remote or diagonal pit clicks cannot cross`() {
        val f = Fixture()
        f.materials()
        val loc = f.pit(17)
        f.pits.finishSet(checkNotNull(f.pits.beginSet(f.player, loc)))
        val end = checkNotNull(f.pits.crossing(f.player, loc))
        f.collision.add(end.x, end.z, end.level, CollisionFlag.LOC)
        assertNull(f.pits.crossing(f.player, loc))
        assertNull(PitfallService.oppositeSide(loc, loc.coords.translate(-1, -1)))
        assertNull(PitfallService.oppositeSide(loc, loc.coords.translate(20, 0)))
    }

    @Test fun `failed big cat leap cannot overlap a blocked tile outside the players landing tile`() {
        val f = Fixture()
        f.materials()
        f.random.roll = 99
        val loc = f.pit(7)
        f.pits.finishSet(checkNotNull(f.pits.beginSet(f.player, loc)))
        val npc = f.npc(PitfallPrey.LARUPIA.npc, f.player.coords.translate(0, -1))
        assertTrue(f.pits.tease(f.player, npc))
        val start = f.player.coords
        val npcStart = npc.coords
        val end = checkNotNull(f.pits.crossing(f.player, loc))
        f.collision.add(end.x + 1, end.z, end.level, CollisionFlag.LOC)
        assertNotNull(f.pits.crossing(f.player, loc))
        f.player.coords = end
        f.pits.crossed(f.player, loc, start, end)
        assertEquals(npcStart, npc.coords)
        assertEquals(PitfallService.State.ARMED, f.pits.trap(f.player, loc)?.state)
        assertEquals(0, f.xp())
    }

    @Test fun `pit state is personal and logout releases prey and all trap reservations`() {
        val f = Fixture()
        f.materials()
        val loc = f.pit(17)
        f.pits.finishSet(checkNotNull(f.pits.beginSet(f.player, loc)))
        val other = f.player(2)
        assertEquals(0, other.vars[PitfallSpot.ALL[16].varbit])
        val npc = f.npc(PitfallPrey.SUNLIGHT.npc, f.player.coords.translate(0, -1))
        assertTrue(f.pits.tease(f.player, npc))
        other.inv[0] = f.item(PitfallService.TEASING_STICK)
        assertFalse(f.pits.tease(other, npc))
        f.pits.cleanup(f.player)
        assertEquals(0, f.manager.ownedTrapCount(f.player))
        assertEquals(0, f.player.vars[PitfallSpot.ALL[16].varbit])
        assertTrue(f.pits.tease(other, npc))
    }

    @Test fun `whistle windup denied by teleport hook before or after waiting never spends a charge`() {
        for (denyBefore in listOf(true, false)) {
            val f = Fixture()
            f.player.inv[0] = InvObj(QuetzalWhistle.BASIC.obj, vars = 2)
            val start = f.player.coords
            f.teleportDenied = denyBefore
            val action = f.whistle()
            f.teleportDenied = true
            f.advance(4); action.advance()
            assertEquals(start, f.player.coords)
            assertEquals(2, f.player.inv[0]?.vars)
            assertEquals(0, f.player.vars["varbit.quetzal_last_destination"])
        }
    }

    @Test fun `cancelling a whistle windup keeps its charge and origin`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(QuetzalWhistle.BASIC.obj, vars = 2)
        val start = f.player.coords
        val action = f.whistle()
        assertTrue(action.isSuspended)
        action.cancel()
        f.advance(4); action.advance()
        assertEquals(start, f.player.coords)
        assertEquals(2, f.player.inv[0]?.vars)
    }

    @Test fun `successful whistle arrival spends one charge only after the windup`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(QuetzalWhistle.BASIC.obj, vars = 2)
        val start = f.player.coords
        val action = f.whistle()
        assertEquals(start, f.player.coords)
        assertEquals(2, f.player.inv[0]?.vars)
        f.advance(4); action.advance()
        assertEquals(QuetzalDestination.GUILD.coords, f.player.coords)
        assertEquals(1, f.player.inv[0]?.vars)
        assertEquals(QuetzalDestination.GUILD.nativeId, f.player.vars["varbit.quetzal_last_destination"])
        action.advance()
        assertEquals(1, f.player.inv[0]?.vars)
    }

    private class TestRandom : GameRandom {
        var roll = 0
        override fun of(maxExclusive: Int) = roll.coerceAtMost(maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive + roll.coerceAtMost(maxInclusive - minInclusive)
        override fun randomDouble() = roll / 100.0
    }
    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val catchEvents = mutableListOf<String>()
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val collision = CollisionFlagMap().apply { for (x in 2352..2408 step 8) for (z in 3560..3632 step 8) allocateIfAbsent(x, z, 0) }
        val random = TestRandom()
        val players = PlayerList()
        val player = player(1)
        val npcList = NpcList()
        val npcRegistry = NpcRegistry(npcList, collision, events)
        val npcs = NpcRepository(clock, npcRegistry, npcList)
        val normal = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val objRegistry = ObjRegistry(updates)
        val objects = ObjRepository(clock, objRegistry)
        val world = WorldRepository(updates)
        val ray = RayCastValidator(collision)
        val xpMods = XpModifiers(emptySet())
        val manager = HunterTrapManager(locs, npcs, objects, PlayerRepository(PlayerRegistry(players, collision, ZonePlayerActivityBitSet(), events)), world, players, clock, random, AreaChecker(regions, AreaIndex()), RayCastValidator(collision))
        val falcons = FalconryService(clock, npcs, world, ray, random, xpMods, events)
        val pits = PitfallService(manager, clock, npcs, objects, world, collision, ray, random, xpMods, StandardPlayerHitModifier(events), events)
        var teleportDenied = false
        init { events.subscribeUnbound(HunterCatchEvent::class.java) { catchEvents += prey } }

        fun player(slot: Int): Player = Player().apply {
            uuid = slot.toLong(); observerUUID = slot.toLong(); slotId = slot; assignUid()
            coords = CoordGrid(2374, 3600)
            currentMapClock = clock.cycle; processedMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hunter", 99); statMap.setCurrentLevel("stat.hunter", 99)
            statMap.setBaseLevel("stat.hitpoints", 99); statMap.setCurrentLevel("stat.hitpoints", 99)
            players[slot] = this
        }
        fun item(symbol: String, count: Int = 1) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())), count)
        fun npc(symbol: String, coords: CoordGrid = CoordGrid(2375, 3600)) = Npc(symbol, coords).apply { hitpoints = 10; npcs.add(this, Int.MAX_VALUE) }
        fun rent() { player.inv[0] = item("obj.coins", 500); check(falcons.rent(player)) }
        fun advance(ticks: Int) { clock.cycle += ticks; for (p in players) { p.currentMapClock = clock.cycle; p.processedMapClock = clock.cycle } }
        fun xp() = player.statMap.getFineXP("stat.hunter")
        fun whistle(): GameCoroutine {
            val destination = QuetzalDestination.GUILD.coords
            collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            val checker = AreaChecker(regions, AreaIndex())
            val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> if (teleportDenied) "Denied" else null }))
            val script = QuetzalWhistleScript(validator, checker)
            val selected = checkNotNull(QuetzalWhistleService.selection(player, 0))
            return player.launch {
                val access = ProtectedAccess(player, this, ProtectedAccessContext(
                    getRandom = { random }, getEventBus = { events }, getNpcList = { npcList }, getPlayerList = { players },
                    getCollision = { collision }, getAreaChecker = { checker }, getAlignment = { error("unused") },
                    getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
                    getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
                    getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { validator },
                    getWorldRepo = { world }, getHitModifier = { error("unused") },
                ))
                with(script) { access.travel(selected, QuetzalDestination.GUILD) }
            }
        }
        fun materials() { player.inv[0] = item("obj.logs", 10); player.inv[1] = item("obj.knife"); player.inv[2] = item(PitfallService.TEASING_STICK) }
        fun pit(number: Int): BoundLocInfo {
            val id = "loc.hunting_pitfall_$number".asRSCM()
            return BoundLocInfo(CoordGrid(2374, 3601), LocEntity(id, 10, 0), 2, 2, 2, 0)
        }
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }
}
