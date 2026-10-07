package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class MuspahStateTest {
    @Test
    fun `form state uses NPC vars and fresh encounters reset it`() {
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc("npc.muspah".asRSCM())), CoordGrid(2846, 4258))
        val fight = MuspahFight(npc)
        assertNull(fight.lastCoords)
        assertEquals(-1, fight.lastCountedAttackTick)
        fight.damageSinceSwitch = 120
        fight.shieldHp = 75
        fight.finalPhaseTriggered = true
        fight.lastCoords = CoordGrid.ZERO
        assertEquals(120, npc.vars["varn.muspah_damage_since_switch"])
        assertEquals(75, npc.vars["varn.muspah_shield_hp"])
        assertEquals(1, npc.vars["varn.muspah_final_phase"])
        assertEquals(CoordGrid.ZERO, fight.lastCoords)
        val fresh = MuspahFight(npc)
        assertEquals(0, fresh.damageSinceSwitch)
        assertEquals(0, fresh.shieldHp)
        assertFalse(fresh.finalPhaseTriggered)
        assertNull(fresh.lastCoords)
        assertEquals(-1, fresh.lastCountedAttackTick)
    }

    @Test
    fun `two bosses keep independent scalar counters and spike collections`() {
        val type = checkNotNull(ServerCacheManager.getNpc("npc.muspah".asRSCM()))
        val first = MuspahFight(Npc(type, CoordGrid(14000, 600)))
        val second = MuspahFight(Npc(type, CoordGrid(14000, 800)))
        first.hitsSinceSwitch = 4
        first.soulsplitBroken = true
        assertEquals(0, second.hitsSinceSwitch)
        assertFalse(second.soulsplitBroken)
        assertTrue(first.activeSpikes !== second.activeSpikes)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
