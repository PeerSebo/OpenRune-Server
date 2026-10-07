package org.rsmod.content.other.consumables.heart

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.stat
import org.rsmod.content.other.consumables.potion.PotionLifecycleFixture

@ResourceLock("ServerCacheManager")
class HeartStateTest {
    @Test
    fun `legacy saves import both heart kinds and preserve exact remaining duration`() {
        for (saturated in listOf(false, true)) {
            val f = PotionLifecycleFixture()
            f.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to 137, LEGACY_ACTIVE to saturated))
            f.restore(f.saved(), 9000)
            assertEquals(9137, f.player.heartCooldownExpiresAt)
            assertEquals(if (saturated) 9137 else 0, f.player.saturatedHeartExpiresAt)
            assertEquals(saturated, StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
            assertEquals(9137, f.timer(HeartEffectService.TIMER))
            assertEquals(14, f.player.vars["varbit.imbued_heart_timer"])
            assertTrue(f.saved().attrs.keys.none { it.startsWith("consumables.heart.") })
        }
    }

    @Test
    fun `explicit modern zero and false take precedence independently over legacy values`() {
        val f = PotionLifecycleFixture()
        f.player.savedHeartCooldownTicks = 0
        f.player.savedSaturatedHeartActive = false
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to 500, LEGACY_ACTIVE to true))
        HeartState.migrate(f.player)
        assertEquals(0, f.player.savedHeartCooldownTicks)
        assertFalse(f.player.savedSaturatedHeartActive)

        val mixed = PotionLifecycleFixture()
        mixed.player.savedSaturatedHeartActive = false
        mixed.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to 17, LEGACY_ACTIVE to true))
        mixed.restore(mixed.saved(), 5000)
        assertEquals(5017, mixed.player.heartCooldownExpiresAt)
        assertEquals(0, mixed.player.saturatedHeartExpiresAt)
    }

    @Test
    fun `completed migration and clearing prevent stale legacy saves from reviving state`() {
        val f = PotionLifecycleFixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to 500, LEGACY_ACTIVE to true))
        HeartState.migrate(f.player)
        HeartState.clear(f.player)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to 500, LEGACY_ACTIVE to true))
        f.restore(f.saved(), 9000)
        assertEquals(0, f.player.heartCooldownExpiresAt)
        assertEquals(0, f.player.savedHeartCooldownTicks)
        assertFalse(f.player.savedSaturatedHeartActive)
        assertNull(f.timer(HeartEffectService.TIMER))
        assertTrue(f.saved().attrs.keys.none { it.startsWith("consumables.heart.") })
    }

    @Test
    fun `negative legacy duration becomes inactive without restoring maintenance`() {
        val f = PotionLifecycleFixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_TICKS to -7, LEGACY_ACTIVE to true))
        f.restore(f.saved(), 9000)
        assertEquals(0, f.player.savedHeartCooldownTicks)
        assertEquals(0, f.player.saturatedHeartExpiresAt)
        assertFalse(f.player.savedSaturatedHeartActive)
        assertEquals(0, f.player.vars["varbit.imbued_heart_timer"])
    }

    @Test
    fun `activation retains native boosts cooldowns and buff packets for both hearts`() {
        for ((type, duration, level) in listOf(Triple(HeartType.Imbued, 700, 109), Triple(HeartType.Saturated, 500, 112))) {
            val f = PotionLifecycleFixture()
            val heart = f.injector.getInstance(HeartEffectService::class.java)
            heart.activate(f.access(), type)
            assertEquals(level, f.player.stat("stat.magic"))
            assertEquals(1000 + duration, f.player.heartCooldownExpiresAt)
            assertEquals(duration, f.player.savedHeartCooldownTicks)
            assertEquals(duration / 10, f.player.vars["varbit.imbued_heart_timer"])
            assertEquals(type == HeartType.Saturated, StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
            assertEquals(1000 + duration, f.timer(HeartEffectService.TIMER))
            val packet = f.client.messages.filterIsInstance<RunClientScript>().single()
            assertEquals(RunClientScript(5931, listOf(3083, 1000)).toString(), packet.toString())
        }
    }

    @Test
    fun `reuse refusal preserves the original timer boost and rounded time message`() {
        val f = PotionLifecycleFixture()
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Imbued)
        f.at(1599)
        heart.activate(f.access(), HeartType.Saturated)
        assertEquals(109, f.player.stat("stat.magic"))
        assertEquals(1700, f.player.heartCooldownExpiresAt)
        assertEquals(1700, f.timer(HeartEffectService.TIMER))
        assertEquals(1, f.client.messages.filterIsInstance<RunClientScript>().size)
        assertTrue(f.client.messages.any { it.toString().contains("1 minute and 1 second") })
    }

    @Test
    fun `stale callbacks reschedule the exact remaining reuse delay`() {
        val f = PotionLifecycleFixture()
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Saturated)
        f.at(1021)
        heart.process(f.access())
        assertEquals(1500, f.timer(HeartEffectService.TIMER))
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertEquals(112, f.player.stat("stat.magic"))
    }

    @Test
    fun `saturated expiry removes only positive boosts and clears all owned state`() {
        for (level in listOf(80, 99, 112)) {
            val f = PotionLifecycleFixture()
            val heart = f.injector.getInstance(HeartEffectService::class.java)
            heart.activate(f.access(), HeartType.Saturated)
            f.level("stat.magic", level)
            f.at(1500)
            heart.process(f.access())
            assertEquals(minOf(99, level), f.player.stat("stat.magic"))
            assertEquals(0, f.player.heartCooldownExpiresAt)
            assertEquals(0, f.player.saturatedHeartExpiresAt)
            assertEquals(0, f.player.savedHeartCooldownTicks)
            assertFalse(f.player.savedSaturatedHeartActive)
            assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
            assertEquals(0, f.player.vars["varbit.imbued_heart_timer"])
            assertNull(f.timer(HeartEffectService.TIMER))
        }
    }

    @Test
    fun `imbued expiry and cancelled saturation preserve independently maintained boosts`() {
        for (type in HeartType.entries) {
            val f = PotionLifecycleFixture()
            val heart = f.injector.getInstance(HeartEffectService::class.java)
            heart.activate(f.access(), type)
            heart.cancelMaintainedBoost(f.player)
            f.level("stat.magic", 120)
            StatBoostDecayPrevention.add(f.player, "stat.magic", "unrelated")
            f.at(1700)
            heart.process(f.access())
            assertEquals(120, f.player.stat("stat.magic"))
            assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        }
    }

    @Test
    fun `autosaves capture exact remaining heart time without packets or runtime changes`() {
        val f = PotionLifecycleFixture()
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Saturated)
        f.at(1041)
        val messageCount = f.client.messages.size
        f.prepare()
        assertEquals(459, f.player.savedHeartCooldownTicks)
        assertTrue(f.player.savedSaturatedHeartActive)
        assertEquals(1500, f.player.saturatedHeartExpiresAt)
        assertEquals(1500, f.timer(HeartEffectService.TIMER))
        assertEquals(messageCount, f.client.messages.size)
        f.restore(f.saved(), 9000)
        assertEquals(9459, f.player.heartCooldownExpiresAt)
        assertEquals(9459, f.player.saturatedHeartExpiresAt)
        assertEquals(9459, f.timer(HeartEffectService.TIMER))
        assertEquals(46, f.player.vars["varbit.imbued_heart_timer"])
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
    }

    @Test
    fun `logout save retries keep heart time paused while disconnected`() {
        val f = PotionLifecycleFixture()
        f.injector.getInstance(HeartEffectService::class.java).activate(f.access(), HeartType.Saturated)
        f.at(1041)
        f.logout()
        val saved = f.saved()
        f.at(8000)
        f.prepare()
        assertEquals(saved, f.saved())
        f.restore(saved, 9000)
        assertEquals(9459, f.player.heartCooldownExpiresAt)
        assertEquals(9459, f.player.saturatedHeartExpiresAt)
    }

    @Test
    fun `an expired autosave cannot revive saturation or consume the pending expiry cleanup`() {
        val f = PotionLifecycleFixture()
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Saturated)
        f.at(1500)
        f.prepare()
        val saved = f.saved()
        assertEquals(0, f.player.savedHeartCooldownTicks)
        assertFalse(f.player.savedSaturatedHeartActive)
        assertEquals(1500, f.player.saturatedHeartExpiresAt)
        assertEquals(112, f.player.stat("stat.magic"))
        heart.process(f.access())
        assertEquals(99, f.player.stat("stat.magic"))
        f.restore(saved, 9000)
        assertEquals(0, f.player.heartCooldownExpiresAt)
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertNull(f.timer(HeartEffectService.TIMER))
    }

    @Test
    fun `death stops saturated maintenance while preserving reuse across saves`() {
        val f = PotionLifecycleFixture()
        f.injector.getInstance(HeartEffectService::class.java).activate(f.access(), HeartType.Saturated)
        f.at(1037)
        f.death()
        f.prepare()
        assertEquals(1500, f.player.heartCooldownExpiresAt)
        assertEquals(1500, f.timer(HeartEffectService.TIMER))
        assertEquals(0, f.player.saturatedHeartExpiresAt)
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertEquals(463, f.player.savedHeartCooldownTicks)
        assertFalse(f.player.savedSaturatedHeartActive)
        f.restore(f.saved(), 9000)
        assertEquals(9463, f.player.heartCooldownExpiresAt)
        assertEquals(0, f.player.saturatedHeartExpiresAt)
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
    }

    @Test
    fun `heart deadlines are private session vars and only snapshots and migration persist`() {
        for ((name, scope) in mapOf(
            "heart_cooldown_expires_at" to VarpLifetime.Temp,
            "saturated_heart_expires_at" to VarpLifetime.Temp,
            "heart_cooldown_remaining" to VarpLifetime.Perm,
            "saturated_heart_active" to VarpLifetime.Perm,
            "heart_migrated" to VarpLifetime.Perm,
        )) {
            val definition = ServerCacheManager.getVarp("varp.consumables_$name".asRSCM())!!
            assertEquals(scope, definition.scope)
            assertEquals(VarpTransmitLevel.Never, definition.transmit)
        }
    }

    companion object {
        private const val LEGACY_TICKS = "consumables.heart.cooldown_remaining"
        private const val LEGACY_ACTIVE = "consumables.heart.saturated_active"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
