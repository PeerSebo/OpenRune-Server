package org.rsmod.content.bosses.vardorvis

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class VardorvisAccessTest {
    @Test
    fun `optional access opens the entry without requiring actual quest progress`() {
        assertTrue(VardorvisAccess.opensEntry(QuestRequirementPolicy(QuestRequirementMode.Optional)))
        assertTrue(VardorvisAccess.opensEntry(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted)))
        assertFalse(VardorvisAccess.opensEntry(QuestRequirementPolicy(QuestRequirementMode.RespectProgress)))
        assertFalse(VardorvisAccess.opensEntry(QuestRequirementPolicy(QuestRequirementMode.VirtualCompletions)))
        assertTrue(VardorvisAccess.opensEntry(QuestRequirementPolicy(
            QuestRequirementMode.VirtualCompletions, setOf("quest_deserttreasure2"))))
    }

    @Test
    fun `replacement retains native tile shape and angle and has no quest transform`() {
        val original = LocInfo(2, CoordGrid(1118, 3428, 0),
            LocEntity("loc.dt2_stranglewood_boss_entry".asRSCM(), LocShape.CentrepieceStraight.id, 2))
        val opened = VardorvisAccess.openEntry(original)
        assertEquals(original.coords, opened.coords)
        assertEquals(original.layer, opened.layer)
        assertEquals(original.shape, opened.shape)
        assertEquals(original.angle, opened.angle)
        assertEquals("loc.dt2_stranglewood_boss_entry_op".asRSCM(), opened.id)
        val type = checkNotNull(ServerCacheManager.getObject(opened.id))
        assertTrue(type.transforms.isNullOrEmpty())
        assertEquals(-1, type.multiVarBit)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
