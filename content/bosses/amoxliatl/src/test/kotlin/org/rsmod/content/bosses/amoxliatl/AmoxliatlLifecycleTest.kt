package org.rsmod.content.bosses.amoxliatl

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
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
import org.rsmod.game.hit.HitType
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
class AmoxliatlLifecycleTest {
    @Test fun `negative armour applies on successive hits through the actual modifier`() {
        val f = Fixture()
        val modifier = StandardNpcHitModifier(f.events, f.players)
        repeat(2) {
            val hit = hit(10)
            with(modifier) { hit.modify(f.boss) }
            assertEquals(12, hit.damage)
            assertEquals(0, f.boss.vars["varn.flat_armour"])
        }
    }

    @Test fun `lethal impact removes blocks and cancels their pending heal and pools`() {
        val f = Fixture()
        val block = f.block()
        f.boss.hitpoints = 0
        f.events.publish(NpcHitEvents.AnyImpact(f.boss, hit(100).build()))

        assertFalse(block.isSlotAssigned)
        f.advance(50)

        assertEquals(0, f.boss.hitpoints)
        assertFalse(f.hasPool())
    }

    @Test fun `instance deletion removes current pools and cancels later ones`() {
        val f = Fixture()
        f.attackFx()
        f.advance(3)
        assertTrue(f.hasPool())
        val block = f.block()

        f.npcs.del(f.boss, Int.MAX_VALUE)
        f.advance(50)

        assertFalse(block.isSlotAssigned)
        assertFalse(f.hasPool())
        assertTrue(f.queues.isEmpty)
    }

    @Test fun `respawning the same boss object cannot inherit previous attack callbacks`() {
        val f = Fixture()
        f.attackFx()
        f.events.publish(NpcStateEvents.Respawn(f.boss))
        f.advance(40)
        assertFalse(f.hasPool())

        f.attackFx()
        f.advance(3)
        assertTrue(f.hasPool())
    }

    @Test fun `deleted exploding block is not deleted twice or replaced by a pool`() {
        val f = Fixture()
        f.boss.hitpoints = 100
        val block = f.block()
        f.advance(15)
        assertTrue(f.boss.hitpoints > 100)
        f.npcs.del(block, Int.MAX_VALUE)

        f.advance(40)

        assertFalse(f.hasPool())
        assertTrue(f.queues.isEmpty)
    }

    private class Fixture {
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val events = EventBus()
        val players = PlayerList()
        private val collision = CollisionFlagMap()
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
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        private val deps = BossDeps(random, unused<AccuracyFormulae>(), WorldRepository(updates), npcs,
            players, clock, queues, collision, EncounterRegistry(clock), extensions, PlayerHitModifier {})
        val boss: Npc
        private val target = Player().apply { coords = CoordGrid(3210, 3210) }
        private val access: StandardNpcAccess
        private val process = WorldQueueListProcess(queues)

        init {
            for (x in 3190..3230) for (z in 3190..3230) collision.allocateIfAbsent(x, z, 0)
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(Amoxliatl(deps, locs)) { context.startup() }
            boss = Npc("npc.amoxliatl", CoordGrid(3200, 3200)).also { npcs.add(it, Int.MAX_VALUE) }
            access = StandardNpcAccess(boss, GameCoroutine(), StandardNpcAccessContext(
                { random }, { error("No NPC access hit modifier expected") }, { error("No hit processor expected") },
            ))
        }

        fun attackFx() = extensions.invoke("amoxliatl.standard_attack_pool", access, boss, target, null)

        fun block(): Npc = Npc("npc.amoxliatl_ice_block", target.coords).also {
            npcs.add(it, Int.MAX_VALUE)
            extensions.invoke("amoxliatl.track_ice_block", access, it, target, null)
        }

        fun hasPool() = locs.findLoc(target.coords, "loc.amoxliatl_ice")

        fun advance(ticks: Int) {
            repeat(ticks) {
                clock.tick()
                process.process()
            }
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private fun hit(damage: Int) = HitBuilder(
            type = HitType.Typeless, damage = damage, sourceUid = null, sourceSlot = null,
            isFromNpc = false, isFromPlayer = false, clientDelay = 0,
            righthandType = null, secondaryType = null, targetHitmark = 0, sourceHitmark = 0,
            publicHitmark = 0, zeroDamageHitmarkLit = null, zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null, targetMaxDamageThreshold = Int.MAX_VALUE, sourceMaxDamageThreshold = Int.MAX_VALUE,
        )

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
