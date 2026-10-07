package org.rsmod.content.bosses.kraken

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.instances.InstanceArea
import org.rsmod.game.hit.HitType

class KrakenFightTest {
    @Test fun `four distinct small pools precede manual boss activation and each respawn resets them`() {
        val cycle = KrakenCycle()
        assertFalse(cycle.awaken(false))
        repeat(10) { cycle.disturbTentacle(0) }
        assertFalse(cycle.awaken(false))
        cycle.disturbTentacle(1); cycle.disturbTentacle(2)
        assertFalse(cycle.awaken(false))
        cycle.disturbTentacle(3)
        assertTrue(cycle.awaken(false))
        assertFalse(cycle.awaken(true))
        cycle.reset()
        assertFalse(cycle.awake)
        assertFalse(cycle.awaken(false))
        assertTrue(cycle.awaken(true))
    }

    @Test fun `level boosts exhausted tasks and unrelated assignments cannot open the fight`() {
        assertFalse(KrakenRules.eligible(86, 20, true))
        assertFalse(KrakenRules.eligible(99, 0, true))
        assertFalse(KrakenRules.eligible(99, 20, false))
        assertTrue(KrakenRules.eligible(87, 1, true))
    }

    @Test fun `submerged pools take no damage and active Kraken uses actual damage style`() {
        for (style in HitType.entries) assertEquals(0, KrakenRules.incoming(70, style, false))
        assertEquals(0, KrakenRules.incoming(70, HitType.Melee, true))
        assertEquals(10, KrakenRules.incoming(70, HitType.Ranged, true))
        assertEquals(1, KrakenRules.incoming(6, HitType.Ranged, true))
        assertEquals(0, KrakenRules.incoming(0, HitType.Ranged, true))
        assertEquals(70, KrakenRules.incoming(70, HitType.Magic, true))
        assertEquals(6, KrakenRules.incoming(6, HitType.Typeless, true))
    }

    @Test fun `private room charges once and independently owns exactly five native submerged NPCs`() {
        val spec = KrakenCove.PrivateSpec
        assertEquals(25_000, spec.fee)
        assertEquals(1, spec.maxPlayers)
        assertTrue(spec.destroyWhenEmpty)
        val area = spec.area as InstanceArea.CopyRegions
        assertEquals(listOf(9116), area.regionIds)
        assertEquals(5, area.npcSpawns.size)
        assertEquals(KrakenCove.OutsideRoom, area.exitCoord)
    }
}
