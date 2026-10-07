package org.rsmod.content.other.consumables

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.content.other.consumables.potion.PotionLifecycleFixture

@ResourceLock("ServerCacheManager")
class ConsumableDelayStateTest {
    @Test
    fun `food potion and combo locks retain their asymmetric consumption order`() {
        val expected = mapOf(
            ConsumableType.FOOD to listOf(false, true, true),
            ConsumableType.POTION to listOf(false, false, true),
            ConsumableType.COMBO_FOOD to listOf(false, false, false),
        )
        for ((type, permissions) in expected) {
            val f = PotionLifecycleFixture()
            assertEquals(listOf(true, true, true), allowed(f))
            ConsumableDelayState.recordConsumption(f.access(), type, 3, 0)
            assertEquals(permissions, allowed(f))
            f.at(1002)
            assertEquals(permissions, allowed(f))
            f.at(1003)
            assertEquals(listOf(true, true, true), allowed(f))
        }
    }

    @Test
    fun `food potion and combo consumption add only their own attack delays`() {
        val f = PotionLifecycleFixture()
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.FOOD, 3, 3)
        assertEquals(1003, f.player.actionDelay)
        assertTrue(ConsumableDelayState.canConsume(f.access(), ConsumableType.POTION))
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.POTION, 3, 0)
        assertEquals(1003, f.player.actionDelay)
        assertTrue(ConsumableDelayState.canConsume(f.access(), ConsumableType.COMBO_FOOD))
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.COMBO_FOOD, 3, 2)
        assertEquals(1005, f.player.actionDelay)
        assertEquals(listOf(false, false, false), allowed(f))
    }

    @Test
    fun `a longer weapon delay is retained without adding it to every food delay`() {
        val f = PotionLifecycleFixture()
        f.player.actionDelay = 1010
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.FOOD, 3, 3)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.COMBO_FOOD, 3, 2)
        assertEquals(1010, f.player.actionDelay)
        assertEquals(1005, f.player.vars["varp.consumables_combat_delay_until"])
        f.at(1011)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.FOOD, 3, 3)
        assertEquals(1014, f.player.actionDelay)
    }

    @Test
    fun `shorter repeated locks and zero or negative delays never shorten existing locks`() {
        val f = PotionLifecycleFixture()
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.POTION, 8, 3)
        f.at(1001)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.POTION, 2, 0)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.POTION, -2, -3)
        assertEquals(1008, f.player.vars["varp.consumables_potion_delay_until"])
        assertEquals(1003, f.player.actionDelay)
        f.at(1007)
        assertFalse(ConsumableDelayState.canConsume(f.access(), ConsumableType.POTION))
        f.at(1008)
        assertTrue(ConsumableDelayState.canConsume(f.access(), ConsumableType.POTION))
    }

    @Test
    fun `food extends only an already active stun action lock`() {
        val f = PotionLifecycleFixture()
        Stun.apply(f.player, movementCycles = 5, actionCycles = 5)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.FOOD, 3, 3)
        assertEquals(5, Stun.movementCyclesRemaining(f.player))
        assertEquals(8, Stun.actionCyclesRemaining(f.player))
        f.at(1008)
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.COMBO_FOOD, 3, 2)
        assertFalse(Stun.isActionBlocked(f.player))
        assertEquals(1010, f.player.actionDelay)
    }

    @Test
    fun `registered death cleanup clears all consumable delays`() {
        val f = PotionLifecycleFixture()
        for (type in ConsumableType.entries) {
            ConsumableDelayState.recordConsumption(f.access(), type, 3, 2)
        }
        f.death()
        assertEquals(listOf(true, true, true), allowed(f))
        assertTrue(FIELDS.all { f.player.vars[it] == 0 })
        ConsumableDelayState.recordConsumption(f.access(), ConsumableType.FOOD, 3, 3)
        assertEquals(1003, f.player.vars["varp.consumables_combat_delay_until"])
    }

    @Test
    fun `consumable delays never persist across reconnect`() {
        val f = PotionLifecycleFixture()
        for (type in ConsumableType.entries) {
            ConsumableDelayState.recordConsumption(f.access(), type, 3, 2)
        }
        val saved = f.saved()
        assertTrue(FIELDS.all { it.asRSCM() !in saved.vars })
        f.restore(saved, 9000)
        assertEquals(listOf(true, true, true), allowed(f))
        assertTrue(FIELDS.all { f.player.vars[it] == 0 })
    }

    @Test
    fun `consume delay definitions are temporary and never transmitted`() {
        for (field in FIELDS) {
            val definition = ServerCacheManager.getVarp(field.asRSCM())!!
            assertEquals(VarpLifetime.Temp, definition.scope)
            assertEquals(VarpTransmitLevel.Never, definition.transmit)
        }
    }

    private fun allowed(f: PotionLifecycleFixture) =
        ConsumableType.entries.map { ConsumableDelayState.canConsume(f.access(), it) }

    companion object {
        private val FIELDS = listOf("food", "potion", "combo_food", "combat")
            .map { "varp.consumables_${it}_delay_until" }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
