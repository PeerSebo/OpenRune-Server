package org.rsmod.content.quest.manager

import org.rsmod.game.entity.Player

public object QuestRequirements {
    private const val QuestPointsVarp = "varp.qp"

    @Volatile private var policy: QuestRequirementPolicy = QuestRequirementPolicy()

    public fun install(policy: QuestRequirementPolicy) {
        this.policy = policy
    }

    public fun activePolicy(): QuestRequirementPolicy = policy

    public fun hasCompleted(player: Player, quest: String): Boolean =
        satisfies(player, quest, QuestRequirement.Completed)

    public fun isOnQuest(player: Player, quest: String): Boolean =
        satisfies(player, quest, QuestRequirement.InProgress)

    public fun hasNotCompleted(player: Player, quest: String): Boolean =
        satisfies(player, quest, QuestRequirement.NotCompleted)

    public fun canStart(player: Player, quest: String): Boolean {
        val required = Quest.get(quest)?.requiredQuestPoints ?: return true
        return hasQuestPoints(player, required)
    }

    public fun hasQuestPoints(player: Player, required: Int): Boolean {
        if (required <= 0) {
            return true
        }
        return when (policy.mode) {
            QuestRequirementMode.AssumeCompleted, QuestRequirementMode.Optional -> true
            QuestRequirementMode.RespectProgress -> earnedQuestPoints(player) >= required
            QuestRequirementMode.VirtualCompletions -> effectiveQuestPoints(player) >= required
        }
    }

    public fun earnedQuestPoints(player: Player): Int = player.vars[QuestPointsVarp]

    private fun effectiveQuestPoints(player: Player): Int {
        val active = policy
        val virtual =
            Quest.all().sumOf { quest ->
                val alreadyEarned = quest.questState(player).isCompleted
                if (!alreadyEarned && active.isVirtuallyCompleted(quest.key)) {
                    quest.questPoints
                } else {
                    0
                }
            }
        return earnedQuestPoints(player) + virtual
    }

    public fun satisfies(player: Player, quest: String, requirement: QuestRequirement): Boolean {
        val active = policy
        return when (active.mode) {
            QuestRequirementMode.RespectProgress ->
                realProgressSatisfies(player, quest, requirement)
            QuestRequirementMode.AssumeCompleted -> requirement == QuestRequirement.Completed
            QuestRequirementMode.Optional ->
                requirement == QuestRequirement.Completed || realProgressSatisfies(player, quest, requirement)
            QuestRequirementMode.VirtualCompletions ->
                if (active.isVirtuallyCompleted(quest)) {
                    requirement == QuestRequirement.Completed
                } else {
                    realProgressSatisfies(player, quest, requirement)
                }
        }
    }

    private fun realProgressSatisfies(
        player: Player,
        quest: String,
        requirement: QuestRequirement,
    ): Boolean {
        val state = Quest.get(quest)?.questState(player) ?: QuestProgressState.NOT_STARTED
        return when (requirement) {
            QuestRequirement.Completed -> state.isCompleted
            QuestRequirement.InProgress -> state.isInProgress
            QuestRequirement.NotCompleted -> !state.isCompleted
        }
    }
}
