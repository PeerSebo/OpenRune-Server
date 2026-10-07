package org.rsmod.content.quest.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

enum class JournalState {
    OVERVIEW,
    LOG,
}

enum class QuestProgressState {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED;

    val isNotStarted: Boolean
        get() = this == NOT_STARTED

    val isInProgress: Boolean
        get() = this == IN_PROGRESS

    val isCompleted: Boolean
        get() = this == FINISHED

    companion object {
        fun fromStage(stage: Int, endState: Int): QuestProgressState =
            when {
                stage <= 0 -> NOT_STARTED
                stage >= endState -> FINISHED
                else -> IN_PROGRESS
            }
    }
}

@DslMarker annotation class QuestJournalDsl

data class QuestReward(
    val xp: Map<String, Double> = emptyMap(),
    val items: List<QuestRewardItem> = emptyList(),
    val extraLines: List<String> = emptyList(),
)

data class QuestRewardItem(val item: String, val amount: Int, val label: String? = null)

@QuestJournalDsl
fun rewards(builder: QuestRewardBuilder.() -> Unit): QuestReward {
    return QuestRewardBuilder().apply(builder).build()
}

@QuestJournalDsl
class QuestRewardBuilder {
    private val _xp = mutableMapOf<String, Double>()
    private val _items = mutableListOf<QuestRewardItem>()
    private val _extraLines = mutableListOf<String>()

    fun xp(skill: String, amount: Double) {
        _xp[skill] = amount
    }

    fun item(id: String, amount: Int = 1, label: String? = null) {
        _items.add(QuestRewardItem(id, amount, label))
    }

    fun extra(text: String) {
        _extraLines += text
    }

    fun build(): QuestReward = QuestReward(_xp, _items, _extraLines)
}

// Supply questVarbit when the backing varp contains fields besides the stage.
abstract class QuestScript(
    val questKey: String,
    val questVarp: String,
    val rewards: QuestReward,
    val completedQuestItemDisplay: ItemRewardDisplay? = null,
    val questVarbit: String? = null,
    val completionJingle: Int? = null,
    val completionJingleLength: Int = 0,
) : PluginScript() {

    val quest =
        Quest.register(
            questKey,
            questVarp,
            completedQuestItemDisplay,
            rewards,
            questVarbit,
            completionJingle,
            completionJingleLength,
        )

    abstract fun subTitle(): String

    abstract fun questLog(player: ProtectedAccess): String

    abstract fun completedLog(player: ProtectedAccess): String

    abstract fun ScriptContext.init()

    override fun ScriptContext.startup() {
        RSCM.requireRSCM(RSCMType.DBROW, "dbrow.${questKey}")

        val stageVarbit = questVarbit
        if (stageVarbit != null) {
            RSCM.requireRSCM(RSCMType.VARBIT, stageVarbit)
        }
        quest.assertStageVarResolves()
        quest.assertFieldsResolve()

        QuestJournalRegistry.register(
            quest,
            QuestJournalContent(
                subTitle = ::subTitle,
                questLog = ::questLog,
                completedLog = ::completedLog,
            ),
        )

        onPlayerLogin {
            quest.migrateFields(player)
            quest.syncStageVar(player)
        }

        this.init()
    }

    protected fun questJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit,
    ): String = buildQuestJournal(player, quest, builder)

    protected fun completionJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit,
    ): String = buildCompletionJournal(player, quest, builder)
}
