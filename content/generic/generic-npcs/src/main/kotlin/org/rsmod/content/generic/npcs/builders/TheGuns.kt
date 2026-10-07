package org.rsmod.content.generic.npcs.builders

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiTimer
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TheGuns @Inject constructor(private val random: GameRandom) : PluginScript() {
    // Scoped to the actual NPC, so replacement spawns start a new set and removed NPCs are not held.
    private val sets = WeakHashMap<Npc, Workout>()

    override fun ScriptContext.startup() {
        onAiTimer("npc.tol_npc_builder04") { npc.countPress() }
    }

    private fun Npc.countPress() {
        val workout = sets.getOrPut(this) { newWorkout() }
        if (workout.repetitions == workout.restartAt) {
            say("Start again!")
            sets[this] = newWorkout()
        } else {
            workout.repetitions++
            say(workout.repetitions.toString())
        }
    }

    private fun newWorkout() = Workout(restartAt = random.of(8200, 10000))

    private class Workout(val restartAt: Int, var repetitions: Int = 0)
}
