package org.rsmod.content.drops.toml

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.QuestRequirementsYaml
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class QuestDropConditionTest {
    private val originalPolicy = QuestRequirements.activePolicy()

    @AfterEach fun restorePolicy() = QuestRequirements.install(originalPolicy)

    @Test fun `optional loot replacements never also award their locked alternative`() {
        val resolver = resolver("optional")
        val player = Player()
        for (quest in listOf("quest_monkeymadness2", "quest_observatoryquest")) {
            assertTrue(resolver.dropCondition(quest, "completed")(player))
            assertFalse(resolver.dropCondition(quest, "not_completed")(player))
            assertFalse(resolver.dropCondition(quest, "during")(player))
        }
        assertNull(player.attr[QUEST_STAGE_MAP_ATTR])
    }

    @Test fun `optional objective drops require actual progress and stop at completion`() {
        val quest = registerQuest()
        val resolver = resolver("optional")
        for (stage in listOf(0, 1, quest.maxSteps)) {
            val player = Player()
            player.attr[QUEST_STAGE_MAP_ATTR] = mutableMapOf(quest.key to stage)
            for (mode in listOf("during", null)) {
                assertEquals(stage in 1 until quest.maxSteps, resolver.dropCondition(quest.key, mode)(player))
            }
            assertEquals(stage, quest.getQuestStage(player))
        }
    }

    @Test fun `ordinary drop alternatives stay exclusive for every policy and actual stage`() {
        val quest = registerQuest()
        for (policy in listOf("optional", "respect-progress", "assume-completed", "virtual-completions")) {
            val resolver = resolver(policy)
            for (stage in listOf(0, 1, quest.maxSteps)) {
                val player = Player()
                player.attr[QUEST_STAGE_MAP_ATTR] = mutableMapOf(quest.key to stage)
                val unlocked = resolver.dropCondition(quest.key, "completed")(player)
                val locked = resolver.dropCondition(quest.key, "not_completed")(player)
                assertNotEquals(unlocked, locked, "$policy, stage $stage")
                assertEquals(policy != "respect-progress" || stage == quest.maxSteps, unlocked)
            }
        }
    }

    private fun resolver(mode: String) = QuestRequirementResolver(
        ServerConfig("test", 43594, 240, "LIVE", 255,
            GameplayConfig(QuestRequirementsYaml(mode = mode, virtualCompletions = setOf("quest_cooksassistant")))),
    )

    private fun registerQuest() = Quest.get("quest_cooksassistant") ?: Quest.register(
        "quest_cooksassistant", "varp.cookquest", null, rewards {},
    )

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
