package org.rsmod.content.skills.slayer

import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.content.skills.slayer.core.MortimerAssignment
import org.rsmod.content.skills.slayer.core.SlayerTaskManager

object SlayerTaskChoiceInterface {

    const val INTERFACE = "interface.slayer_task_choice"
    const val CONTENT = "component.slayer_task_choice:content"
    const val CLOSE = "component.slayer_task_choice:close_button"
    const val UNIVERSE = "component.slayer_task_choice:universe"

    // Requires protected access and PauseButton events or the client will not send a selection.
    suspend fun openAndAwait(access: ProtectedAccess) {
        access.ifOpenMainModal(INTERFACE)
        enableClickEvents(access)

        val input = access.pauseButton()
        handleInput(access, input)
    }

    fun open(access: ProtectedAccess) {
        access.ifOpenMainModal(INTERFACE)
        enableClickEvents(access)
    }

    fun onContentClick(access: ProtectedAccess, comsub: Int) {
        acceptSlot(access, comsub)
    }

    fun onClose(access: ProtectedAccess) {
        access.ifClose()
    }

    private fun enableClickEvents(access: ProtectedAccess) {
        access.player.ifSetEvents(CONTENT, 0..16, IfEvent.Op1)
    }

    private fun handleInput(access: ProtectedAccess, input: ResumePauseButtonInput) {
        when {
            input.isComponentType(CLOSE) -> access.ifClose()
            input.isComponentType(CONTENT) || input.isComponentType(UNIVERSE) -> acceptSlot(access, input.subcomponent)
            else -> {}
        }
    }

    private fun acceptSlot(access: ProtectedAccess, comsub: Int) {
        if (!MortimerAssignment.hasPendingChoice(access)) {
            access.ifClose()
            return
        }
        val master = SlayerTaskManager.findMasterByNpc(MortimerAssignment.NPC) ?: return
        val offers = MortimerAssignment.readStoredOffers(access, master)
        if (offers.isEmpty()) {
            MortimerAssignment.clearOffers(access)
            access.ifClose()
            return
        }
        val slot = resolveSlot(comsub, offers.size) ?: return
        val offer = MortimerAssignment.acceptSlot(access, slot) ?: return
        access.ifClose()
        access.mes(
            "Your new task is to kill ${offer.amount} ${offer.masterTask.task.nameUppercase}.",
        )
    }

    private fun resolveSlot(comsub: Int, offerCount: Int): Int? =
        when {
            comsub in 0 until offerCount -> comsub
            comsub in 1..offerCount -> comsub - 1
            else -> null
        }
}
