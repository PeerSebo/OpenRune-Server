package org.rsmod.api.game.process.player

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.plugin.PlayerDeathScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlayerQueueProcessorTest {
    @Test
    fun GameTestState.`no death queue speed-up if last hit kills player`() =
        runGameTest(PlayerHitScript::class, PlayerDeathScript::class) {
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), man)
            player.placeAt(npc.coords.translateZ(1))
            player.setCurrentLevel(stats.hitpoints, 7)

            player.queueCombatRetaliate(npc, delay = 1)
            player.queueHit(npc, delay = 1, HitType.Typeless, damage = 5)
            player.queueHit(npc, delay = 1, HitType.Typeless, damage = 7)

            advance(ticks = 1)
            check(player.hitpoints == 0)
            assertFalse(player.isDelayed)

            advance(ticks = 1)
            assertTrue(player.isDelayed)
        }

    @Test
    fun GameTestState.`trigger death queue speed-up if first hit kills player`() =
        runGameTest(PlayerHitScript::class, PlayerDeathScript::class) {
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), man)
            player.placeAt(npc.coords.translateZ(1))
            player.setCurrentLevel(stats.hitpoints, 7)

            player.queueCombatRetaliate(npc, delay = 1)
            player.queueHit(npc, delay = 1, HitType.Typeless, damage = 7)
            player.queueHit(npc, delay = 1, HitType.Typeless, damage = 5)

            check(player.isNotDelayed)

            // A death queue appended during traversal can fire this cycle after the remaining
            // queues.
            advance(ticks = 1)
            check(player.hitpoints == 0)
            check(!player.hasMovedPreviousCycle)

            assertTrue(player.isDelayed)
        }

    @Test
    fun GameTestState.`weak queue add-tick decrement survives unrelated pending normal queue`() =
        runInjectedGameTest(WeakLoopState::class, WeakLoopModule, WeakLoopScript::class) {
            // One long-dated NORMAL queue sits in the list the whole test. The old weak-sweep
            // break clause inspected `queueList.size == 1` (the normal list), so this pending
            // queue used to skip the re-sweep that gives a just-requeued weak queue its
            // add-tick decrement - running the loop one tick slow.
            player.queue(PENDING_QUEUE, cycles = 20)

            // Added outside queue processing: cycles=2 fires after 2 full ticks.
            player.weakQueue(LOOP_QUEUE, cycles = 2)
            advance(ticks = 2)
            assertEquals(1, it.fired)

            // Requeues from inside the handler (cycles=2) receive the add-tick decrement and
            // must keep firing every tick - the documented n-1 weak-queue semantics production
            // loops compensate for.
            advance(ticks = 3)
            assertEquals(4, it.fired)
        }

    @Test
    fun GameTestState.`weak queue add-tick decrement holds for a lone self-requeueing loop`() =
        runInjectedGameTest(WeakLoopState::class, WeakLoopModule, WeakLoopScript::class) {
            player.weakQueue(LOOP_QUEUE, cycles = 2)
            advance(ticks = 2)
            assertEquals(1, it.fired)

            advance(ticks = 3)
            assertEquals(4, it.fired)
        }

    private class WeakLoopScript @Inject constructor(private val state: WeakLoopState) :
        PluginScript() {
        override fun ScriptContext.startup() {
            onPlayerQueue(LOOP_QUEUE) {
                state.fired++
                weakQueue(LOOP_QUEUE, cycles = 2)
            }
        }
    }

    private object WeakLoopModule : AbstractModule() {
        override fun configure() {
            bind(WeakLoopState::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private data class WeakLoopState(var fired: Int = 0)

    private companion object {
        private const val LOOP_QUEUE = "queue.generic_queue1"
        private const val PENDING_QUEUE = "queue.generic_queue2"

        private val man =
            npcTypeFactory.create {
                name = "Man"
                op[1] = "Attack"
                size = 1
                hitpoints = 7
                defaultMode = NpcMode.None
            }
    }
}
