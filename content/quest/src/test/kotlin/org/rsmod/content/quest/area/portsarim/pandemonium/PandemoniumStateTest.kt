package org.rsmod.content.quest.area.portsarim.pandemonium

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class PandemoniumStateTest {
    @TestFactory
    fun `every resumable stage preserves both companion flags across a saved session`() =
        (0..50 step 2).flatMap { stage ->
            (0..3).map { flags ->
                DynamicTest.dynamicTest("stage $stage with flags $flags") {
                    val quest = quest()
                    val player = Player()
                    VarPlayerIntMapSetter.set(player, PRIMARY, flags shl 6)

                    quest.jumpToStage(player, stage)

                    assertEquals((flags shl 6) or stage, player.vars[PRIMARY])
                    val resumed = Player().apply {
                        vars.backing.putAll(player.vars.backing)
                        attr.putAllFromPersistence(player.attr.toPersistentMap())
                    }
                    quest.syncStageVar(resumed)
                    assertEquals(stage, quest.getQuestStage(resumed))
                    assertEquals((flags shl 6) or stage, resumed.vars[PRIMARY])
                    assertEquals(flags and 1, resumed.vars[REACHED_WRECK])
                    assertEquals((flags shr 1) and 1, resumed.vars[REPAIR_KITS])
                    assertEquals(0, resumed.vars["varp.qp"])
                }
            }
        }

    @TestFactory
    fun `native progress recovery separates stage from packed flags`() =
        listOf(0, 14, 16, 28, 40, 44, 46, 48, 50).map { stage ->
            DynamicTest.dynamicTest("native stage $stage with both flags set") {
                val quest = quest()
                val player = Player()
                VarPlayerIntMapSetter.set(player, PRIMARY, 192 or stage)

                quest.syncStageVar(player)

                assertEquals(stage, quest.getQuestStage(player))
                assertEquals(192 or stage, player.vars[PRIMARY])
                assertEquals(stage == 50, quest.isQuestCompleted(player))
                assertEquals(0, player.vars["varp.qp"])
            }
        }

    @Test
    fun `finishing dialogue remains pending until the completion handler awards the quest`() {
        val quest = quest()
        val player = Player()
        VarPlayerIntMapSetter.set(player, PRIMARY, 240)

        quest.syncStageVar(player)

        assertEquals(Pandemonium.FINISHING, quest.getQuestStage(player))
        assertFalse(quest.isQuestCompleted(player))
        assertTrue(quest.isQuestInProgress(player))
        assertEquals(1, player.vars[REPAIR_KITS])
        assertEquals(1, player.vars[REACHED_WRECK])
    }

    @Test
    fun `saved partial progress overrides a stale completed client mirror`() {
        val quest = quest()
        val player = Player()
        quest.jumpToStage(player, Pandemonium.SALVAGE)
        VarPlayerIntMapSetter.set(player, PRIMARY, 242)

        quest.syncStageVar(player)

        assertEquals(Pandemonium.SALVAGE, quest.getQuestStage(player))
        assertEquals(192 or Pandemonium.SALVAGE, player.vars[PRIMARY])
        assertFalse(quest.isQuestCompleted(player))
    }

    private fun quest(): Quest {
        val row = QuestRow.getRow("dbrow.quest_pandemonium")
        return Quest(
            id = row.id,
            key = "quest_pandemonium",
            rowID = row.rowId,
            displayName = row.displayname,
            mapElement = row.mapelement,
            startCoord = row.startcoord,
            maxSteps = row.endstate,
            questPoints = row.questpoints,
            questVarp = PRIMARY,
            questVarbit = "varbit.sailing_intro",
            rewards = rewards {},
            itemDisplay = null,
        )
    }

    companion object {
        private const val PRIMARY = "varp.sailing_intro_primary"
        private const val REACHED_WRECK = "varbit.sailing_intro_reached_wreck"
        private const val REPAIR_KITS = "varbit.sailing_intro_received_repairkits"

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(241).close()
        }
    }
}
