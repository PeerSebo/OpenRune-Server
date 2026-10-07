package org.rsmod.content.interfaces.journal.tab.scripts

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class AdventurePathScript @Inject constructor(private val eventBus: EventBus) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.adventurepath_side:toggles") {
            player.openAdventurePathModal()
        }

        onIfOverlayButton("component.adventurepath_side:open_interface") {
            player.openAdventurePathModal()
        }

        onIfOverlayButton("component.adventurepath_side:task_name") {
            player.openAdventurePathModal()
        }

        onIfOverlayButton("component.adventurepath_side:path_name") {
            player.openAdventurePathModal()
        }

        onIfOverlayButton("component.adventurepath_side:task_description") {
            player.mainInterfaceTab = 2
            player.selectedPath = 0
            player.openAdventurePathModal()
        }

        // The spawned parent and active multinpc leaf both need operation bindings.
        onOpNpc4("npc.ap_guide_parent") { player.openAdventurePathModal() }
        onOpNpc4("npc.ap_guide_active") { player.openAdventurePathModal() }

        for (jon in listOf("npc.ap_guide_parent", "npc.ap_guide_active")) {
            onOpNpc1(jon) { talkToJon(it.npc) }
            onOpNpc3(jon) { claimStarterKit() }
        }

        onIfModalButton("component.adventurepath:paths") {
            player.selectPath(it.comsub)
        }

        onIfModalButton("component.adventurepath:back") { player.mainInterfaceTab = TAB_PATH_LIST }
        onIfModalButton("component.adventurepath:extra_back_button") {
            player.mainInterfaceTab = TAB_PATH_LIST
        }
    }

    private fun Player.openAdventurePathModal() {
        ifOpenMainModal("interface.adventurepath", eventBus)

        for (n in 0 until TASK_ROWS) {
            val sub = TASK_FIRST + (n * TASK_STRIDE)
            ifSetEvents("component.adventurepath:tasks", sub..sub, IfEvent.Op1)
        }

        for (n in 0 until PATH_ROWS) {
            val base = n * PATH_STRIDE
            ifSetEvents("component.adventurepath:paths", base..base, IfEvent.Op1)
            val select = base + PATH_SELECT_OFFSET
            ifSetEvents("component.adventurepath:paths", select..select, IfEvent.Op1)
            val extra = base + PATH_SHOW_OFFSET
            ifSetEvents("component.adventurepath:paths", extra..extra, IfEvent.Op4)
        }

        ifSetEvents("component.adventurepath:task_focus", 9..10, IfEvent.Op1)
        ifSetEvents("component.adventurepath:back", 9..9, IfEvent.Op1)
        ifSetEvents("component.adventurepath:extra_back_button", 9..9, IfEvent.Op1)
        ifSetEvents("component.adventurepath:task_hint", 1..2, IfEvent.Op1)
        ifSetEvents("component.adventurepath:path_reward", 9..9, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.talkToJon(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        chatNpcNoTurn(
            happy,
            "Hello there, adventurer! I'm Jon - I can point you towards an Adventure Path if " +
                "you're looking for something new to try.",
        )
    }

    private suspend fun ProtectedAccess.claimStarterKit() {
        mes("Jon checks his ledger, but doesn't have anything to give you right now.")
    }

    // Task selection is client-owned and uses global task IDs, not visible row indices.
    private val taskSelectionIsClientOwned: Unit = Unit

    private fun Player.selectPath(comsub: Int) {
        if (comsub % PATH_STRIDE != PATH_SELECT_OFFSET) return
        val path = comsub / PATH_STRIDE
        if (path >= PATH_ROWS) return
        if (mainInterfaceTab != TAB_PATH_DETAIL) {
            mainInterfaceTab = TAB_PATH_DETAIL
        }
    }

    private companion object {
        private const val TASK_FIRST = 11
        private const val TASK_STRIDE = 12
        private const val TASK_ROWS = 15

        private const val PATH_STRIDE = 39
        private const val PATH_ROWS = 5
        private const val PATH_ROW_OFFSET = 0
        private const val PATH_SELECT_OFFSET = 10
        private const val PATH_SHOW_OFFSET = 38

        private const val TAB_PATH_LIST = 0
        private const val TAB_PATH_DETAIL = 1

        private var Player.selectedTask by intVarp("varp.adventurepath_selected_task")

        private var Player.selectedPath by intVarp("varp.adventurepath_selected_path")

        private var Player.mainInterfaceTab by intVarBit("varbit.adventurepath_main_if_tab")
    }
}
