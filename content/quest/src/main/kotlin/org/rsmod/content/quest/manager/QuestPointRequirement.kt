package org.rsmod.content.quest.manager

import org.rsmod.api.player.protect.ProtectedAccess

public suspend fun ProtectedAccess.checkQuestPointRequirement(quest: Quest): Boolean {
    val required = quest.requiredQuestPoints
    if (QuestRequirements.hasQuestPoints(player, required)) {
        return true
    }
    val label = if (required == 1) "Quest Point" else "Quest Points"
    mesbox("You need $required $label to start this quest.")
    return false
}
