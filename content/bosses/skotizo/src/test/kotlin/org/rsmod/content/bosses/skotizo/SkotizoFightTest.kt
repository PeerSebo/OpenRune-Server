package org.rsmod.content.bosses.skotizo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.instances.InstanceId
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class SkotizoFightTest {
    @Test fun `ordinary weapons lose a quarter per eye while demonbane retains forty percent through all four`() {
        assertEquals(100, SkotizoRules.reducedDamage(100, 0, false))
        assertEquals(75, SkotizoRules.reducedDamage(100, 1, false))
        assertEquals(0, SkotizoRules.reducedDamage(100, 4, false))
        assertEquals(85, SkotizoRules.reducedDamage(100, 1, true))
        assertEquals(40, SkotizoRules.reducedDamage(100, 4, true))
        assertEquals(0, SkotizoRules.reducedDamage(0, 4, true))
    }

    @Test fun `three minutes with all four eyes open exhausts later revivals`() {
        val eyes = AltarCycle()
        for (i in 0..3) assertTrue(eyes.activate(i, 100 + i))
        assertFalse(eyes.activate(0, 110))
        eyes.tick(402)
        assertFalse(eyes.exhausted)
        eyes.deactivate(0, 403)
        assertTrue(eyes.exhausted)
        assertEquals(3, eyes.count)
        assertFalse(eyes.activate(0, 1000))
    }

    @Test fun `closing an eye early resets the uninterrupted three minute period`() {
        val eyes = AltarCycle()
        repeat(4) { eyes.activate(it, 0) }
        eyes.deactivate(1, 299)
        assertTrue(eyes.activate(1, 300))
        eyes.tick(599)
        assertFalse(eyes.exhausted)
        eyes.tick(600)
        assertTrue(eyes.exhausted)
    }

    @Test fun `demon spawns unlock after two thirds health has been depleted`() {
        assertFalse(SkotizoRules.minionsUnlocked(151))
        assertTrue(SkotizoRules.minionsUnlocked(150))
        assertTrue(SkotizoRules.minionsUnlocked(1))
        assertFalse(SkotizoRules.minionsUnlocked(0))
    }

    @Test fun `private copies translate every native landmark and exclude another owner arena`() {
        val one = SkotizoRoom(InstanceId(1), Player(), CoordGrid(10000, 10000))
        val two = SkotizoRoom(InstanceId(2), Player(), CoordGrid(10200, 10000))
        for (site in SkotizoRules.Altars + SkotizoRules.BossSpawn + SkotizoRules.Entry) {
            assertTrue(one.contains(one.tile(site)))
            assertFalse(two.contains(one.tile(site)))
        }
        assertFalse(one.contains(CoordGrid(one.enter.x, one.enter.z, 1)))
        assertFalse(one.contains(SkotizoRules.Exit))
    }
}
