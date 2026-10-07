package org.rsmod.content.bosses.sarachnis

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
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.feet
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InternalApi::class)
class SarachnisCombatTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `live attack callback binds on fifth rotation and sprints before resuming normal movement`() {
        val f = Fixture()
        repeat(4) { f.script.attack(f.boss, f.player); assertFalse(f.player.isFrozen) }
        f.script.attack(f.boss, f.player)
        assertTrue(f.player.isFrozen)
        assertEquals(MoveSpeed.Run, f.boss.moveSpeed)
        assertTrue(f.boss.routeDestination.isNotEmpty())
        f.boss.routeDestination.clear()
        f.boss.processArrivalAction()
        assertEquals(f.boss.defaultMoveSpeed, f.boss.moveSpeed)
    }

    @Test fun `Aranea boots suppress webs but do not prevent the boss retreat`() {
        val f = Fixture()
        f.player.feet = InvObj("obj.aranea_boots")
        repeat(5) { f.script.attack(f.boss, f.player) }
        assertFalse(f.player.isFrozen)
        assertEquals(MoveSpeed.Run, f.boss.moveSpeed)
    }

    @Test fun `real threshold tick creates distinct one-life pairs and leaving removes all spawns`() {
        val f = Fixture()
        f.script.attack(f.boss, f.player)
        f.player.coords = Sarachnis.Spawn.translate(-5, 0)
        f.boss.hitpoints = 130
        f.script.tick()
        val young = f.npcs.filter { it !== f.boss }
        assertEquals(4, young.size)
        assertTrue(young.all { !it.respawns })
        assertEquals(4, young.map { it.coords }.toSet().size)
        for (spawn in young) {
            assertFalse(Sarachnis.overlaps(spawn.coords, spawn.size, f.player.coords, 1))
            assertFalse(Sarachnis.overlaps(spawn.coords, spawn.size, f.boss.coords, f.boss.size))
            assertTrue(young.filter { it !== spawn }.none {
                Sarachnis.overlaps(spawn.coords, spawn.size, it.coords, it.size)
            })
        }
        f.script.tick()
        assertEquals(5, f.npcs.count())
        PathingEntityCommon.telejump(f.boss, f.flags, Sarachnis.Spawn.translate(2, 2))
        f.player.coords = SarachnisAccess.Outside
        f.script.tick()
        assertEquals(1, f.npcs.count())
        assertEquals(400, f.boss.hitpoints)
        assertEquals(Sarachnis.Spawn, f.boss.coords)
        assertTrue(young.all { !it.isSlotAssigned })
    }

    @Test fun `native boss hide event removes surviving young before respawn`() {
        val f = Fixture()
        f.boss.hitpoints = 264
        f.script.tick()
        assertEquals(3, f.npcs.count())
        f.events.publish(NpcStateEvents.Hide(f.boss))
        assertEquals(1, f.npcs.count())
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val flags = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        private val repo = NpcRepository(clock, NpcRegistry(npcs, flags, events), npcs)
        private val instances = InstanceManager(unused<RegionRepository>(), repo, players, events,
            InstanceAreaResolver(), clock, flags)
        private val deps = BossDeps(DefaultGameRandom(1), unused<AccuracyFormulae>(),
            WorldRepository(ZoneUpdateMap()), repo, players, clock, WorldQueueList(), flags,
            EncounterRegistry(clock), BossExtensionRegistry(), NoopPlayerHitModifier)
        val script = Sarachnis(deps, RayCastValidator(flags), RouteFactory(flags),
            AiPlayerInteractions(events, players), instances)
        val player = Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = Sarachnis.Spawn
            currentMapClock = clock.cycle
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val boss = Npc("npc.sarachnis", Sarachnis.Spawn)
        init {
            for (x in 1800..1880 step 8) for (z in 9864..9944 step 8) flags.allocateIfAbsent(x, z, 0)
            players[1] = player
            val ctx = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(script) { ctx.startup() }
            repo.add(boss, Int.MAX_VALUE)
        }
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
