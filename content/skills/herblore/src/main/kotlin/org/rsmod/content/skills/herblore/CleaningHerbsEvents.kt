package org.rsmod.content.skills.herblore

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.herblore.HerbloreCleaningRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CleaningHerbsEvents : PluginScript() {

    override fun ScriptContext.startup() {
        HerbloreDefinitions.cleaningHerbs.forEach { row ->
            onOpHeld1(row.input) { startCleanHerb(row, it.slot) }
        }
        onPlayerQueueWithArgs<CleanHerbTask>("queue.herblore_clean") { processCleanHerbTick(it.args) }
    }

    private suspend fun ProtectedAccess.startCleanHerb(row: HerbloreCleaningRow, slot: Int) {
        if (!meetsStatReqs(row.statReq)) {
            return
        }

        if (inv[slot]?.id != row.input.id) {
            return
        }

        weakQueue("queue.herblore_clean", 1, CleanHerbTask(row, slot))
    }

    private suspend fun ProtectedAccess.processCleanHerbTick(task: CleanHerbTask) {
        val row = task.row

        if (!meetsStatReqs(row.statReq)) {
            return
        }

        if (inv[task.slot]?.id != row.input.id) {
            return
        }

        if (invReplaceSlot(inv, task.slot, 1, row.output).failure) {
            return
        }

        soundSynth(random.pick(CLEAN_SOUNDS))
        mes("You clean the ${row.input.name}.")

        if (row.xp > 0) {
            statAdvance("stat.herblore", row.xp.toDouble())
        }
    }

    private data class CleanHerbTask(val row: HerbloreCleaningRow, val slot: Int)

    private companion object {
        private val CLEAN_SOUNDS =
            listOf(
                "synth.HERBLORE_CLEAN_HERB_01",
                "synth.HERBLORE_CLEAN_HERB_02",
                "synth.HERBLORE_CLEAN_HERB_03",
                "synth.HERBLORE_CLEAN_HERB_04",
            )
    }
}
