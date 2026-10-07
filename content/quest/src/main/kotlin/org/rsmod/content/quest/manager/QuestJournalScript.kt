package org.rsmod.content.quest.manager

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuestJournalScript : PluginScript() {
    override fun ScriptContext.startup() {
        QuestJournalState.assertResolves()
        onPlayerLogin { QuestJournalState.migrate(player) }

        onIfOverlayButton("component.questlist:list") { evt ->
            if (QuestJournalRegistry.openStatus(this, evt.comsub)) return@onIfOverlayButton
            val quest = Quest.getById(evt.comsub) ?: return@onIfOverlayButton
            if (QuestJournalRegistry.get(quest) == null) return@onIfOverlayButton

            val journalState =
                if (quest.questState(player) == QuestProgressState.NOT_STARTED) {
                    JournalState.OVERVIEW
                } else {
                    JournalState.LOG
                }

            QuestJournalRegistry.openJournal(this, quest, journalState)
        }

        onIfModalButton("component.questscroll:close_button") { player.ifCloseModals(eventBus) }

        // Quest journals use an overlay; modal-only button handlers would never receive their clicks.
        onIfOverlayButton("component.questjournal_overview:close") {
            player.ifCloseOverlay("interface.questjournal_overview", eventBus)
        }

        onIfOverlayButton("component.questjournal:close") {
            player.ifCloseOverlay("interface.questjournal", eventBus)
        }

        onIfOverlayButton("component.questjournal_overview:content_inner") {
            val quest = QuestJournalRegistry.activeQuest(player) ?: return@onIfOverlayButton

            player.ifOpenOverlay("interface.worldmap", eventBus)
            player.ifSetEvents(
                "component.worldmap:close",
                0..1,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
            )

            quest.startCoord?.let { coord ->
                quest.mapElement?.let { element ->
                    player.runClientScript(
                        "clientscript.[clientscript,script3331]",
                        RSCM.getRSCM("component.worldmap:map_noclick"),
                        coord.packed,
                        element,
                    )
                }
            }
        }

        onIfOverlayButton("component.questjournal:switch") {
            val quest = QuestJournalRegistry.activeQuest(player) ?: return@onIfOverlayButton
            QuestJournalRegistry.openJournal(this, quest, JournalState.OVERVIEW)
        }

        onIfOverlayButton("component.questjournal_overview:switch") {
            val quest = QuestJournalRegistry.activeQuest(player) ?: return@onIfOverlayButton
            QuestJournalRegistry.openJournal(this, quest, JournalState.LOG)
        }
    }
}
