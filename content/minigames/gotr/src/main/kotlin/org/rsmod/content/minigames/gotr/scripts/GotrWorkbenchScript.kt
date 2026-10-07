package org.rsmod.content.minigames.gotr.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// One conversion per tick; restart the chisel animation per item and reset one tick after the last.
// Initial op scheduling needs no +1; queue-handler rescheduling does (docs/skills.md §2).
class GotrWorkbenchScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(WORKBENCH) { workAt() }
        onPlayerQueueWithArgs<Unit>(QUEUE) { continueWorking() }
    }

    private suspend fun ProtectedAccess.workAt() {
        if (inv.count(FRAGMENT) <= 0) {
            mesbox("You'll need at least one guardian fragment to craft guardian essence.")
            return
        }
        clearWeakQueue(QUEUE)
        anim(CHISEL_ANIM)
        weakQueue(QUEUE, CADENCE_TICKS, Unit)
    }

    private suspend fun ProtectedAccess.continueWorking() {
        if (inv.count(FRAGMENT) <= 0) {
            resetAnim()
            mes(NO_FRAGMENTS_MSG)
            return
        }

        // Fragments stack but essence does not; roll back if the output needs an unavailable slot.
        if (invDel(inv, FRAGMENT, 1).failure) {
            resetAnim()
            return
        }
        if (invAdd(inv, ESSENCE, 1).failure) {
            invAdd(inv, FRAGMENT, 1)
            resetAnim()
            mesbox("Your inventory is too full to hold any more essence.")
            return
        }
        player.statAdvance("stat.crafting", craftingXpPerItem(player))

        anim(CHISEL_ANIM)
        requeue()
    }

    private fun ProtectedAccess.requeue() {
        weakQueue(QUEUE, CADENCE_TICKS + 1, Unit)
    }

    private fun craftingXpPerItem(player: Player): Double =
        (player.craftingLvl / 10).coerceIn(1, 5).toDouble()

    private companion object {
        const val WORKBENCH = "loc.gotr_workbench"
        const val FRAGMENT = "obj.gotr_guardian_fragment"
        const val ESSENCE = "obj.gotr_guardian_essence"
        const val CHISEL_ANIM = "seq.gotr_chisel_essence"
        const val QUEUE = "queue.gotr_workbench_craft"
        const val CADENCE_TICKS = 1
        const val NO_FRAGMENTS_MSG = "You have no more guardian fragments to combine."
    }
}
