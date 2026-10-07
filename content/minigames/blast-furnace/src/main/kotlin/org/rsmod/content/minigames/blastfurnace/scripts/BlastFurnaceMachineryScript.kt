package org.rsmod.content.minigames.blastfurnace.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceMachine
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceVisualEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class BlastFurnaceMachineryScript
@Inject
constructor(private val machine: BlastFurnaceMachine) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.StartCycle> { machine.tick() }
        onEvent<BlastFurnaceVisualEvent.Belt> { machine.loadBelt(ores) }
        onEvent<BlastFurnaceVisualEvent.Pour> { machine.pourBars() }

        WORKER_DIALOGUES.forEach { (symbol, message) ->
            onOpNpc1(symbol) {
                startDialogue(it.npc) { chatNpcNoTurn(neutral, message) }
            }
        }

        val loc = "The dwarves keep the Blast Furnace running. You can load ore onto the conveyor belt."
        MACHINERY_LOCS.forEach { symbol ->
            onOpLoc1(symbol) {
                mes(loc)
            }
        }
    }

    private companion object {
        val WORKER_DIALOGUES =
            linkedMapOf(
                "npc.blast_furnace_automaton_pedaller" to
                    "Stumpy pedals the conveyor belt. The coins in the coffer pay for his wages.",
                "npc.blast_furnace_automaton_pumper" to
                    "Pumpy keeps the pumps running. The coins in the coffer pay for his wages.",
                "npc.blast_furnace_automaton_cog_repairer" to
                    "Thumpy repairs the conveyer cogs. The coins in the coffer pay for his wages.",
                "npc.blast_furnace_automaton_pipe_repairer" to
                    "Numpty repairs the pipes. The coins in the coffer pay for his wages.",
                "npc.blast_furnace_automaton_stoker_empty" to
                    "Dumpy keeps the stove fuelled. The coins in the coffer pay for his wages.",
                "npc.blast_furnace_automaton_stoker_coke" to
                    "Dumpy keeps the stove fuelled. The coins in the coffer pay for his wages.",
            )

        val MACHINERY_LOCS =
            setOf(
                "loc.blast_furnace_pump",
                "loc.blast_furnace_pedals",
                "loc.blast_furnace_stove_low",
                "loc.blast_furnace_stove_medium",
                "loc.blast_furnace_stove_full",
                "loc.blast_furnace_coke",
                "loc.blast_furnace_conveyer_cogs_broken",
                "loc.blast_furnace_conveyer_cogs2_broken",
                "loc.blast_furnace_pipes2_broken",
                "loc.blast_furnace_pipes6_broken",
            )
    }
}
