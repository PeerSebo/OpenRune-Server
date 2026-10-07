package org.rsmod.content.other.achievementdiaries.pack

import dev.openrune.pack.PluginPack

class AchievementDiaryPluginPack : PluginPack() {
    override fun dbTables() = listOf(achievementDiaryTasks(), achievementDiaryTiers())
}
