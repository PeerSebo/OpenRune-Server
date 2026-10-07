package org.rsmod.content.skills.prayer.ecto

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EctoSlimeEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs(SLIME_QUEUE) { processSlimeTick(it.args) }
        onOpLocU("loc.ahoy_new_green_floor", "obj.bucket_empty") { startCollectSlime(it.vis) }
    }

    private fun ProtectedAccess.startCollectSlime(pool: BoundLocInfo) {
        if (skillAnimDelay > mapClock) {
            return
        }
        clearWeakQueue(SLIME_QUEUE)
        if (collectSlime(pool)) {
            weakQueue(SLIME_QUEUE, FILL_TICKS, SlimeTask(pool))
        }
    }

    private fun ProtectedAccess.processSlimeTick(task: SlimeTask) {
        if (!inv.contains("obj.bucket_empty")) {
            resetAnim()
            return
        }
        if (!isWithinDistance(task.pool, 1)) {
            resetAnim()
            return
        }
        if (collectSlime(task.pool)) {
            // Queue callbacks lose one delay tick in the current queue-processing phase.
            weakQueue(SLIME_QUEUE, FILL_TICKS + 1, task)
        }
    }

    private fun ProtectedAccess.collectSlime(pool: BoundLocInfo): Boolean {
        if (invReplace(inv, "obj.bucket_empty", 1, "obj.bucket_ectoplasm").failure) {
            return false
        }
        faceSquare(pool.coords)
        skillAnimDelay = mapClock + FILL_TICKS
        anim("seq.quest_ahoy_human_filling_bucket")
        // This sequence has no baked audio in the native cache.
        soundSynth("synth.FILL_ECTOPLASM")
        mes("You fill the bucket from the pool of slime.")
        return true
    }

    private data class SlimeTask(val pool: BoundLocInfo)

    private companion object {
        const val SLIME_QUEUE = "queue.prayer_ecto_collect_slime"
        const val FILL_TICKS = 3
    }
}
