package org.rsmod.content.bosses.dukesucellus

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class DukeCombatTest {
    @Test fun `slam penetrates protection prayer without delaying the defend animation`() {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val npcList = NpcList()
        val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
        val players = PlayerList()
        val hits = mutableListOf<HitBuilder>()
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val extensions = BossExtensionRegistry()
        val deps = BossDeps(random, unused<AccuracyFormulae>(), WorldRepository(ZoneUpdateMap()),
            npcs, players, clock, WorldQueueList(), collision, EncounterRegistry(clock), extensions,
            PlayerHitModifier { hits += this })
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(DukeSucellus(deps, events)) { context.startup() }
        val boss = Npc("npc.duke_sucellus_awake", CoordGrid(3036, 6452))
        npcs.add(boss, Int.MAX_VALUE)
        val player = Player().apply {
            coords = boss.coords.translate(3, -1)
            slotId = 1
            uuid = 1L
            assignUid()
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        players[1] = player
        val access = StandardNpcAccess(boss, GameCoroutine(), StandardNpcAccessContext(
            { random }, { error("No access hit modifier expected") }, { error("No hit processor expected") },
        ))

        extensions.invoke("duke_sucellus.melee_slam", access, boss, player, null)

        assertEquals(75, hits.single().prayerPenetration)
        assertEquals(0, player.pendingSequence.delay)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
