package org.rsmod.content.skills.sailing.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.content.skills.sailing.PortTaskManager
import org.rsmod.content.skills.sailing.portTaskOpenedSlot
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class SailingLogScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld3("obj.sailing_log") { openLog(tasks = false) }
        onOpHeld4("obj.sailing_log") { openLog(tasks = true) }
        onOpHeld4("obj.sailing_log_initial") { openLog(tasks = true) }
        onIfModalButton(CLOSE) { ifClose() }
        onIfModalButton(TASKS) {
            if (it.op == IfButtonOp.Op1 && it.comsub in 0 until PortTaskManager.unlockedSlots(player)) {
                player.portTaskOpenedSlot = it.comsub
            }
        }
        onIfModalButton(BACK) {
            player.portTaskOpenedSlot = NO_SELECTION
            runClientScript("clientscript.sailing_log_hide_popup".asRSCM())
        }
        onIfModalButton(CANCEL) { if (it.op == IfButtonOp.Op1) cancelTask() }
        onIfClose(INTERFACE) { player.portTaskOpenedSlot = NO_SELECTION }
    }

    private fun ProtectedAccess.openLog(tasks: Boolean) {
        PortTaskManager.syncSlotCount(player)
        player.writeSailingVar("varbit.sailing_log_tab_opened", if (tasks) 1 else 0)
        player.portTaskOpenedSlot = NO_SELECTION
        ifOpenMainModal(INTERFACE)
        ifSetEvents(CLOSE, -1..-1, IfEvent.Op1)
        ifSetEvents(BACK, -1..-1, IfEvent.Op1)
        ifSetEvents(CANCEL, -1..-1, IfEvent.Op1)
        ifSetEvents(TASKS, 0 until PortTaskManager.MAX_TASK_SLOTS, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.cancelTask() {
        val slot = player.portTaskOpenedSlot
        if (player.ui.closeModal || !player.ui.containsModal(INTERFACE)) return
        if (slot !in 0 until PortTaskManager.unlockedSlots(player)) return
        val task = PortTaskManager.slotTask(player, slot) ?: return
        if (!PortTaskManager.isCancellable(player, slot)) {
            mes("You cannot cancel this task.")
            return
        }
        if (PortTaskManager.cargoTaken(player, slot) > PortTaskManager.cargoDelivered(player, slot)) {
            mes("Deliver your collected cargo before cancelling this task.")
            return
        }
        ifClose()
        if (!choice2("Cancel this task.", true, "Keep this task.", false)) return
        if (PortTaskManager.slotTaskId(player, slot) != task.taskId ||
            !PortTaskManager.isCancellable(player, slot) || PortTaskManager.cargoTaken(player, slot) != 0) return
        PortTaskManager.cancel(player, slot)
        mes("You cancel the task: ${task.name}.")
        openLog(tasks = true)
    }

    private companion object {
        const val INTERFACE = "interface.sailing_log"
        const val CLOSE = "component.sailing_log:close_button"
        const val TASKS = "component.sailing_log:tasks_click"
        const val BACK = "component.sailing_log:back"
        const val CANCEL = "component.sailing_log:cancel"
        const val NO_SELECTION = PortTaskManager.MAX_TASK_SLOTS
    }
}
