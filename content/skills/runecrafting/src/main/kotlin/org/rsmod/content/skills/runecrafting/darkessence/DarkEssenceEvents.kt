package org.rsmod.content.skills.runecrafting.darkessence

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DarkEssenceEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocU("loc.archeus_altar_dark", DENSE_ESSENCE_BLOCK) { convertAtDarkAltar() }
        onOpHeldU(CHISEL, DARK_ESSENCE_BLOCK) { chiselDarkEssenceBlock() }
        onPlayerQueueWithArgs<ChiselTask>(CHISEL_QUEUE) { processChisel(it.args) }
    }

    private suspend fun ProtectedAccess.convertAtDarkAltar() {
        val count = inv.count(DENSE_ESSENCE_BLOCK)
        if (count <= 0) {
            return
        }

        if (invDel(inv, DENSE_ESSENCE_BLOCK, count).failure) {
            return
        }
        invAdd(inv, DARK_ESSENCE_BLOCK, count)
        statAdvance(
            "stat.runecrafting",
            count * DARK_ALTAR_XP_PER_BLOCK * xpMods.get(player, "stat.runecrafting"),
        )
        statSub("stat.prayer", constant = DARK_ALTAR_PRAYER_DRAIN, percent = 0)
        delay(DARK_ALTAR_TICKS)
        mes("The Dark Altar converts your dense essence blocks into dark essence blocks.")
    }

    private suspend fun ProtectedAccess.chiselDarkEssenceBlock() {
        val count = inv.count(DARK_ESSENCE_BLOCK)
        if (count <= 0) {
            return
        }

        if (player.craftingLvl < CHISEL_CRAFTING_LEVEL) {
            mesbox("You need a Crafting level of $CHISEL_CRAFTING_LEVEL.")
            return
        }

        if (inv.freeSpace() < FRAGMENTS_PER_BLOCK - 1) {
            mes(Constants.dm_invspace)
            return
        }

        // Replace the existing chain when another batch is confirmed.
        clearWeakQueue(CHISEL_QUEUE)

        anim(CHISEL_ANIM)
        soundSynth(CHISEL_SOUND)
        chiselQueue(ChiselTask(totalRequested = count, converted = 0))
    }

    private suspend fun ProtectedAccess.processChisel(task: ChiselTask) {
        val stillHeld = inv.count(DARK_ESSENCE_BLOCK) > 0
        val canAfford = inv.freeSpace() >= FRAGMENTS_PER_BLOCK - 1
        if (task.converted >= task.totalRequested || !stillHeld || !canAfford) {
            val reason =
                when {
                    !canAfford -> ChiselStopReason.NoSpace
                    !stillHeld -> ChiselStopReason.NoBlocksLeft
                    else -> ChiselStopReason.Completed
                }
            finishChisel(task, reason)
            return
        }

        if (invDel(inv, DARK_ESSENCE_BLOCK, 1).failure) {
            finishChisel(task, ChiselStopReason.NoBlocksLeft)
            return
        }
        invAdd(inv, DARK_ESSENCE_FRAGMENT, FRAGMENTS_PER_BLOCK)
        statAdvance(CRAFTING_STAT, FRAGMENT_XP * xpMods.get(player, CRAFTING_STAT))

        val next = task.copy(converted = task.converted + 1)
        val moreToDo =
            next.converted < next.totalRequested &&
                inv.count(DARK_ESSENCE_BLOCK) > 0 &&
                inv.freeSpace() >= FRAGMENTS_PER_BLOCK - 1
        if (moreToDo) {
            anim(CHISEL_ANIM)
            soundSynth(CHISEL_SOUND)
            chiselQueue(next)
        } else {
            finishChisel(next, ChiselStopReason.Completed)
        }
    }

    private suspend fun ProtectedAccess.finishChisel(task: ChiselTask, reason: ChiselStopReason) {
        resetAnim()
        if (task.converted == 0) {
            when (reason) {
                ChiselStopReason.NoSpace -> mes(Constants.dm_invspace)
                ChiselStopReason.NoBlocksLeft ->
                    mesbox("You don't have any dark essence blocks left to chisel.")
                ChiselStopReason.Completed -> Unit
            }
            return
        }
        val noun = if (task.converted == 1) "block" else "blocks"
        mes("You chisel the dark essence $noun into fragments.")
    }

    private enum class ChiselStopReason {
        Completed,
        NoSpace,
        NoBlocksLeft,
    }

    // Queue/callback additions decrement immediately; compensate scheduling with +1.
    private fun ProtectedAccess.chiselQueue(task: ChiselTask) {
        weakQueue(CHISEL_QUEUE, CHISEL_TICKS + 1, task)
    }

    private data class ChiselTask(val totalRequested: Int, val converted: Int)

    private companion object {
        const val DENSE_ESSENCE_BLOCK = "obj.arceuus_essence_block"
        const val DARK_ESSENCE_BLOCK = "obj.arceuus_essence_block_dark"
        const val DARK_ESSENCE_FRAGMENT = "obj.bigblankrune"
        const val CHISEL = "obj.chisel"

        const val CHISEL_CRAFTING_LEVEL = 38

        const val CRAFTING_STAT = "stat.crafting"

        const val FRAGMENTS_PER_BLOCK = 4

        const val FRAGMENT_XP = 8.0

        const val CHISEL_TICKS = 3

        const val CHISEL_ANIM = "seq.arceuus_chisel_essenceblock"
        const val CHISEL_SOUND = "synth.CHISEL"
        const val CHISEL_QUEUE = "queue.rcu_dark_essence_chisel"

        const val DARK_ALTAR_XP_PER_BLOCK = 2.5

        const val DARK_ALTAR_PRAYER_DRAIN = 1

        const val DARK_ALTAR_TICKS = 3
    }
}
