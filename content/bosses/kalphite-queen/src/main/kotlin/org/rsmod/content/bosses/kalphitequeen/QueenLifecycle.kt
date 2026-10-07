package org.rsmod.content.bosses.kalphitequeen

import dev.openrune.types.NpcServerType
import java.util.IdentityHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc
import org.rsmod.game.queue.WorldQueueList

internal class QueenLifecycle(private val queues: WorldQueueList, private val clock: () -> Int) {
    enum class Phase { Grounded, Transforming, Airborne, Dead }
    private class Life(
        var phase: Phase = Phase.Grounded,
        var lastCombat: Int? = null,
        var reversionClock: Int? = null,
    )
    private val lives = IdentityHashMap<Npc, Life>()

    fun reset(npc: Npc) {
        lives[npc] = Life()
        npc.movementLocked = false
        npc.ignoreCombatInteractions = false
        npc.apRangeOverride = ATTACK_RANGE
        npc.delay = Int.MIN_VALUE
    }

    fun remove(npc: Npc) { lives.remove(npc) }
    fun phase(npc: Npc): Phase = lives[npc]?.phase ?: Phase.Grounded

    fun combat(npc: Npc, clock: Int) {
        lives[npc]?.lastCombat = clock
    }

    @OptIn(InternalApi::class)
    fun tick(clock: Int) {
        for ((npc, life) in lives) {
            if (life.phase != Phase.Grounded && life.phase != Phase.Airborne) continue
            if (npc.hitpoints <= 0) continue
            if (life.phase == Phase.Airborne && life.reversionClock?.let { clock >= it } == true) {
                life.phase = Phase.Grounded
                life.reversionClock = null
                // Reversion alone preserves HP, drained stats and damage ownership.
                npc.resetTransmog()
            }
            if (life.lastCombat?.let { clock - it > IDLE_RESET_TICKS } == true) {
                npc.hitpoints = PHASE_HP
                npc.clearHeroPoints()
                life.lastCombat = null
            }
        }
    }

    fun acceptDeathQueue(npc: Npc) {
        // Regeneration runs before NPC queues. A queued lethal impact remains a defeat even if
        // that intervening regeneration step briefly restored one hitpoint.
        if (phase(npc) == Phase.Grounded || phase(npc) == Phase.Airborne) npc.hitpoints = 0
    }

    fun transform(npc: Npc, flying: NpcServerType, onEmerge: () -> Unit): Boolean {
        val life = lives.getOrPut(npc) { Life() }
        if (life.phase != Phase.Grounded || npc.hitpoints > 0) return false
        life.phase = Phase.Transforming
        npc.clearInteraction()
        npc.abortRoute()
        npc.noneMode()
        npc.hideAllOps()
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
        npc.delay(TRANSFORM_TICKS + 1)
        // Missiles already in flight belong to the defeated form, not its replacement health bar.
        npc.clearQueue("queue.hit")
        npc.clearQueue("queue.com_retaliate_player")
        queues.add(TRANSFORM_TICKS) {
            if (lives[npc] !== life || life.phase != Phase.Transforming) return@add
            npc.transmog(flying, Int.MAX_VALUE)
            npc.hitpoints = PHASE_HP
            life.phase = Phase.Airborne
            // World queues run before the engine refreshes the NPC's local map clock.
            life.reversionClock = clock() + REVERSION_TICKS
            npc.movementLocked = false
            npc.ignoreCombatInteractions = false
            npc.delay = Int.MIN_VALUE
            npc.showAllOps()
            npc.defaultMode()
            onEmerge()
        }
        return true
    }

    fun markFinalDeath(npc: Npc): Boolean {
        val life = lives[npc] ?: return false
        if (life.phase != Phase.Airborne || npc.hitpoints > 0) return false
        life.phase = Phase.Dead
        return true
    }

    companion object {
        const val PHASE_HP = 255
        const val TRANSFORM_TICKS = 20
        const val REVERSION_TICKS = 2000
        const val IDLE_RESET_TICKS = 500
        const val ATTACK_RANGE = 8
    }
}
