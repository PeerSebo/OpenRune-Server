package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsData
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryMachineryScript @Inject constructor(
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val queues: WorldQueueList,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { queues.addDeferred(1, ::pulse) }
    }

    internal fun pulse() {
        if (clock.cycle % HAMMER_CYCLES == 0) strike()
        queues.addDeferred(1, ::pulse)
    }

    internal fun strike() {
        val definition = SleepingGiantsData.repairs.single {
            it.original == "loc.giants_foundry_trip_hammer"
        }
        val hammer = locs.findAll(definition.coords).firstOrNull {
            it.id == definition.original.asRSCM()
        }
        if (hammer != null) {
            world.locAnim(hammer, "seq.giants_foundry_hammer01")
            locs.findAll(hammer.coords.translate(-1, -1)).firstOrNull {
                it.id == "loc.giants_foundry_tool_powerunit02".asRSCM()
            }?.let { world.locAnim(it, "seq.giants_foundry_hammer01") }
            world.soundArea(hammer.coords.translate(4, 1), "synth.SG_BIG_HAMMER_HIT", radius = 15)
        }
    }

    companion object {
        const val HAMMER_CYCLES = 5

        internal fun cyclesUntilStrike(cycle: Int): Int =
            HAMMER_CYCLES - Math.floorMod(cycle, HAMMER_CYCLES)
    }
}
