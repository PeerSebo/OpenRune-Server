package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.scripts.SailingBoardingLifecycleTest
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class SailingQuestSupportTest {
    @Test
    fun `Jim reuses an owned raft without replacing its fitted facilities`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        val player = fixture.player
        val facilityVar = SailingBoatVars.hotspot(1, 0)
        player.writeSailingVar(facilityVar, 3)
        val support = SailingQuestSupport(BoatOwnershipService())
        assertTrue(support.grantRaft(fixture.access()))
        assertTrue(support.grantRaft(fixture.access()))
        assertEquals(listOf(1), player.ownedBoatSlots())
        assertEquals(3, player.readSailingVar(facilityVar))
        assertEquals(1, SailingQuestSupport.tutorialSlot(player))
    }

    @Test
    fun `full fleet without a raft survives the tutorial grant`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        val player = fixture.player
        for (slot in 1..5) {
            player.writeSailingVar(SailingBoatVars.owned(slot), 1)
            player.writeSailingVar(SailingBoatVars.field(slot, "type"), BoatHullEntity.SLOOP.typeId)
        }
        assertTrue(SailingQuestSupport(BoatOwnershipService()).grantRaft(fixture.access()))
        assertEquals(5, player.ownedBoatSlots().size)
        for (slot in 1..5) {
            assertEquals(BoatHullEntity.SLOOP.typeId, player.readSailingVar(SailingBoatVars.field(slot, "type")))
        }
    }

    @Test
    fun `tutorial cargo never appears on ordinary port boards`() {
        val player = player(99)
        for (dock in SailingDocks.ALL) {
            assertFalse(PortTaskManager.eligibleTasks(player, dock.row).any(SailingQuestSupport::isTutorialTask))
        }
    }

    @Test
    fun `tutorial task needs real quest progress and cannot be cancelled`() {
        val player = player(1)
        val row = SailingQuestSupport.tutorialTask
        assertNull(PortTaskManager.accept(player, row))
        player.writeSailingVar("varbit.sailing_intro", 32)
        val slot = checkNotNull(PortTaskManager.accept(player, row))
        assertFalse(PortTaskManager.isCancellable(player, slot))
        PortTaskManager.cancel(player, slot)
        assertEquals(row.taskId, PortTaskManager.slotTaskId(player, slot))
        assertNull(PortTaskManager.accept(player, row))
    }

    @Test
    fun `full task list is preserved when Jim assigns his commission`() {
        val player = player(99)
        val ordinary = PortTaskManager.all.filterNot(SailingQuestSupport::isTutorialTask).take(PortTaskManager.MAX_TASK_SLOTS)
        ordinary.forEach { assertNotNull(PortTaskManager.accept(player, it)) }
        val before = PortTaskManager.activeTasks(player).map { it.taskId }
        player.writeSailingVar("varbit.sailing_intro", 32)
        assertFalse(SailingQuestSupport(BoatOwnershipService()).assignCargoTask(player))
        assertEquals(before, PortTaskManager.activeTasks(player).map { it.taskId })
    }

    @Test
    fun `Jim assigning an existing task is idempotent`() {
        val player = player(1)
        player.writeSailingVar("varbit.sailing_intro", 32)
        val support = SailingQuestSupport(BoatOwnershipService())
        assertTrue(support.assignCargoTask(player))
        val slot = checkNotNull(PortTaskManager.slotOf(player, SailingQuestSupport.tutorialTask.taskId))
        PortTaskManager.setCargoTaken(player, slot, 1)
        player.writeSailingVar("varbit.sailing_intro", 40)
        assertTrue(support.assignCargoTask(player))
        assertEquals(1, PortTaskManager.activeSlots(player).size)
        assertEquals(1, PortTaskManager.cargoTaken(player, slot))
    }

    private fun player(level: Int): Player = Player().apply {
        statMap.setBaseLevel("stat.sailing", level.toByte())
        statMap.setCurrentLevel("stat.sailing", level.toByte())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() { ServerCacheManager.init(241).close() }
    }
}
