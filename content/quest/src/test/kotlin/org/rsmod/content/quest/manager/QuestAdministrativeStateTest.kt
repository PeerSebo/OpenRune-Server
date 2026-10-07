package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestAdministrativeStateTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    private fun quest() = Quest(
        id = 10, key = "quest_pr216_test", rowID = 0, displayName = "Test quest",
        mapElement = null, startCoord = null, maxSteps = 6, questPoints = 3,
        questVarp = "varp.goblinquest", rewards = rewards {}, itemDisplay = null,
        questVarbit = "varbit.gobdip_main",
    )

    @Test fun `administrative stage jump preserves flags sharing its native varp`() {
        val player = Player()
        val quest = quest()
        val flags = 7 shl 6
        VarPlayerIntMapSetter.set(player, "varp.goblinquest", flags or 1)
        quest.jumpToStage(player, 4)
        assertEquals(4, quest.getQuestStage(player))
        assertEquals(flags or 4, player.vars["varp.goblinquest"])
        assertEquals(0, player.vars["varp.qp"])
        assertThrows(IllegalArgumentException::class.java) { quest.jumpToStage(player, 7) }
        assertEquals(4, quest.getQuestStage(player))
    }

    @Test fun `quest reset clears registered side state and refunds completion counters once`() {
        val player = Player()
        val quest = quest()
        val sideState = quest.attribute("GIFT_PENDING", false)
        sideState.set(player, true)
        quest.jumpToStage(player, 6)
        VarPlayerIntMapSetter.set(player, "varp.qp", 10)
        VarPlayerIntMapSetter.set(player, "varbit.quests_completed_count", 2)
        quest.resetQuest(player)
        assertFalse(sideState.exists(player))
        assertEquals(0, quest.getQuestStage(player))
        assertEquals(7, player.vars["varp.qp"])
        assertEquals(1, player.vars["varbit.quests_completed_count"])
        quest.resetQuest(player)
        assertEquals(7, player.vars["varp.qp"])
        assertEquals(1, player.vars["varbit.quests_completed_count"])
    }
}
