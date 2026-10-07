package org.rsmod.content.other.consumables.potion

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.stat.PlayerStatResetService
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.content.other.consumables.heart.HeartType
import org.rsmod.content.other.consumables.heart.heartCooldownExpiresAt
import org.rsmod.content.other.consumables.heart.saturatedHeartExpiresAt
import org.rsmod.content.other.consumables.heart.savedHeartCooldownTicks
import org.rsmod.content.other.consumables.heart.savedSaturatedHeartActive

@ResourceLock("ServerCacheManager")
class ConsumableStatResetHookTest {
    @Test
    fun `full activity reset clears maintained boosts and prayer while preserving other potion effects and heart reuse`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_antifire")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_prayer_regeneration")
        f.drink("dbrow.effect_divine_super_attack_boost")
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Saturated)
        f.at(1011)
        f.prepare()
        val cooldown = f.player.heartCooldownExpiresAt
        val savedCooldown = f.player.savedHeartCooldownTicks
        val heartTimer = f.timer(HeartEffectService.TIMER)
        val retainedTimers = listOf(StaminaEffectService.TIMER, AntifireEffectService.ANTIFIRE_TIMER, MenaphiteRemedyEffectService.TIMER)
            .associateWith(f::timer)

        f.injector.getInstance(PlayerStatResetService::class.java).resetAll(f.player)

        assertEquals(0, f.player.prayerRegenerationPulses)
        assertEquals(0, f.player.savedPrayerRegenerationPulses)
        assertEquals(0, f.player.vars[PrayerRegenerationEffectService.DISPLAY_VARBIT])
        assertNull(f.timer(PrayerRegenerationEffectService.TIMER))
        assertTrue(DivineState.active(f.player).isEmpty())
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertNull(f.timer(DivineEffectService.DIVINE_TIMER))
        assertTrue(PotionLifecycleFixture.DIVINE_FIELDS.all { f.player.vars[it] == 0 })
        assertFalse(f.player.savedSaturatedHeartActive)
        assertEquals(0, f.player.saturatedHeartExpiresAt)
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertEquals(99, f.player.stat("stat.attack"))
        assertEquals(99, f.player.stat("stat.magic"))
        assertEquals(cooldown, f.player.heartCooldownExpiresAt)
        assertEquals(savedCooldown, f.player.savedHeartCooldownTicks)
        assertEquals(heartTimer, f.timer(HeartEffectService.TIMER))
        assertEquals(retainedTimers, retainedTimers.keys.associateWith(f::timer))
        assertEquals(1200, f.player.staminaExpiresAt)
        assertEquals(1600, AntifireState.NORMAL.expiresAt(f.player))
        assertEquals(1500, f.player.menaphiteRemedyExpiresAt)
        heart.activate(f.access(), HeartType.Saturated)
        assertEquals(99, f.player.stat("stat.magic"))
        assertTrue(f.client.messages.any { it.toString().contains("still drained of its power") })
        f.logout()
        f.restore(f.saved(), 9000)
        assertEquals(0, f.player.prayerRegenerationPulses)
        assertTrue(DivineState.active(f.player).isEmpty())
        assertFalse(f.player.savedSaturatedHeartActive)
        assertTrue(f.player.heartCooldownExpiresAt > 9000)
    }

    @Test
    fun `partial stat restoration leaves active and saved prayer and divine effects intact`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_prayer_regeneration")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.prepare()
        val divine = DivineState.active(f.player)

        f.player.statRestoreAll(listOf("stat.attack"))

        assertEquals(99, f.player.stat("stat.attack"))
        assertEquals(66, f.player.prayerRegenerationPulses)
        assertEquals(66, f.player.savedPrayerRegenerationPulses)
        assertEquals(divine, DivineState.active(f.player))
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
