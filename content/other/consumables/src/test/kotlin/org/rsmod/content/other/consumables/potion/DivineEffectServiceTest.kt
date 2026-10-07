package org.rsmod.content.other.consumables.potion

import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.stat
import org.rsmod.api.table.PotionEffectRow

@ResourceLock("ServerCacheManager")
class DivineEffectServiceTest {
    @Test
    fun `drinking applies the base boost and hit but rejects insufficient hitpoints`() {
        val f = PotionLifecycleFixture()
        f.drink(ATTACK)

        assertEquals(118, f.player.stat("stat.attack"))
        assertEquals(89, f.player.stat("stat.hitpoints"))
        assertEquals(listOf(10), f.hits)
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        f.level("stat.hitpoints", 10)
        assertFalse(f.effects.canApply(f.access(), PotionEffectRow.getRow(ATTACK)))
        assertTrue(f.client.messages.last().toString().contains("You need more Hitpoints"))
        assertEquals(listOf(10), f.hits)
    }

    @Test
    fun `redosing resets native clocks before publishing overlapping effects`() {
        val f = PotionLifecycleFixture()
        f.drink(ATTACK)
        f.at(1100)
        f.client.divineFrames.clear()

        f.drink(COMBAT)

        val frames = f.client.divineFrames
        val blank = frames.indexOfFirst { frame -> frame.all { it == 0 } }
        assertTrue(blank >= 0, "The previous native clocks must be cleared before restarting.")
        val refreshed = frames.indexOfFirst { it[0] == 500 && it[1] == 500 && it[2] == 500 && it[5] == 500 }
        assertTrue(refreshed > blank)
        assertEquals(listOf(500, 500, 500, 0, 0, 500, 0, 0), frames.last())

        f.at(1500)
        f.effects.processDivineEffects(f.access())

        assertEquals(118, f.player.stat("stat.attack"))
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        assertEquals(listOf(100, 100, 100, 0, 0, 100, 0, 0), f.client.divineFrames.last())
    }

    @Test
    fun `warning fires once and expiry clears positive boosts without restoring drained stats`() {
        val f = PotionLifecycleFixture()
        f.drink(COMBAT)
        f.level("stat.strength", 70)
        f.at(1475)
        f.effects.processDivineEffects(f.access())
        f.at(1476)
        f.effects.processDivineEffects(f.access())
        assertEquals(1, f.client.messages.count { it.toString().contains("is about to expire") })
        assertEquals(1500, f.timer(DivineEffectService.DIVINE_TIMER))

        f.at(1500)
        f.effects.processDivineEffects(f.access())

        assertEquals(99, f.player.stat("stat.attack"))
        assertEquals(70, f.player.stat("stat.strength"))
        assertEquals(99, f.player.stat("stat.defence"))
        assertEquals(1, f.client.messages.count { it.toString().contains("has expired") })
        assertTrue(DivineState.active(f.player).isEmpty())
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertNull(f.timer(DivineEffectService.DIVINE_TIMER))
        assertTrue(PotionLifecycleFixture.DIVINE_FIELDS.all { f.player.vars[it] == 0 })
    }

    @Test
    fun `expiry and maintenance cancellation preserve unrelated decay protection`() {
        val f = PotionLifecycleFixture()
        StatBoostDecayPrevention.add(f.player, "stat.attack", "test.unrelated")
        f.drink(ATTACK)
        f.at(1500)
        f.effects.processDivineEffects(f.access())
        assertEquals(118, f.player.stat("stat.attack"))
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))

        f.drink(COMBAT)
        f.divine.cancelMaintenance(f.player)

        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.strength"))
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.defence"))
        assertTrue(DivineState.active(f.player).isEmpty())
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertNull(f.timer(DivineEffectService.DIVINE_TIMER))
    }

    @Test
    fun `death clears saved and native divine state after generic attributes have reset`() {
        val f = PotionLifecycleFixture()
        f.drink(COMBAT)
        f.drink("dbrow.effect_stamina")
        f.prepare()
        f.player.attr.removeIf { it.resetOnDeath }
        val messagesBefore = f.client.messages.size
        val hooks = f.injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))

        hooks.single { it is DivineDeathCleanupHook }.cleanup(f.player)

        assertTrue(DivineState.active(f.player).isEmpty())
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertNull(f.timer(DivineEffectService.DIVINE_TIMER))
        assertTrue(PotionLifecycleFixture.DIVINE_FIELDS.all { f.player.vars[it] == 0 })
        assertTrue(f.player.staminaExpiresAt > 0)
        assertFalse(f.client.messages.drop(messagesBefore).any { it.toString().contains("has expired") })
        assertEquals(118, f.player.stat("stat.attack"))
    }

    companion object {
        private const val ATTACK = "dbrow.effect_divine_super_attack_boost"
        private const val COMBAT = "dbrow.effect_divine_super_combat_boost"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
