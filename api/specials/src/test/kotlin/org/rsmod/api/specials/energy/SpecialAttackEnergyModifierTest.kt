package org.rsmod.api.specials.energy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class SpecialAttackEnergyModifierTest {
    @Test
    fun `default and cleared modifiers preserve normal costs`() {
        val player = Player()
        assertFalse(SpecialAttackEnergyModifier.isActive(player))
        assertEquals(55, SpecialAttackEnergyModifier.adjustedCost(player, 55))
        SpecialAttackEnergyModifier.setCostDivisor(player, 2)
        assertTrue(SpecialAttackEnergyModifier.isActive(player))
        assertEquals(28, SpecialAttackEnergyModifier.adjustedCost(player, 55))

        SpecialAttackEnergyModifier.clear(player)

        assertFalse(SpecialAttackEnergyModifier.isActive(player))
        assertEquals(55, SpecialAttackEnergyModifier.adjustedCost(player, 55))
    }

    @Test
    fun `specialized requirements remain exempt while other costs round up without overflow`() {
        val player = Player()
        SpecialAttackEnergyModifier.setCostDivisor(player, 2)
        for (cost in 0..9) assertEquals(cost, SpecialAttackEnergyModifier.adjustedCost(player, cost))
        assertEquals(5, SpecialAttackEnergyModifier.adjustedCost(player, 10))
        assertEquals(6, SpecialAttackEnergyModifier.adjustedCost(player, 11))
        assertEquals(1_073_741_824, SpecialAttackEnergyModifier.adjustedCost(player, Int.MAX_VALUE))
        SpecialAttackEnergyModifier.setCostDivisor(player, Int.MAX_VALUE)
        assertEquals(1, SpecialAttackEnergyModifier.adjustedCost(player, 100))
    }

    @Test
    fun `invalid divisors cannot corrupt an active effect`() {
        val player = Player()
        SpecialAttackEnergyModifier.setCostDivisor(player, 2)
        for (divisor in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                SpecialAttackEnergyModifier.setCostDivisor(player, divisor)
            }
            assertEquals(25, SpecialAttackEnergyModifier.adjustedCost(player, 50))
        }
    }

    @Test
    fun `modifier is private session state and does not survive a character save`() {
        val definition = checkNotNull(ServerCacheManager.getVarp(VARP.asRSCM()))
        assertEquals(VarpLifetime.Temp, definition.scope)
        assertEquals(VarpTransmitLevel.Never, definition.transmit)
        val player = Player()
        SpecialAttackEnergyModifier.setCostDivisor(player, 2)
        val restored = Player().apply {
            vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertFalse(SpecialAttackEnergyModifier.isActive(restored))
        assertEquals(50, SpecialAttackEnergyModifier.adjustedCost(restored, 50))
    }

    companion object {
        private const val VARP = "varp.player_special_attack_cost_divisor"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
