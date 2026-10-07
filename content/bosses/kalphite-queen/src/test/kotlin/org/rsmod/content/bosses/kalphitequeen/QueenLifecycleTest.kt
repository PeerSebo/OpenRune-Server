package org.rsmod.content.bosses.kalphitequeen

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.npc.NpcRegenProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.entity.Npc
import org.rsmod.game.queue.WorldQueueList

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueenLifecycleTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `first defeat waits twenty ticks then restores only second-form health`() {
        val f = Fixture()
        f.npc.defenceLvl = 42
        f.npc.attackLvl = 100
        f.npc.hitpoints = 0
        assertTrue(f.transform())
        f.advance(19)
        assertEquals(0, f.npc.hitpoints)
        assertEquals(QueenLifecycle.Phase.Transforming, f.life.phase(f.npc))
        assertTrue(f.npc.ignoreCombatInteractions)
        f.advance(1)
        assertEquals(255, f.npc.hitpoints)
        assertEquals(f.flying.id, f.npc.visType.id)
        assertEquals(42, f.npc.defenceLvl)
        assertEquals(100, f.npc.attackLvl)
        assertFalse(f.npc.ignoreCombatInteractions)
        assertEquals(1, f.emerged)
    }

    @Test fun `form change preserves original npc and hero points`() {
        val f = Fixture()
        f.npc.heroPoints.add(123L, 200)
        f.npc.hitpoints = 0
        f.transform()
        f.advance(20)
        assertEquals(f.ground.id, f.npc.id)
        assertEquals(123L, f.npc.heroPoints.toMutableList().single().uuid)
        assertEquals(200, f.npc.heroPoints.toMutableList().single().points)
        assertFalse(f.life.markFinalDeath(f.npc))
    }

    @Test fun `a due regeneration cycle cannot skip a queued first-form defeat`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.npc.regenClock = 0
        NpcRegenProcessor().process(f.npc)
        f.life.acceptDeathQueue(f.npc)
        assertTrue(f.transform())
        f.advance(20)
        assertEquals(QueenLifecycle.Phase.Airborne, f.life.phase(f.npc))
    }

    @Test fun `transition delay suppresses regeneration until emergence`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.npc.defenceLvl = 42
        f.npc.regenClock = 0
        f.transform()
        repeat(19) {
            f.advance(1, regenerate = true)
            assertEquals(0, f.npc.hitpoints)
            assertEquals(42, f.npc.defenceLvl)
        }
        f.advance(1)
        assertEquals(255, f.npc.hitpoints)
        assertTrue(f.npc.isNotDelayed)
    }

    @Test fun `first form never qualifies for loot and second form finalizes once`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        assertFalse(f.life.markFinalDeath(f.npc))
        assertTrue(f.transform())
        assertFalse(f.transform())
        f.advance(20)
        f.npc.hitpoints = 0
        assertFalse(f.transform())
        assertTrue(f.life.markFinalDeath(f.npc))
        assertFalse(f.life.markFinalDeath(f.npc))
    }

    @Test fun `transformation clears already fired first-form hits`() {
        val f = Fixture()
        f.npc.queue("queue.hit", 10)
        f.npc.queue("queue.com_retaliate_player", 2)
        f.npc.hitpoints = 0
        f.transform()
        assertFalse("queue.hit" in f.npc.queueList)
        assertFalse("queue.com_retaliate_player" in f.npc.queueList)
    }

    @Test fun `second form reverts after twenty minutes preserving remaining health`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.transform()
        f.advance(20)
        f.npc.hitpoints = 71
        f.npc.defenceLvl = 52
        f.advance(1999, inCombat = true)
        assertEquals(QueenLifecycle.Phase.Airborne, f.life.phase(f.npc))
        f.advance(1)
        assertEquals(QueenLifecycle.Phase.Grounded, f.life.phase(f.npc))
        assertEquals(f.ground.id, f.npc.visType.id)
        assertEquals(71, f.npc.hitpoints)
        assertEquals(52, f.npc.defenceLvl)
    }

    @Test fun `defeating a reverted first form restores a fresh airborne health bar`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.transform()
        f.advance(2020)
        f.npc.hitpoints = 0
        assertTrue(f.transform())
        f.advance(20)
        assertEquals(255, f.npc.hitpoints)
        assertEquals(2, f.emerged)
    }

    @Test fun `despawn or new life cancels an old transformation callback`() {
        val removed = Fixture()
        removed.npc.hitpoints = 0
        removed.transform()
        removed.life.remove(removed.npc)
        removed.advance(20)
        assertEquals(0, removed.emerged)
        val reset = Fixture()
        reset.npc.hitpoints = 0
        reset.transform()
        reset.life.reset(reset.npc)
        reset.advance(20)
        assertEquals(0, reset.emerged)
        assertEquals(QueenLifecycle.Phase.Grounded, reset.life.phase(reset.npc))
    }

    @Test fun `old reversion timer cannot change a new fight`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.transform()
        f.advance(20)
        f.life.reset(f.npc)
        f.npc.resetTransmog()
        f.npc.hitpoints = 0
        f.transform()
        f.advance(2000)
        assertEquals(QueenLifecycle.Phase.Airborne, f.life.phase(f.npc))
    }

    @Test fun `more than five idle minutes heals health and resets previous damage ownership`() {
        val f = Fixture()
        f.npc.hitpoints = 67
        f.npc.defenceLvl = 42
        f.npc.heroPoints.add(123L, 188)
        f.life.combat(f.npc, f.npc.currentMapClock)
        f.advance(500)
        assertEquals(67, f.npc.hitpoints)
        f.advance(1)
        assertEquals(255, f.npc.hitpoints)
        assertEquals(42, f.npc.defenceLvl)
        assertTrue(f.npc.heroPoints.toMutableList().isEmpty())
    }

    @Test fun `combat restarts idle countdown without restarting the airborne form timer`() {
        val f = Fixture()
        f.npc.hitpoints = 0
        f.transform()
        f.advance(20)
        f.npc.hitpoints = 67
        f.life.combat(f.npc, f.npc.currentMapClock)
        f.advance(499)
        f.life.combat(f.npc, f.npc.currentMapClock)
        f.advance(2)
        assertEquals(67, f.npc.hitpoints)
        f.advance(499)
        assertEquals(255, f.npc.hitpoints)
        assertEquals(QueenLifecycle.Phase.Airborne, f.life.phase(f.npc))
        f.advance(1000)
        assertEquals(QueenLifecycle.Phase.Grounded, f.life.phase(f.npc))
    }

    @Test fun `idle timer cannot revive a defeated or transforming queen`() {
        val f = Fixture()
        f.life.combat(f.npc, f.npc.currentMapClock - 1000)
        f.npc.hitpoints = 0
        f.life.tick(f.npc.currentMapClock)
        assertEquals(0, f.npc.hitpoints)
        f.transform()
        f.life.tick(f.npc.currentMapClock)
        assertEquals(0, f.npc.hitpoints)
        assertEquals(QueenLifecycle.Phase.Transforming, f.life.phase(f.npc))
    }

    private class Fixture {
        val ground = ServerCacheManager.getNpc(KalphiteQueen.GROUND.asRSCM(RSCMType.NPC))!!
        val flying = ServerCacheManager.getNpc(KalphiteQueen.FLYING.asRSCM(RSCMType.NPC))!!
        val npc = Npc(ground).apply { currentMapClock = 100; processedMapClock = 100 }
        var worldClock = 100
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val life = QueenLifecycle(queues) { worldClock }
        var emerged = 0
        init { life.reset(npc) }
        fun transform() = life.transform(npc, flying) { emerged++ }
        fun advance(ticks: Int, inCombat: Boolean = false, regenerate: Boolean = false) { repeat(ticks) {
            worldClock++
            // Match GameCycle: world callbacks see the previous NPC-local clock.
            process.process()
            npc.currentMapClock = worldClock
            npc.processedMapClock = worldClock
            if (regenerate && npc.isNotDelayed) NpcRegenProcessor().process(npc)
            if (inCombat) life.combat(npc, worldClock)
            life.tick(worldClock)
        } }
    }
}
