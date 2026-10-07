package org.rsmod.content.minigames.pyramidplunder.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.minigames.pyramidplunder.PlunderRun
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.exitPlunderRun
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderTimerCount
import org.rsmod.content.minigames.pyramidplunder.PlunderTimer
import org.rsmod.content.minigames.pyramidplunder.PlunderWorldStateManager
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderTimerScript
@Inject
constructor(
    private val worldState: PlunderWorldStateManager,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        worldState.bind(random)

        onEvent<GameLifecycle.LateCycle> { worldState.tick() }

        onPlayerSoftTimer(PlunderTimer.TICK_TIMER) {
            // Clamp the soft counter while protected access delays the actual expiry callback.
            if (player.plunderTimerCount < TIMER_COUNT_MAX) {
                player.plunderTimerCount += 1
            }
        }

        onPlayerTimer(PlunderTimer.RUN_TIMER) {
            spam("Your time is up...")
            // Timers run after queues; queue expiry relocation for the next tick.
            queue(QUEUE_EXPIRY, cycles = 1)
        }

        onPlayerQueue(QUEUE_EXPIRY) {
            exitPlunderRun(eventBus)
            startDialogue {
                chatNpcSpecific(
                    "Guardian Mummy",
                    PlunderLocs.MUMMY_GUARDIAN,
                    neutral,
                    "You've had your five minutes of plundering. Now be off with you!",
                )
            }
        }
    }

    private companion object {
        const val TIMER_COUNT_MAX = PlunderRun.RUN_DURATION_CYCLES

        const val QUEUE_EXPIRY = "queue.ntk_run_expiry"
    }
}
