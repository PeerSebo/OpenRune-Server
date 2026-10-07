package org.rsmod.content.minigames.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.*
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.ui.UserInterface
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@Execution(ExecutionMode.SAME_THREAD)
class WintertodtGameTest {
    @Test fun `entry opens a per player native hud and starts the shared countdown`() {
        val f = Fixture()
        val p = f.add()
        f.game.tick()
        assertEquals(1000, p.vars[WintertodtState.Warmth])
        assertEquals(WinterPhase.Waiting, f.game.round.phase)
        assertTrue(p.ui.overlays.values.any { it == "interface.wint_status".asRSCM() })
        assertTrue(f.locs.findLoc(WintertodtData.rules.storm, "loc.wint_snow_storm_idle"))
        f.step(100)
        assertEquals(WinterPhase.Fighting, f.game.round.phase)
        assertTrue(f.game.canAct(p))
        assertTrue(f.locs.findLoc(WintertodtData.rules.storm, "loc.wint_snow_storm"))
    }

    @Test fun `food restores warmth only inside and only for qualifying foods`() {
        val f = Fixture()
        val p = f.add()
        f.game.tick()
        WintertodtState.set(p, WintertodtState.Warmth, 100)
        f.game.food(p, "obj.triangle_sandwich", 6)
        f.game.food(p, "obj.jug_wine", 11)
        f.game.food(p, "obj.cooked_meat", 3)
        assertEquals(100, p.vars[WintertodtState.Warmth])
        f.game.food(p, "obj.shark", 20)
        assertEquals(450, p.vars[WintertodtState.Warmth])
        f.game.food(p, "obj.shark", 20)
        f.game.food(p, "obj.shark", 20)
        assertEquals(1000, p.vars[WintertodtState.Warmth])
        p.coords = WintertodtData.rules.exit
        f.step()
        f.game.food(p, "obj.shark", 20)
        assertFalse(f.game.canAct(p))
    }

    @Test fun `leaving removes only that player's points and preserves earned rewards`() {
        val f = Fixture()
        val a = f.add()
        val b = f.add()
        f.game.tick()
        WintertodtState.set(a, WintertodtState.Points, 500)
        WintertodtState.set(b, WintertodtState.Points, 1000)
        WintertodtState.set(a, WintertodtState.RewardPool, 25)
        a.inv[0] = InvObj("obj.wint_bruma_root")
        a.coords = WintertodtData.rules.exit
        f.step()
        assertEquals(0, a.vars[WintertodtState.Points])
        assertEquals(1000, b.vars[WintertodtState.Points])
        assertEquals(25, a.vars[WintertodtState.RewardPool])
        assertFalse("obj.wint_bruma_root" in a.inv)
    }

    @Test fun `forced disconnect drops active points without recreating the session`() {
        val f = Fixture()
        val p = f.add()
        f.game.tick()
        f.step(100)
        WintertodtState.set(p, WintertodtState.Points, 750)
        p.forceDisconnect = true
        f.step()
        assertEquals(0, p.vars[WintertodtState.Points])
        assertFalse(f.game.canAct(p))
        assertEquals(WinterPhase.Waiting, f.game.round.phase)
    }

    @Test fun `victory awards once and a late joining participant must meet the threshold`() {
        val f = Fixture()
        val a = f.add()
        val b = f.add()
        f.game.tick()
        f.step(100)
        WintertodtState.set(a, WintertodtState.Points, 1000)
        WintertodtState.set(b, WintertodtState.Points, 499)
        f.game.round.camps.forEach { it.light() }
        while (f.game.round.phase == WinterPhase.Fighting) {
            f.game.round.advance()
            if (f.game.round.energy == 0) { f.step(); break }
        }
        assertEquals(WinterPhase.Waiting, f.game.round.phase)
        assertEquals(3, a.vars[WintertodtState.RewardPool])
        assertEquals(0, b.vars[WintertodtState.RewardPool])
        assertEquals(1, a.vars["varp.total_wintertodt_kills"])
        f.step(5)
        assertEquals(3, a.vars[WintertodtState.RewardPool])
        assertEquals(1, a.vars["varp.total_wintertodt_kills"])
    }

    @Test fun `actions from an old round cannot continue into another`() {
        val f = Fixture()
        val p = f.add()
        f.game.tick()
        f.step(100)
        val old = f.game.round.id
        assertTrue(f.game.canAct(p, old))
        f.game.round.finish(f.clock.cycle)
        f.game.round.begin(f.clock.cycle + 100)
        assertFalse(f.game.canAct(p, old))
        f.game.leave(p)
        assertFalse(f.game.canAct(p))
    }

    @Test fun `login clears orphan points but does not discard unclaimed rewards`() {
        val f = Fixture()
        val p = f.add()
        WintertodtState.set(p, WintertodtState.Points, 5000)
        WintertodtState.set(p, WintertodtState.RewardPool, 20)
        WintertodtState.set(p, WintertodtState.PendingCount, 4)
        f.game.login(p)
        assertEquals(0, p.vars[WintertodtState.Points])
        assertEquals(20, p.vars[WintertodtState.RewardPool])
        assertEquals(4, p.vars[WintertodtState.PendingCount])
    }
    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normal = LocRegistryNormal(updates, collision, storage)
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision,
            storage, npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locRegistry = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        val locs = LocRepository(clock, locRegistry, regions)
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
            override fun randomDouble() = 0.9
        }
        val game = WintertodtGame(collision, clock, players, npcs, locs, WorldRepository(updates), events, random)
        init {
            for (x in 1600..1664 step 8) for (z in 3960..4032 step 8)
                collision.allocateIfAbsent(x, z, 0)
            game.initialise()
        }
        fun add(): Player {
            val slot = players.nextFreeSlot()!!
            val p = Player().apply {
                uuid = slot.toLong()
                slotId = slot
                currentMapClock = clock.cycle
                coords = WintertodtData.rules.entry
                inv = Inventory.create("inv.inv")
                worn = Inventory.create("inv.worn")
                statMap.setBaseLevel("stat.firemaking", 99)
                statMap.setCurrentLevel("stat.firemaking", 99.toByte())
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99.toByte())
                ui.topLevel = UserInterface("interface.toplevel_osrs_stretch".asRSCM())
                client = Recorder()
            }
            players[slot] = p
            return p
        }
        fun step(ticks: Int = 1) { repeat(ticks) {
            clock.tick()
            players.forEach { it.currentMapClock = clock.cycle }
            game.tick()
        } }
    }
    private class Recorder : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() } }
}
