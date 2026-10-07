package org.rsmod.content.bosses.giants

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.*
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.*
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InternalApi::class)
class GiantRoomsTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `first-key unlock is persistent and separate for both bosses`() {
        val original = Player()
        assertFalse(GiantBoss.OBOR.unlocked(original))
        GiantBoss.OBOR.unlock(original)
        val restored = Player().apply {
            vars.backing.putAll(original.vars.backing)
            attr.putAllFromPersistence(original.attr.toPersistentMap())
        }
        assertTrue(GiantBoss.OBOR.unlocked(restored))
        assertFalse(GiantBoss.BRYOPHYTA.unlocked(restored))
    }

    @Test fun `legacy giant unlocks migrate once without disturbing other saved attributes`() {
        val player = Player().apply {
            attr.putAllFromPersistence(mapOf(
                "giant_boss_obor_unlocked" to true,
                "giant_boss_bryophyta_unlocked" to false,
                "unrelated" to 42,
            ))
        }
        assertTrue(GiantBoss.OBOR.unlocked(player))
        assertFalse(GiantBoss.BRYOPHYTA.unlocked(player))
        assertEquals(mapOf("unrelated" to 42), player.attr.toPersistentMap())
        player.attr.putAllFromPersistence(mapOf("giant_boss_bryophyta_unlocked" to true))
        player.migrateGiantUnlocks()
        assertFalse(GiantBoss.BRYOPHYTA.unlocked(player))
        assertEquals(mapOf("unrelated" to 42), player.attr.toPersistentMap())
    }

    @Test fun `real instance owns boss and growthlings and restarts only after one chest attempt`() {
        val f = Fixture()
        val room = f.enter(GiantBoss.BRYOPHYTA)
        val first = checkNotNull(room.boss)
        assertFalse(first.respawns)
        assertSame(room, f.rooms.forNpc(first))
        f.rooms.summon(room)
        f.rooms.summon(room)
        assertEquals(3, room.growthlings.size)
        assertEquals(4, f.npcs.count())
        f.rooms.killed(first, Player())
        assertFalse(room.life.dead)
        f.rooms.killed(first, f.player)
        f.rooms.killed(first, f.player)
        assertTrue(room.life.dead)
        assertEquals(0, room.growthlings.size)
        assertEquals(1, f.npcs.count())
        f.clock.cycle += 100
        f.rooms.tick()
        assertSame(first, room.boss)
        assertTrue(room.life.attemptChest(f.clock.cycle))
        assertFalse(room.life.attemptChest(f.clock.cycle))
        f.clock.cycle += 14
        f.rooms.tick()
        assertSame(first, room.boss)
        f.clock.cycle++
        f.rooms.tick()
        assertNotSame(first, room.boss)
        assertFalse(first.isSlotAssigned)
        assertFalse(room.life.dead)
        assertEquals(1, f.npcs.count())
    }

    @Test fun `death has outside recovery destination and logout removes all one-life actors`() {
        val f = Fixture()
        val room = f.enter(GiantBoss.BRYOPHYTA)
        f.rooms.summon(room)
        val actors = f.npcs.toList()
        val redirect = GiantDeathRedirect(f.rooms)
        assertEquals(GiantBoss.BRYOPHYTA.exit, redirect.destination(f.player))
        f.manager.handleLogout(f.player, f.clock.cycle)
        f.rooms.tick()
        assertNull(f.rooms.forPlayer(f.player))
        assertNull(f.manager.sessionForId(room.id))
        assertEquals(0, f.npcs.count())
        assertTrue(actors.all { !it.isSlotAssigned })
        assertNull(redirect.destination(f.player))
    }

    private class Fixture {
        val clock = MapClock(100)
        private val events = EventBus()
        private val flags = CollisionFlagMap()
        private val players = PlayerList()
        val npcs = NpcList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(),
            LocRegistryNormal(updates, flags, storage), flags, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val manager = InstanceManager(RegionRepository(regions), npcRepo, players, events,
            InstanceAreaResolver(), clock, flags)
        private val deps = BossDeps(DefaultGameRandom(1), unused<AccuracyFormulae>(),
            WorldRepository(updates), npcRepo, players, clock, WorldQueueList(), flags,
            EncounterRegistry(clock), BossExtensionRegistry(), NoopPlayerHitModifier)
        val rooms = GiantRooms(manager, deps, AiPlayerInteractions(events, players))
        val player = Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            currentMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        init {
            players[1] = player
            for (boss in GiantBoss.entries) {
                for (x in boss.entry.mx * 64 until (boss.entry.mx + 1) * 64 step 8)
                    for (z in boss.entry.mz * 64 until (boss.entry.mz + 1) * 64 step 8)
                        flags.allocateIfAbsent(x, z, 0)
            }
        }
        fun enter(kind: GiantBoss): GiantRoom {
            player.coords = kind.exit
            val result = rooms.create(player, kind) as InstanceManager.Result.Created
            player.coords = result.enter
            manager.finalizeEntry(player, result.session, clock.cycle)
            return checkNotNull(rooms.forPlayer(player)).also { rooms.start(it) }
        }
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
