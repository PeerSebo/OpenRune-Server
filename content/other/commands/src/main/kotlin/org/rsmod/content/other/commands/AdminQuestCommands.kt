package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AdminQuestCommands @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private val logger = InlineLogger()

    private var Player.questPoints by intVarp("varp.qp")
    private var Player.questsCompleted by intVarBit("varbit.quests_completed_count")

    override fun ScriptContext.startup() {
        onCommand("questreset", "Reset a quest to not-started", ::questReset) {
            invalidArgs = ResetUsage
        }
        onCommand("resetquest", "Reset a quest to not-started", ::questReset) {
            invalidArgs = ResetUsage
        }
        onCommand(
            "questcomplete",
            "Complete a quest through the quest framework",
            ::questComplete,
        ) {
            invalidArgs = CompleteUsage
        }
        onCommand(
            "completequest",
            "Complete a quest through the quest framework",
            ::questComplete,
        ) {
            invalidArgs = CompleteUsage
        }
        onCommand("setqp", "Set quest points", ::setQuestPoints) { invalidArgs = QuestPointUsage }
        onCommand("qp", "Set quest points", ::setQuestPoints) { invalidArgs = QuestPointUsage }
    }

    private fun questReset(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes("Registered quests: " + Quest.all().joinToString(", ") { it.key.removePrefix("quest_") })
                return@with
            }
            val quest = resolveQuest(ResetUsage) ?: return@with

            val stage = quest.getQuestStage(player)
            val wasCompleted = quest.isQuestCompleted(player)
            val previousQuestPoints = player.questPoints
            val launched =
                protectedAccess.launch(player, busyText = BusyText) {
                    quest.resetQuest(player)
                }
            if (!launched) {
                return@with
            }

            if (wasCompleted) {
                // Roll back rewards before reset so reset/complete cycles cannot inflate quest points or completion count.
                player.mes(
                    "Reset '${quest.key}' (${quest.displayName}) from stage $stage to 0. " +
                        "Quest points: $previousQuestPoints -> ${player.questPoints}."
                )
            } else {
                player.mes("Reset '${quest.key}' (${quest.displayName}) from stage $stage to 0.")
            }
            player.mes(
                "Registered quest attributes were cleared. Separate world vars, reward items and xp are unchanged."
            )
            logger.info {
                "Quest reset by '${player.displayName}': key=${quest.key}, stage=$stage -> 0."
            }
        }

    private fun questComplete(cheat: Cheat) =
        with(cheat) {
            val quest = resolveQuest(CompleteUsage) ?: return@with

            if (quest.isQuestCompleted(player)) {
                player.mes(
                    "'${quest.key}' (${quest.displayName}) is already completed. " +
                        "Use ::questreset ${quest.key} first to replay it."
                )
                return@with
            }

            val stage = quest.getQuestStage(player)
            val launched =
                protectedAccess.launch(player, busyText = BusyText) {
                    quest.completeQuest(this)
                }
            if (!launched) {
                return@with
            }

            player.mes(
                "Completed '${quest.key}' (${quest.displayName}) from stage $stage " +
                    "(+${quest.questPoints} quest points)."
            )
            logger.info {
                "Quest completed by '${player.displayName}': key=${quest.key}, stage=$stage."
            }
        }

    private fun setQuestPoints(cheat: Cheat) =
        with(cheat) {
            val raw = args.getOrNull(0)
            if (raw == null) {
                player.mes(QuestPointUsage)
                player.mes("Current quest points: ${player.questPoints}")
                return@with
            }
            val value = raw.toIntOrNull()
            if (value == null || value !in 0..MaxQuestPoints) {
                player.mes("Quest points must be an integer from 0 to $MaxQuestPoints.")
                return@with
            }
            val before = player.questPoints
            player.questPoints = value

            player.mes("Quest points: $before -> $value (individual quest stages unchanged).")
            logger.info { "Quest points set by '${player.displayName}': $before -> $value." }
        }

    private fun Cheat.resolveQuest(usage: String): Quest? {
        val key = args.getOrNull(0)?.trim()
        if (key.isNullOrEmpty()) {
            player.mes(usage)
            listQuestKeys()
            return null
        }
        val quest = Quest.get(key)
        if (quest == null) {
            player.mes("No quest found with key: '$key'")
            listQuestKeys()
            return null
        }
        return quest
    }

    private fun Cheat.listQuestKeys() {
        val keys = Quest.all().map { it.key }.sorted()
        if (keys.isEmpty()) {
            player.mes("No quests are registered.")
            return
        }
        player.mes("Known quest keys (${keys.size}):")
        for (chunk in keys.chunked(KeysPerLine)) {
            player.mes("  ${chunk.joinToString(", ")}")
        }
    }

    private companion object {
        private const val ResetUsage =
            "Use as ::questreset questKey (ex: ::questreset quest_goblindiplomacy). " +
                "No argument lists every key."

        private const val CompleteUsage =
            "Use as ::questcomplete questKey (ex: ::questcomplete quest_goblindiplomacy). " +
                "No argument lists every key."

        private const val QuestPointUsage = "Use as ::setqp amount (ex: ::setqp 12)"

        private const val BusyText = "You can't do that right now."

        private const val MaxQuestPoints = 10_000

        private const val KeysPerLine = 4
    }
}
