package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.accuracy.magic.NvPMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.NvPMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.NvPRangedAccuracy
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.*
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZulrahEncounterTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `all four rotations validate with the local BossDSL and reject invalid rotation lists`() {
        for (rotation in 1..4) {
            val spec = ZulrahSpec.rotating(listOf(rotation))
            assertTrue(SpecValidator.validate(spec).isEmpty())
            assertTrue("rotation_$rotation" in spec.phases)
            assertEquals(1, spec.phases.keys.count { it.startsWith("rotation_") })
        }
        assertTrue(SpecValidator.validate(ZulrahSpec.boss).isEmpty())
        assertTrue(SpecValidator.validate(ZulrahSpec.snakelings).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ZulrahSpec.rotating(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ZulrahSpec.rotating(listOf(1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { ZulrahSpec.rotating(listOf(5)) }
    }

    @Test fun `submerging blocks owner attacks until the next emergence`() {
        val f = Fixture()
        val guard = ZulrahAttackGuard(f.controller)
        assertEquals(NpcAttackValidateResult.Pass, guard.validate(f.player, f.boss))
        assertTrue(guard.validate(Player(), f.boss) is NpcAttackValidateResult.Deny)
        f.controller.dive(f.boss)
        assertTrue(guard.validate(f.player, f.boss) is NpcAttackValidateResult.Deny)
        f.controller.emerge(f.boss, ZulrahRoutineEvent(0, "emerge", "npc.snakeboss_boss_magic"))
        assertEquals(NpcAttackValidateResult.Pass, guard.validate(f.player, f.boss))
    }

    @Test fun `instance coordinates resolve only within a live owned template`() {
        val f = Fixture()
        assertEquals(f.player.coords, f.manager.resolveCoord(f.session, ZulrahIsland.arrival))
        assertEquals(f.boss.coords, f.manager.resolveCoord(f.session, ZulrahIsland.openingSpawn))
        assertNull(f.manager.resolveCoord(f.session, CoordGrid(3200, 3200)))
        f.manager.leave(f.player, f.session, f.clock.cycle)
        assertNull(f.manager.resolveCoord(f.session, ZulrahIsland.arrival))
    }

    @Test fun `stepping off the tail target before impact prevents the hit and stun`() {
        val f = Fixture()
        f.controller.tailWindup(f.boss, ZulrahSpec.tail)
        assertEquals(1, f.queues.size)
        f.player.coords = f.player.coords.translate(2, 0)
        repeat(ZulrahSpec.tail.impactDelay) { f.advance() }
        assertEquals(0, f.cloudHits)
        assertFalse(f.player.frozen)
    }

    @Test fun `cloud damage covers nine tiles on the same floor and is applied once per tick`() {
        val origin = CoordGrid(3200, 3200)
        for (x in 0..2) for (z in 0..2) {
            assertTrue(ZulrahEncounterController.cloudContains(origin, origin.translate(x, z)))
        }
        for (tile in listOf(origin.translate(-1, 0), origin.translate(3, 0),
            origin.translate(0, -1), origin.translate(0, 3), CoordGrid(3200, 3200, 1))) {
            assertFalse(ZulrahEncounterController.cloudContains(origin, tile))
        }
        val f = Fixture()
        val cloud = f.hazard("gas")
        f.controller.launchHazard(f.boss, cloud)
        f.controller.launchHazard(f.boss, cloud.copy(target = cloud.target.copy(x = cloud.target.x - 1)))
        f.advance()
        f.controller.tick(f.clock.cycle)
        f.controller.tick(f.clock.cycle)
        assertEquals(1, f.cloudHits)
        f.controller.tick(f.clock.cycle + 1)
        assertEquals(2, f.cloudHits)
    }

    @Test fun `leaving destroys owned snakelings and invalidates queued hazards`() {
        val f = Fixture()
        f.controller.launchHazard(f.boss, f.hazard("egg"))
        f.advance()
        val snake = f.npcs.single { it !== f.boss }
        assertFalse(snake.respawns)
        assertEquals(f.session.id, f.manager.instanceForNpc(snake))
        f.controller.launchHazard(f.boss, f.hazard("egg", delay = 2))
        f.manager.leave(f.player, f.session, f.clock.cycle)
        repeat(3) { f.advance() }
        assertFalse(f.boss.isSlotAssigned)
        assertFalse(snake.isSlotAssigned)
        assertTrue(f.npcs.none())
        assertNull(f.manager.sessionForId(f.session.id))
    }

    @Test fun `death rewards run once before NPC identity is released and use the owners shore tile`() {
        val f = Fixture()
        val originalUid = f.boss.uid
        f.boss.hitpoints = 0
        assertTrue(f.controller.beginDeath(f.boss))
        assertFalse(f.controller.beginDeath(f.boss))
        var drops = 0
        f.controller.finishDeath(f.boss) { tile ->
            assertTrue(f.boss.isSlotAssigned)
            assertEquals(originalUid, f.boss.uid)
            assertEquals(f.player.coords, tile)
            assertNotEquals(tile, f.boss.coords)
            drops++
        }
        f.controller.finishDeath(f.boss) { drops++ }
        assertEquals(1, drops)
        assertFalse(f.boss.isSlotAssigned)
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val flags = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, flags, events)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normal = LocRegistryNormal(updates, flags, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, flags, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val locRepo = LocRepository(clock, LocRegistry(storage, normal,
            LocRegistryRegion(updates, flags, storage, regions)), regions)
        val worldRepo = WorldRepository(updates)
        val queues = WorldQueueList()
        val deps = BossDeps(DefaultGameRandom(1), unused<AccuracyFormulae>(), worldRepo, npcRepo,
            players, clock, queues, flags, EncounterRegistry(clock), BossExtensionRegistry(), NoopPlayerHitModifier)
        val manager = InstanceManager(RegionRepository(regions), npcRepo, players, events,
            InstanceAreaResolver(), clock, flags)
        var cloudHits = 0
        val combat = ZulrahCombat(unused<NvPMagicAccuracy>(), unused<NvPRangedAccuracy>(),
            unused<NvPMeleeAccuracy>(), DefaultGameRandom(2), NoopPlayerHitModifier,
            InstantPlayerHitProcessor { cloudHits++ })
        val controller = ZulrahEncounterController(manager, npcRepo, players, locRepo, worldRepo,
            flags, AiPlayerInteractions(events, players), RayCastValidator(flags), combat, deps)

        @OptIn(InternalApi::class)
        val player = Player().apply {
            slotId = 1; uuid = 123L; assignUid()
            coords = ZulrahIsland.zulAndraTeleport
            currentMapClock = clock.cycle
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        val session: InstanceSession
        val boss: Npc

        init {
            players[1] = player
            for (chunk in ZulrahIsland.chunks) {
                val tile = chunk.source.toCoords()
                flags.allocateIfAbsent(tile.x, tile.z, tile.level)
            }
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(ZulrahEncounterScript(deps, unused<NpcDeath>(), controller)) { context.startup() }
            val result = manager.create(player, ZulrahIsland.KEY, ZulrahIsland.spec(player.coords),
                InstanceAccess.Private, clock.cycle) as InstanceManager.Result.Created
            session = result.session
            player.coords = result.enter
            manager.finalizeEntry(player, session, clock.cycle)
            boss = Npc(ZulrahIsland.RANGED_FORM,
                checkNotNull(manager.resolveCoord(session, ZulrahIsland.openingSpawn)))
            npcRepo.add(boss, Int.MAX_VALUE)
            boss.respawns = false
            check(manager.registerSessionNpc(player, boss))
            controller.register(player, session, boss)
        }

        fun hazard(kind: String, delay: Int = 1) = ZulrahRoutineEvent(
            tick = 0, kind = kind, symbol = "spotanim.snakeboss_double_orb",
            source = ZulrahPoint(2, 1),
            target = ZulrahPoint(player.coords.x - boss.coords.x, player.coords.z - boss.coords.z),
            impactDelay = delay, cloudLifetime = 30,
            spawn = ZulrahEncounterController.MELEE_SNAKE,
        )

        fun advance() {
            clock.cycle++
            val iterator = queues.iterator()
            while (iterator.hasNext()) {
                val queue = iterator.next()
                if (--queue.remainingCycles <= 0) {
                    iterator.remove()
                    queue.action()
                }
            }
            iterator.cleanUp()
        }
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
