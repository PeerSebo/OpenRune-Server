package org.rsmod.api.repo.npc

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

public class NpcRepository
@Inject
constructor(
    private val mapClock: MapClock,
    private val registry: NpcRegistry,
    private val npcList: NpcList,
) {
    private val addNpcs = ObjectArrayList<Npc>()
    private val delNpcs = ObjectArrayList<Npc>()
    private val addDelayedNpcs = ConcurrentLinkedQueue<Npc>()

    /**
     * **Note**: If [duration] is equal to [Int.MAX_VALUE], the [npc] will have its `respawn` flag
     * set to `true` and will respawn on death.
     */
    public fun add(npc: Npc, duration: Int) {
        val add = registry.add(npc)
        check(add.isSuccess()) { "Failed to add npc. (result=$add, npc=$npc)" }

        val permanentSpawn = duration == Int.MAX_VALUE
        npc.respawns = permanentSpawn

        if (!permanentSpawn) {
            val deleteCycle = mapClock + duration
            npc.lifecycleDelCycle = deleteCycle
        }
    }

    public fun del(npc: Npc, duration: Int) {
        val del = registry.del(npc)
        check(del.isSuccess()) { "Failed to delete npc. (result=$del, npc=$npc)" }
        if (duration != Int.MAX_VALUE) {
            val addCycle = mapClock + duration
            npc.lifecycleAddCycle = addCycle
        }
    }

    /** **Note**: Unlike [del], this function does **not** call [Npc.cancelActiveCoroutine]. */
    public fun hide(npc: Npc, duration: Int) {
        check(mapClock > npc.lifecycleRespawnCycle) {
            "Cannot hide npc while it is respawning. (npc=$npc)"
        }

        registry.hide(npc)
        if (duration != Int.MAX_VALUE) {
            val revealCycle = mapClock + duration
            npc.lifecycleRevealCycle = revealCycle
        }
    }

    /**
     * Similar to [hide], but this function is specifically for handling npc deaths.
     *
     * Calling this function will eventually trigger [NpcRegistry.respawn], which:
     * - Publishes the [NpcStateEvents.Respawn] event.
     * - Resets the npc's [Npc.coords] to its original [Npc.spawnCoords].
     *
     * **Note**: Unlike [del], this function does **not** call [Npc.cancelActiveCoroutine].
     */
    public fun despawn(npc: Npc, duration: Int) {
        // We do not want previously queued `hide` operations to go through in the
        // middle of the respawn.
        npc.lifecycleRevealCycle = 0

        registry.despawn(npc)
        if (duration != Int.MAX_VALUE) {
            val revealCycle = mapClock + duration
            npc.lifecycleRespawnCycle = revealCycle
        }
    }

    /**
     * Delay startup spawns until onNpcSpawn handlers are registered. Runtime equivalence
     * remains unverified; instance coordinates are unsupported.
     */
    public fun addDelayed(npc: Npc, spawnDelay: Int, duration: Int) {
        check(!RegionRegistry.inWorkingArea(npc.coords)) {
            "Cannot schedule npc spawn with `addDelayed` in regions."
        }
        val spawnCycle = mapClock + spawnDelay
        npc.lifecycleDelayedAddCycle = spawnCycle
        npc.lifecycleDelayedAddDuration = duration
        addDelayedNpcs.add(npc)
    }

    public fun findAll(zone: ZoneKey): Sequence<Npc> = registry.findAll(zone)

    public fun findAll(coords: CoordGrid): Sequence<Npc> =
        findAll(ZoneKey.from(coords)).filter { it.coords == coords }

    public fun findAll(zone: ZoneKey, zoneRadius: Int): Sequence<Npc> {
        return sequence {
            for (x in -zoneRadius..zoneRadius) {
                val targetX = zone.x + x
                if (targetX !in 0..ZoneKey.X_BIT_MASK) continue
                for (z in -zoneRadius..zoneRadius) {
                    val targetZ = zone.z + z
                    if (targetZ !in 0..ZoneKey.Z_BIT_MASK) continue
                    val translate = ZoneKey(x = targetX, z = targetZ, level = zone.level)
                    val players = findAll(translate)
                    yieldAll(players)
                }
            }
        }
    }

    internal fun processReveal(npc: Npc) {
        if (shouldTrigger(npc.lifecycleRespawnCycle)) {
            registry.respawn(npc)
        } else if (shouldTrigger(npc.lifecycleRevealCycle)) {
            registry.reveal(npc)
        }
    }

    internal fun processDurations() {
        computeDurations()
        processDelDurations()
        processAddDurations()
    }

    private fun computeDurations() {
        for (npc in npcList) {
            if (shouldTrigger(npc.lifecycleDelCycle)) {
                delNpcs.add(npc)
            }
            if (shouldTrigger(npc.lifecycleAddCycle)) {
                addNpcs.add(npc)
            }
            if (shouldTrigger(npc.lifecycleChangeCycle)) {
                npc.resetTransmog()
            }
        }
    }

    private fun processDelDurations() {
        for (npc in delNpcs) {
            registry.del(npc)
        }
        delNpcs.clear()
    }

    private fun processAddDurations() {
        for (npc in addNpcs) {
            registry.add(npc)
        }
        addNpcs.clear()
    }

    internal fun processDelayedAdd(): Int {
        if (addDelayedNpcs.isEmpty()) {
            return 0
        }
        return processAddDelayed(DELAYED_ADDS_PER_CYCLE)
    }

    internal fun flushDelayedAdds(): Int {
        if (addDelayedNpcs.isEmpty()) {
            return 0
        }
        return processAddDelayed(limit = Int.MAX_VALUE)
    }

    private fun processAddDelayed(limit: Int): Int {
        var added = 0
        val iterator = addDelayedNpcs.iterator()
        while (iterator.hasNext() && added < limit) {
            val npc = iterator.next()
            if (mapClock.cycle >= npc.lifecycleDelayedAddCycle) {
                add(npc, duration = npc.lifecycleDelayedAddDuration)
                iterator.remove()
                added++
            }
        }
        return added
    }

    private fun shouldTrigger(triggerCycle: Int): Boolean = mapClock.cycle == triggerCycle

    private companion object {
        private const val DELAYED_ADDS_PER_CYCLE: Int = 750
    }
}
