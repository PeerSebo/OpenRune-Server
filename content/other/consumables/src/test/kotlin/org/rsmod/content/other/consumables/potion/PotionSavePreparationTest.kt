package org.rsmod.content.other.consumables.potion

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.stat.stat

@ResourceLock("ServerCacheManager")
class PotionSavePreparationTest {
    @Test
    fun `a bound autosave hook captures current durations without logout or runtime changes`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_antifire")
        f.drink("dbrow.effect_super_antifire")
        f.drink("dbrow.effect_prayer_regeneration")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.at(1011)
        val timers = TIMERS.associateWith(f::timer)
        val runtime = runtime(f)
        val stats = listOf("stat.attack", "stat.prayer", "stat.hitpoints").map { f.player.stat(it) }
        val clientMessages = f.client.messages.size

        f.prepare()

        assertEquals(189, f.player.savedStaminaTicks)
        assertEquals(589, AntifireState.NORMAL.remaining(f.player))
        assertEquals(289, AntifireState.SUPER.remaining(f.player))
        assertEquals(66, f.player.savedPrayerRegenerationPulses)
        assertEquals(1, f.player.savedPrayerRegenerationDelay)
        assertEquals(489, f.player.savedMenaphiteRemedyTicks)
        assertEquals(mapOf("divine_super_attack_boost" to 489), DivineState.saved(f.player))
        assertEquals(timers, TIMERS.associateWith(f::timer))
        assertEquals(runtime, runtime(f))
        assertEquals(stats, listOf("stat.attack", "stat.prayer", "stat.hitpoints").map { f.player.stat(it) })
        assertEquals(clientMessages, f.client.messages.size)
    }

    @Test
    fun `retrying logout preparation does not subtract ticks while the player waits for saving`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_antifire")
        f.drink("dbrow.effect_prayer_regeneration")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.at(1011)
        f.logout()
        val savedAtLogout = f.saved()

        f.at(1511)
        f.prepare()

        assertEquals(savedAtLogout, f.saved())
        f.restore(f.saved(), 9000)
        assertEquals(9189, f.player.staminaExpiresAt)
        assertEquals(9589, AntifireState.NORMAL.expiresAt(f.player))
        assertEquals(9001, f.timer(PrayerRegenerationEffectService.TIMER))
        assertEquals(9489, f.player.menaphiteRemedyExpiresAt)
        assertEquals(listOf(9489), DivineState.active(f.player).values.toList())
    }

    @Test
    fun `an autosaved prayer pulse resumes its phase on a new map clock`() {
        val f = PotionLifecycleFixture()
        f.level("stat.prayer", 0)
        f.drink("dbrow.effect_prayer_regeneration")
        f.at(1012)
        f.effects.processPrayerRegeneration(f.access())
        f.at(1017)
        f.prepare()

        f.restore(f.saved(), 9000)

        assertEquals(65, f.player.prayerRegenerationPulses)
        assertEquals(9007, f.player.prayerRegenerationNextPulseAt)
        assertEquals(9007, f.timer(PrayerRegenerationEffectService.TIMER))
    }

    @Test
    fun `autosaved countdowns resume exact remaining durations without offline decay`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_antifire")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.at(1041)
        f.prepare()

        f.restore(f.saved(), 9000)

        assertEquals(9159, f.player.staminaExpiresAt)
        assertEquals(9559, AntifireState.NORMAL.expiresAt(f.player))
        assertEquals(9459, f.player.menaphiteRemedyExpiresAt)
        assertEquals(9025, f.timer(MenaphiteRemedyEffectService.TIMER))
        assertEquals(listOf(9459), DivineState.active(f.player).values.toList())
    }

    @Test
    fun `redosing after an autosave is reflected in the next prepared save`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_prayer_regeneration")
        f.at(1011)
        f.prepare()
        val prior = f.saved()
        f.at(1050)
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_prayer_regeneration")
        f.at(1054)
        f.prepare()
        val refreshed = f.saved()

        f.restore(prior, 9000)
        assertEquals(9189, f.player.staminaExpiresAt)
        assertEquals(9001, f.timer(PrayerRegenerationEffectService.TIMER))
        f.restore(refreshed, 9500)
        assertEquals(9696, f.player.staminaExpiresAt)
        assertEquals(9508, f.timer(PrayerRegenerationEffectService.TIMER))
    }

    @Test
    fun `expired state snapshots cannot revive buffs and do not steal expiry callbacks`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.at(1600)
        val timers = TIMERS.associateWith(f::timer)
        val before = f.client.messages.size

        f.prepare()

        assertEquals(0, f.player.savedStaminaTicks)
        assertEquals(0, f.player.savedMenaphiteRemedyTicks)
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertEquals(timers, TIMERS.associateWith(f::timer))
        assertEquals(before, f.client.messages.size)
        f.restore(f.saved(), 9000)
        assertEquals(0, f.player.staminaExpiresAt)
        assertEquals(0, f.player.menaphiteRemedyExpiresAt)
        assertTrue(DivineState.active(f.player).isEmpty())
    }

    @Test
    fun `death followed by autosave clears every owned effect instead of saving stale remaining state`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_stamina")
        f.drink("dbrow.effect_antifire")
        f.drink("dbrow.effect_prayer_regeneration")
        f.drink("dbrow.effect_menaphite_remedy")
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.prepare()
        f.death()
        f.prepare()

        f.restore(f.saved(), 9000)

        assertEquals(0, f.player.staminaExpiresAt)
        assertEquals(0, AntifireState.NORMAL.expiresAt(f.player))
        assertEquals(0, f.player.prayerRegenerationPulses)
        assertEquals(0, f.player.menaphiteRemedyExpiresAt)
        assertTrue(DivineState.active(f.player).isEmpty())
        assertTrue(TIMERS.all { f.timer(it) == null })
    }

    private fun runtime(f: PotionLifecycleFixture) = listOf(
        f.player.staminaExpiresAt, AntifireState.NORMAL.expiresAt(f.player),
        AntifireState.SUPER.expiresAt(f.player), f.player.prayerRegenerationPulses,
        f.player.prayerRegenerationNextPulseAt, f.player.menaphiteRemedyExpiresAt,
    )

    companion object {
        private val TIMERS = listOf(
            StaminaEffectService.TIMER, AntifireEffectService.ANTIFIRE_TIMER,
            AntifireEffectService.SUPER_ANTIFIRE_TIMER, PrayerRegenerationEffectService.TIMER,
            MenaphiteRemedyEffectService.TIMER, DivineEffectService.DIVINE_TIMER,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
