package org.rsmod.content.generic.npcs.ducks

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.api.script.onAiContentTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class Ducks
@Inject
constructor(
    private val random: GameRandom,
    private val death: NpcDeath,
    private val search: NpcSearch,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val ducklingStates = WeakHashMap<Npc, DucklingState>()
    private val steps = StepFactory(collision)

    override fun ScriptContext.startup() {
        for (type in ducklingTypes + listOf("npc.duck", "npc.duck_female")) {
            onEvent<NpcStateEvents.Spawn>(type.asRSCM()) { npc.setPoolCollision() }
        }
        onAiContentTimer("content.duck") { npc.duckTimer() }
        onAiContentTimer("content.duckling") { npc.ducklingTimer() }
        onNpcQueue("content.duck", "queue.death") { duckDeath() }
        onNpcQueue("content.duck", "queue.generic_queue1") { duckSay() }
        onNpcQueue("content.duckling", "queue.generic_queue1") { ducklingSay() }
        onNpcQueue("content.duckling", "queue.generic_queue2") { ducklingMourn() }
    }

    private fun Npc.duckTimer() {
        setPoolCollision()
        setNextTimer()
        queue("queue.generic_queue1", 1)
    }

    private fun StandardNpcAccess.duckSay() {
        say("Quack!")
        worldRepo.soundArea(coords, "synth.QUACK")
    }

    private suspend fun StandardNpcAccess.duckDeath() {
        val ducklings =
            ducklingTypes.asSequence().flatMap {
                search.findAll(coords, it, 1, HuntVis.LineOfSight)
            }
        for (duckling in ducklings) {
            duckling.queue("queue.generic_queue2", 2)
        }
        val duck = search.find(coords, "npc.duck", 1, HuntVis.LineOfSight)
        duck?.queue("queue.generic_queue1", 2)
        death.deathNoDrops(this)
    }

    private fun Npc.ducklingTimer() {
        setPoolCollision()
        // Keep chatter slower than swimming so ducks do not quack every movement tick.
        if (isBusy || isDelayed || hitpoints <= 0 || (mode != null && mode != NpcMode.None)) {
            return
        }
        val state = ducklingStates.getOrPut(this) { DucklingState(random.of(50..100)) }
        if (state.fleeUntil > currentMapClock) {
            return
        }
        val previousMother = state.mother
        val mother =
            previousMother?.takeIf { it.isAvailableMother(this) }
                ?: search.findAll(coords, "npc.duck_female", 10, HuntVis.LineOfSight)
                    .filter { it.isAvailableMother(this) }
                    .minByOrNull { coords.chebyshevDistance(it.coords) }
        state.mother = mother
        if (mother != null) {
            faceNpc(mother)
            if (coords.chebyshevDistance(mother.coords) > 1) {
                walk(mother.coords)
            } else {
                abortRoute()
            }
        } else {
            resetFaceEntity()
            if (previousMother != null) {
                abortRoute()
            }
            if (random.randomBoolean(8)) {
                swimAway()
            }
        }
        if (--state.voiceTicks <= 0) {
            state.voiceTicks = random.of(50..100)
            if (mother != null) {
                mother.say("Quack?")
                worldRepo.soundArea(mother.coords, "synth.QUACK")
                // AI timers run before NPC queues, which consume the first count this tick.
                queue("queue.generic_queue1", 3)
            } else {
                queue("queue.generic_queue1", 1)
            }
        }
    }

    private fun StandardNpcAccess.ducklingSay() {
        say("Eep!")
        // Despite its gameval name, DUCK_HIT is also the baby duck quack.
        worldRepo.soundArea(coords, "synth.DUCK_HIT")
    }

    private fun StandardNpcAccess.ducklingMourn() {
        ducklingSay()
        if (npc.mode != null && npc.mode != NpcMode.None) {
            return
        }
        val state = ducklingStates.getOrPut(npc) { DucklingState(random.of(50..100)) }
        state.mother = null
        state.fleeUntil = npc.currentMapClock + 10
        npc.resetFaceEntity()
        npc.abortRoute()
        npc.swimAway(coords, 9)
    }

    private fun Npc.isAvailableMother(duckling: Npc): Boolean =
        isValidTarget() && coords.level == duckling.coords.level &&
            coords.chebyshevDistance(duckling.coords) <= 10 &&
            coords.chebyshevDistance(duckling.spawnCoords) <= duckling.type.maxRange &&
            (!inGuildPool(duckling.coords) || inGuildPool(coords))

    private fun Npc.setPoolCollision() {
        // This pool uses ordinary floor and invisible walls; PassThru preserves the walls while allowing overlap.
        moveRestrict = if (inGuildPool(coords) && inGuildPool(spawnCoords)) MoveRestrict.PassThru else type.moveRestrict
    }

    private fun Npc.swimAway(centre: CoordGrid = spawnCoords, radius: Int = wanderRange) {
        val pool = inGuildPool(coords) && inGuildPool(spawnCoords)
        val strategy = collisionStrategy ?: return
        repeat(8) {
            val destination = if (pool) random.of(coords, minOf(radius, 2)) else random.of(centre, radius)
            if (destination == coords ||
                destination.chebyshevDistance(spawnCoords) > type.maxRange ||
                (if (pool) !inGuildPool(destination) else
                    collision[destination.x, destination.z, destination.level] and CollisionFlag.BLOCK_WALK == 0)) {
                return@repeat
            }
            if (steps.validated(this, coords, destination, strategy) != CoordGrid.NULL) {
                walk(destination)
                return
            }
        }
    }

    private class DucklingState(var voiceTicks: Int, var mother: Npc? = null, var fleeUntil: Int = 0)

    private fun Npc.setNextTimer() {
        val next = random.of(50..100)
        aiTimer(next)
    }

    private companion object {
        val ducklingTypes = listOf("npc.duckling", "npc.duck_update_ducklings")

        fun inGuildPool(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 1225..1226 && coords.z in 3727..3732
    }
}
