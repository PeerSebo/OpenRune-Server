package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.knockback
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
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.inv.Inventory
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
@OptIn(InternalApi::class)
class MuspahLifecycleTest {
    @Test fun `teleport sequence restores pursuit after clearing the old route`() {
        val f = Fixture()
        f.teleport()
        f.advance(100)
        assertNotNull(f.boss.interaction)
        assertNotNull(f.boss.routeRequest)
        assertTrue(f.boss.isVisType("npc.muspah_melee"))
    }

    @Test fun `teleport sequence does not reacquire a player who left the room`() {
        val f = Fixture()
        f.teleport()
        f.target.coords = CoordGrid(3222, 3218)
        f.advance(100)
        assertNull(f.boss.interaction)
    }

    @Test fun `respawn discards pending spike warnings from the previous fight`() {
        val f = Fixture()
        f.slam()
        f.events.publish(NpcStateEvents.Respawn(f.boss))
        f.advance(8)
        assertFalse(f.hasSpikes())

        f.slam()
        f.advance(2)
        assertTrue(f.hasSpikes())
    }

    @Test fun `deletion removes warned spikes before solidification`() {
        val f = Fixture()
        f.slam()
        f.advance(2)
        assertTrue(f.hasSpikes())
        f.npcs.del(f.boss, Int.MAX_VALUE)
        f.advance(8)
        assertFalse(f.hasSpikes())
    }

    @Test fun `deletion removes moving clouds and drains their callbacks`() {
        val f = Fixture()
        f.hazards()
        assertTrue(f.cloudCount() > 0)
        f.npcs.del(f.boss, Int.MAX_VALUE)
        assertEquals(0, f.cloudCount())
        f.advance(20)
        assertTrue(f.queues.isEmpty)
    }

    @Test fun `homing spike cannot chase a player who left the arena`() {
        val f = Fixture()
        f.homing()
        f.target.coords = CoordGrid(3222, 3218)
        f.advance(50)
        assertFalse(f.hasSpikes())
        assertTrue(f.queues.isEmpty)
    }

    @Test fun `respawn removes homing spikes and cannot recreate old ones`() {
        val f = Fixture()
        f.homing()
        f.advance(2)
        assertTrue(f.hasSpikes())
        f.events.publish(NpcStateEvents.Respawn(f.boss))
        f.advance(50)
        assertFalse(f.hasSpikes())
    }

    @Test fun `knockback begins at original tile relative to the updated server position`() {
        val f = Fixture()
        val from = CoordGrid(2846, 4258)
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val destination = from.translate(dx, dz)
            f.target.coords = from
            f.target.knockback(destination, 0, f.collision)
            val move = checkNotNull(f.target.pendingExactMove)
            assertEquals(from, destination.translate(move.deltaX1, move.deltaZ1))
            assertEquals(destination, destination.translate(move.deltaX2, move.deltaZ2))
            assertEquals(30, move.clientDelay2)
        }
    }

    @Test fun `shockwave checks occupancy on arrival instead of remembering a departed player`() {
        val f = Fixture()
        f.target.coords = f.boss.coords.translate(-15, 0)
        f.shockwave()
        f.target.coords = CoordGrid(3222, 3218)
        f.advance(8)
        assertTrue(f.hits.isEmpty())
    }

    @Test fun `shockwave hits a player entering its path only once`() {
        val f = Fixture()
        f.target.coords = CoordGrid(3222, 3218)
        f.shockwave()
        f.target.coords = f.boss.coords.translate(-15, 0)
        f.advance(8)
        assertEquals(1, f.hits.size)
    }

    @Test fun `respawn cancels the previous shockwave damage`() {
        val f = Fixture()
        f.target.coords = f.boss.coords.translate(-15, 0)
        f.shockwave()
        f.events.publish(NpcStateEvents.Respawn(f.boss))
        f.advance(8)
        assertTrue(f.hits.isEmpty())
    }

    @Test fun `stationary melee attack sends penetration to protection prayer handling`() {
        val f = Fixture()
        f.boss.vars["varn.muspah_melee_still_ticks"] = 3
        f.melee()
        assertEquals(15, f.hits.single().prayerPenetration)
    }

    private class Fixture {
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val events = EventBus()
        private val players = PlayerList()
        val collision = CollisionFlagMap()
        private val npcList = NpcList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        private val extensions = BossExtensionRegistry()
        private val interactions = AiPlayerInteractions(events, players)
        private val script: Muspah
        val hits = mutableListOf<HitBuilder>()
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        private val deps = BossDeps(random, unused<AccuracyFormulae>(), WorldRepository(updates), npcs,
            players, clock, queues, collision, EncounterRegistry(clock), extensions, PlayerHitModifier { hits += this })
        val boss: Npc
        val target = Player().apply {
            coords = CoordGrid(2848, 4260)
            slotId = 1
            uuid = 1L
            assignUid()
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val access: StandardNpcAccess
        private val process = WorldQueueListProcess(queues)

        init {
            for (x in 2800..2900 step 8) for (z in 4216..4312 step 8) collision.allocateIfAbsent(x, z, 0)
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            script = Muspah(deps, locs, npcList, unused<BossHpBarScript>(), regions, interactions)
            with(script) { context.startup() }
            boss = Npc("npc.muspah", CoordGrid(2846, 4258)).also { npcs.add(it, Int.MAX_VALUE) }
            access = StandardNpcAccess(boss, GameCoroutine(), StandardNpcAccessContext(
                { random }, { error("No NPC access hit modifier expected") }, { error("No hit processor expected") },
            ))
            players[1] = target
        }

        fun slam() = extensions.invoke("muspah_spike_slam", access, boss, target, null)
        fun hazards() = extensions.invoke("muspah_hazard_batch", access, boss, target, null)
        fun homing() = extensions.invoke("muspah_homing_spikes", access, boss, target, null)
        fun shockwave() = extensions.invoke("muspah_shockwave_release", access, boss, target, null)
        fun melee() = extensions.invoke("muspah_melee_hit", access, boss, target, null)
        fun teleport() {
            boss.apPlayer2(target, interactions)
            EffectInterpreter(boss, target, script.spec, deps.encounterRegistry.of(boss), deps)
                .run(access, checkNotNull(script.spec.abilities["cloud_teleport"]))
        }
        fun cloudCount() = npcList.count { it.isVisType("npc.muspah_projectile") }
        fun hasSpikes(): Boolean =
            (2800..2900).any { x -> (4216..4312).any { z ->
                val tile = CoordGrid(x, z)
                locs.findLoc(tile, "loc.muspah_spike") ||
                    locs.findLoc(tile, "loc.muspah_spike_pre") ||
                    locs.findLoc(tile, "loc.muspah_spike_follow")
            } }

        fun advance(ticks: Int) {
            repeat(ticks) {
                clock.tick()
                process.process()
            }
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
