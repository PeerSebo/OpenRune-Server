package org.rsmod.content.quest.manager

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
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class OptionalQuestRequirementsTest {
    private val originalPolicy = QuestRequirements.activePolicy()

    @AfterEach fun restorePolicy() = QuestRequirements.install(originalPolicy)

    @Test fun `optional access preserves every actual quest stage and earned points`() {
        val quest = registerQuest()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.Optional))
        for (stage in listOf(0, 1, quest.maxSteps)) {
            val player = Player()
            player.attr[QUEST_STAGE_MAP_ATTR] = mutableMapOf(quest.key to stage)

            assertTrue(QuestRequirements.hasCompleted(player, quest.key))
            assertTrue(QuestRequirements.hasQuestPoints(player, 100))
            assertTrue(QuestRequirements.canStart(player, "quest_dragonslayer1"))
            assertEquals(stage in 1 until quest.maxSteps, QuestRequirements.isOnQuest(player, quest.key))
            assertEquals(stage < quest.maxSteps, QuestRequirements.hasNotCompleted(player, quest.key))
            assertEquals(stage == quest.maxSteps, quest.isQuestCompleted(player))
            assertEquals(stage, quest.getQuestStage(player))
            assertEquals(0, QuestRequirements.earnedQuestPoints(player))
            assertEquals(0, player.vars["varp.cookquest"])
        }
    }

    @Test fun `unimplemented quests cannot block optional content or pretend an objective started`() {
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.Optional))
        val player = Player()
        val missing = "quest_optional_policy_unregistered"
        assertNull(Quest.get(missing))
        assertTrue(QuestRequirements.hasCompleted(player, missing))
        assertTrue(QuestRequirements.hasNotCompleted(player, missing))
        assertFalse(QuestRequirements.isOnQuest(player, missing))
        assertNull(player.attr[QUEST_STAGE_MAP_ATTR])
    }

    @Test fun `strict assumed and virtual policies retain their existing contracts`() {
        val quest = registerQuest()
        val player = Player()
        player.attr[QUEST_STAGE_MAP_ATTR] = mutableMapOf(quest.key to 1)
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        assertFalse(QuestRequirements.hasCompleted(player, quest.key))
        assertFalse(QuestRequirements.hasQuestPoints(player, 1))
        assertTrue(QuestRequirements.isOnQuest(player, quest.key))
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        assertTrue(QuestRequirements.hasCompleted(player, quest.key))
        assertFalse(QuestRequirements.isOnQuest(player, quest.key))
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.VirtualCompletions, setOf(quest.key)))
        assertTrue(QuestRequirements.hasCompleted(player, quest.key))
        assertFalse(QuestRequirements.isOnQuest(player, quest.key))
        assertFalse(quest.isQuestCompleted(player))
    }

    @Test fun `server configuration selects optional mode without granting saved completions`() {
        val config = ServerConfig("test", 43594, 240, "LIVE", 255,
            GameplayConfig(QuestRequirementsYaml(mode = "optional")))
        val resolver = QuestRequirementResolver(config)
        assertEquals(QuestRequirementMode.Optional, resolver.policy.mode)
        assertTrue(resolver.hasCompleted(Player(), "quest_unimplemented"))
        assertEquals(QuestRequirementMode.Optional, QuestRequirementMode.parse(" OPTIONAL "))
    }

    private fun registerQuest() = Quest.get("quest_cooksassistant") ?: Quest.register(
        "quest_cooksassistant", "varp.cookquest", null, rewards {},
    )

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
