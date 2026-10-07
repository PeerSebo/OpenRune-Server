package org.rsmod.content.quest.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.QuestRow
import org.rsmod.game.entity.Player

internal object QuestJournalState {
    private val selected = QuestVar.integer(
        name = "active_quest_journal",
        varp = "varp.active_quest_journal",
        legacyKey = "active_quest_journal",
        migratedVarp = "varp.active_quest_journal_migrated",
        legacyValue = { legacy ->
            (legacy as? String)?.let(::legacyQuestId)?.plus(1)
        },
    )

    private val questIdsByKey by lazy {
        QuestRow.all().associate { row ->
            val key = RSCM.getReverseMapping(RSCMType.DBROW, row.rowId)
                .removePrefix("dbrow.").normalizedQuestKey()
            key to row.id
        }
    }

    fun select(player: Player, quest: Quest) {
        require(quest.id in 0 until Int.MAX_VALUE)
        selected.set(player, quest.id + 1)
    }

    fun activeQuest(player: Player): Quest? {
        val id = selected.get(player)
        return if (id > 0) Quest.getById(id - 1) else null
    }

    fun clear(player: Player) = selected.clear(player)

    fun migrate(player: Player) = selected.migrate(player)

    fun assertResolves() {
        selected.assertResolves()
        val ids = QuestRow.all().map(QuestRow::id)
        check(ids.all { it in 0 until Int.MAX_VALUE })
        check(ids.distinct().size == ids.size) { "Quest journal ids must be unique." }
    }

    private fun legacyQuestId(key: String): Int? =
        Quest.get(key)?.id ?: questIdsByKey[key.normalizedQuestKey()]
}
