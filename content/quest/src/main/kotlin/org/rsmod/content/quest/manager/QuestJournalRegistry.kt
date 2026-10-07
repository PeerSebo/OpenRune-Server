package org.rsmod.content.quest.manager

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import toRs

data class QuestJournalContent(
    val subTitle: () -> String,
    val questLog: (ProtectedAccess) -> String,
    val completedLog: (ProtectedAccess) -> String,
)

object QuestJournalRegistry {
    private val journals = mutableMapOf<String, QuestJournalContent>()
    private val statusJournals = mutableMapOf<Int, (ProtectedAccess) -> Unit>()

    fun registerStatus(rowId: Int, open: (ProtectedAccess) -> Unit) {
        check(statusJournals.putIfAbsent(rowId, open) == null) {
            "Status journal $rowId is already registered."
        }
    }

    fun openStatus(access: ProtectedAccess, rowId: Int): Boolean {
        val open = statusJournals[rowId] ?: return false
        QuestJournalState.clear(access.player)
        open(access)
        return true
    }
    fun register(quest: Quest, content: QuestJournalContent) {
        journals[quest.key.normalizedQuestKey()] = content
    }

    fun get(quest: Quest): QuestJournalContent? = journals[quest.key.normalizedQuestKey()]

    fun getById(id: Int): QuestJournalContent? {
        val quest = Quest.getById(id) ?: return null
        return get(quest)
    }

    fun openJournal(access: ProtectedAccess, quest: Quest, type: JournalState) {
        val content = get(quest) ?: return
        QuestJournalState.select(access.player, quest)
        when (type) {
            JournalState.OVERVIEW -> openJournalOverview(access, quest, content)
            JournalState.LOG -> openQuestLog(access, quest, content)
        }
    }

    fun activeQuest(player: Player): Quest? = QuestJournalState.activeQuest(player)

    private fun openJournalOverview(
        access: ProtectedAccess,
        quest: Quest,
        content: QuestJournalContent,
    ) {
        access.ifOpenOverlay("interface.questjournal_overview")
        access.ifSetText(
            "component.questjournal_overview:title",
            "<col=7f0000>${quest.displayName}</col>",
        )

        access.runClientScript(
            "clientscript.[clientscript,questjournal_overview_setup]",
            quest.rowID,
            content.subTitle(),
            RSCM.getRSCM("component.questjournal_overview:universe"),
            RSCM.getRSCM("component.questjournal_overview:content_inner"),
            RSCM.getRSCM("component.questjournal_overview:content_outer"),
            RSCM.getRSCM("component.questjournal_overview:scrollbar"),
            RSCM.getRSCM("component.questjournal_overview:inner"),
            RSCM.getRSCM("component.questjournal_overview:container"),
            RSCM.getRSCM("component.questjournal_overview:scroll"),
            access.player.combatLevel,
        )

        // Opening an interface discards old event ranges; arm the valid buttons after each open.
        access.ifSetEvents("component.questjournal_overview:content_inner", 1..1, IfEvent.Op1)
    }

    private fun openQuestLog(access: ProtectedAccess, quest: Quest, content: QuestJournalContent) {
        val lines =
            (if (quest.isQuestCompleted(access.player)) content.completedLog(access)
                else content.questLog(access))
                .lines()
                .flatMap { it.toRs(inheritPreviousTags = true, wrapAt = 64).split("<br>") }
                .take(MAX_JOURNAL_LINES)

        access.ifSetText("component.questjournal:title", "<col=7f0000>${quest.displayName}</col>")
        access.runClientScript(QUEST_JOURNAL_RESET_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT))
        lines.forEachIndexed { index, line ->
            access.ifSetText("component.questjournal:qj${index + 1}", line)
        }
        access.ifOpenOverlay("interface.questjournal")
        access.runClientScript(
            QUEST_JOURNAL_SETUP_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
            0,
            lines.size,
        )
    }

    private const val QUEST_JOURNAL_RESET_SCRIPT =
        "clientscript.[clientscript,quest_journal_reset]"

    private const val QUEST_JOURNAL_SETUP_SCRIPT = "clientscript.[clientscript,script2523]"

    private const val MAX_JOURNAL_LINES = 210
}
