package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.rewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DraynorManorSearchTest {
    @Test fun `fountain uses real progress even while quest requirements assume completion`() {
        val access = access()
        val quest = Quest.get("quest_ernestthechicken") ?: Quest.register(
            "quest_ernestthechicken", "varp.haunted", null, rewards {},
        )
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
            assertFalse(DraynorManorSearch.canFindPressureGauge(access.player))
            quest.jumpToStage(access.player, 1)
            assertFalse(QuestRequirements.isOnQuest(access.player, quest.key))
            assertTrue(DraynorManorSearch.canFindPressureGauge(access.player))
            quest.jumpToStage(access.player, quest.maxSteps)
            assertFalse(DraynorManorSearch.canFindPressureGauge(access.player))
        } finally {
            QuestRequirements.install(previous)
        }
    }

    @Test fun `lost small key can be replaced despite its old permanent found flag`() {
        val access = access()
        access.player.attr[AttributeKey<Boolean>(persistenceKey = "ernest_found_closet_key")] = true
        assertTrue(DraynorManorSearch.findClosetKey(access))
        assertEquals(1, access.inv.count("obj.closet_key"))
        assertFalse(DraynorManorSearch.findClosetKey(access))
        access.inv[0] = null
        assertTrue(DraynorManorSearch.findClosetKey(access))
    }

    @Test fun `banked key prevents duplicates and full inventory never marks it collected`() {
        val access = access()
        access.bank[0] = InvObj("obj.closet_key")
        assertFalse(DraynorManorSearch.findClosetKey(access))
        access.bank[0] = null
        for (slot in access.inv.indices) access.inv[slot] = InvObj("obj.bronze_dagger")
        assertFalse(DraynorManorSearch.findClosetKey(access))
        access.inv[0] = null
        assertTrue(DraynorManorSearch.findClosetKey(access))
    }

    private fun access(): ProtectedAccess {
        val events = EventBus()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val player = Player().apply { inv = invMap.getOrPut("inv.inv") }
        return ProtectedAccess(player, GameCoroutine(),
            ProtectedAccessContextFactory.empty().copy(getEventBus = { events }))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
