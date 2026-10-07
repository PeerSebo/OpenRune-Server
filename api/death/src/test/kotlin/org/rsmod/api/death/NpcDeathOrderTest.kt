package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NpcDeathOrderTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `one-life death delivers hero and stable uid to loot and kill hooks before deletion`() {
        exercise(respawns = false)
    }

    @Test fun `respawning death still delivers exactly one reward then hides until respawn`() {
        exercise(respawns = true)
    }

    @Test fun `shoreline loot overrides reach all reward hooks without relocating the boss`() {
        exercise(respawns = false, dropCoords = CoordGrid(3203, 3205))
    }

    private fun exercise(respawns: Boolean, dropCoords: CoordGrid = CoordGrid(3200, 3200)) {
        val clock = MapClock()
        val npcs = NpcList()
        val flags = CollisionFlagMap()
        flags.allocateIfAbsent(3200, 3200, 0)
        val repo = NpcRepository(clock, NpcRegistry(npcs, flags, EventBus()), npcs)
        val npc = Npc("npc.hillgiant_boss", CoordGrid(3200, 3200))
        repo.add(npc, Int.MAX_VALUE)
        npc.respawns = respawns
        val originalUid = npc.uid
        val players = PlayerList()
        val player = Player().apply { uuid = 123L; slotId = 1 }
        players[1] = player
        npc.heroPoints(player, 100)
        npc.hitpoints = 0
        var drops = 0
        var kills = 0
        val dropHook = NpcDeathDropHook { context ->
            assertEquals(dropCoords, context.dropCoords)
            assertSame(player, context.hero)
            assertSame(npc, npcs[npc.slotId])
            assertEquals(originalUid, npc.uid)
            drops++
            true
        }
        val killHook = NpcDeathKillHook { context ->
            assertEquals(dropCoords, context.dropCoords)
            assertEquals(CoordGrid(3200, 3200), context.npc.coords)
            assertSame(player, context.hero)
            assertTrue(npc.isSlotAssigned)
            assertEquals(originalUid, npc.uid)
            kills++
        }
        val death = NpcDeath(repo, players, ObjRepository(clock, ObjRegistry(ZoneUpdateMap())),
            setOf(dropHook), setOf(killHook))
        val coroutine = GameCoroutine("death-regression")
        val access = StandardNpcAccess(npc, coroutine, StandardNpcAccessContext(
            getRandom = { error("unused") }, getHitModifier = { error("unused") },
            getHitProcessor = { error("unused") }))
        var completed = false
        val block: suspend () -> Unit = { death.deathWithDrops(access, dropCoords) }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); completed = true }
        })
        assertEquals(0, kills, "No loot before the death animation completes")
        repeat(100) { cycle ->
            clock.cycle = cycle + 1
            npc.currentMapClock = cycle + 1
            npc.processedMapClock = cycle + 1
            coroutine.advance()
        }
        assertTrue(completed)
        assertEquals(1, drops)
        assertEquals(1, kills)
        assertEquals(respawns, npc.isSlotAssigned)
        if (respawns) assertTrue(npc.hidden)
    }
}
