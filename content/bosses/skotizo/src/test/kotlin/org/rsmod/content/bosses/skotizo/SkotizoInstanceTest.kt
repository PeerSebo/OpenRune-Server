package org.rsmod.content.bosses.skotizo

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkotizoInstanceTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `real instance allocation starts five owned NPCs and leaving deletes them`() {
        val f = Fixture()
        val room = f.enter()
        val owned = f.manager.npcsForInstance(room.id).toList()
        assertEquals(5, owned.size)
        assertTrue(owned.none { it.respawns })
        assertEquals(NpcAttackValidateResult.BypassSingleWayPvnRestriction, SkotizoAttackHook(f.rooms).validate(f.player, room.boss))
        val stranger = Player().apply { coords = f.player.coords }
        assertTrue(SkotizoAttackHook(f.rooms).validate(stranger, room.boss) is NpcAttackValidateResult.Deny)
        assertEquals(SkotizoRules.Exit, SkotizoGraveHook(f.rooms).destination(f.player))
        val session = checkNotNull(f.manager.sessionForId(room.id))
        assertEquals(SkotizoRules.Exit, f.manager.leave(f.player, session, f.clock.cycle))
        assertNull(f.manager.sessionForId(room.id))
        assertTrue(owned.none { it.isSlotAssigned })
        assertNull(f.rooms.forPlayer(f.player))
    }

    @Test fun `an eye opening after a projectile was launched reduces damage at impact`() {
        val f = Fixture()
        val room = f.enter()
        val hit = f.launch(room.boss, 100)
        f.clock.cycle = room.nextAltar
        f.rooms.tick()
        assertEquals(1, room.eyes.count)
        f.land(room.boss, hit)
        assertEquals(375, room.boss.hitpoints)
    }

    @Test fun `Arclight closes an eye in one hit and queued follow-up hits cannot delete it`() {
        val f = Fixture()
        val room = f.enter()
        f.clock.cycle = room.nextAltar
        f.rooms.tick()
        val altar = room.altars.single { it.hitpoints == 100 }
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.arclight", 1)
        val first = f.launch(altar, 0, HitType.Melee)
        val stale = f.launch(altar, 200)
        f.player.worn[Wearpos.RightHand.slot] = null
        f.land(altar, first)
        assertEquals(0, room.eyes.count)
        assertEquals(1, altar.hitpoints)
        assertFalse("queue.death" in altar.queueList)
        f.land(altar, stale)
        assertEquals(1, altar.hitpoints)
        assertTrue(altar.isSlotAssigned)
        assertFalse("queue.death" in altar.queueList)
    }

    @Test fun `boss lethal impact clears helpers and death teardown releases the owner region`() {
        val f = Fixture()
        val room = f.enter()
        val helpers = room.altars.toList()
        f.land(room.boss, f.launch(room.boss, 9999))
        assertFalse(room.alive)
        assertTrue(helpers.none { it.isSlotAssigned })
        assertTrue("queue.death" in room.boss.queueList)
        assertEquals(SkotizoRules.Exit, SkotizoGraveHook(f.rooms).destination(f.player))
        f.manager.handleDeath(f.player, f.clock.cycle)
        assertNull(f.manager.sessionForId(room.id))
        assertFalse(room.boss.isSlotAssigned)
    }

    private class Fixture {
        val clock = MapClock(100)
        private val events = EventBus()
        private val flags = CollisionFlagMap()
        private val players = PlayerList()
        private val npcs = NpcList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, flags, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, flags, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        private val worldRepo = WorldRepository(updates)
        private val deps = BossDeps(DefaultGameRandom(1), unused<AccuracyFormulae>(), worldRepo, npcRepo, players,
            clock, WorldQueueList(), flags, EncounterRegistry(clock), BossExtensionRegistry(), NoopPlayerHitModifier)
        val manager = InstanceManager(RegionRepository(regions), npcRepo, players, events, InstanceAreaResolver(), clock, flags)
        val rooms = SkotizoRooms(manager, deps, AiPlayerInteractions(events, players))

        @OptIn(InternalApi::class)
        val player = Player().apply {
            slotId = 1; uuid = 123L; assignUid()
            coords = SkotizoRules.Exit
            currentMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        init {
            players[1] = player
            for (level in 0..3) for (x in 2240 until 2304 step 8) for (z in 5632 until 5696 step 8) flags.allocateIfAbsent(x, z, level)
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(Skotizo(deps, rooms, RayCastValidator(flags), unused<NpcDeath>())) { context.startup() }
            with(SkotizoEntrance(rooms, clock)) { context.startup() }
        }
        fun enter(): SkotizoRoom {
            val result = rooms.create(player) as InstanceManager.Result.Created
            player.coords = result.enter
            manager.finalizeEntry(player, result.session, clock.cycle)
            return checkNotNull(rooms.forPlayer(player)).also(rooms::start)
        }
        fun launch(npc: Npc, damage: Int, style: HitType = HitType.Ranged): Hit = npc.queueHit(player, 5, style, damage, NpcHitModifier {})
        fun land(npc: Npc, hit: Hit) {
            val processor = StandardNpcHitProcessor(players, events, emptySet(), worldRepo)
            val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext({ DefaultGameRandom(1) }, { NpcHitModifier {} }, { processor }))
            with(processor) { access.process(hit) }
        }
    }
    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
