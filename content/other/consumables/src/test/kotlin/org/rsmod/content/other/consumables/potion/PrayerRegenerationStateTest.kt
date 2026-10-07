package org.rsmod.content.other.consumables.potion

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.openrune.types.varp.baseVar
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.other.consumables.ConsumablesModule
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class PrayerRegenerationStateTest {
    @Test
    fun `old saves restore pulse count and phase against the new world clock`() {
        val f = Fixture()
        f.player.currentMapClock = 0
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 43, LEGACY_DELAY to 7))

        f.login()

        assertEquals(43, f.player.prayerRegenerationPulses)
        assertEquals(43, f.player.savedPrayerRegenerationPulses)
        assertEquals(7, f.player.savedPrayerRegenerationDelay)
        assertEquals(1007, f.player.prayerRegenerationNextPulseAt)
        assertEquals(1007, f.timerExpiry())
        assertEquals(43, f.player.vars[DISPLAY])
        assertEquals(1, f.player.vars[MIGRATED])
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        assertTrue(f.client.messages.none { it is RunClientScript })
    }

    @Test
    fun `missing and out of range legacy phases stay within one pulse interval`() {
        for ((oldDelay, delay) in listOf(null to 12, -9 to 1, 0 to 1, 27 to 12)) {
            val f = Fixture()
            f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 2))
            if (oldDelay != null) f.player.attr.putAllFromPersistence(mapOf(LEGACY_DELAY to oldDelay))
            f.login()
            assertEquals(delay, f.player.savedPrayerRegenerationDelay)
            assertEquals(1000 + delay, f.timerExpiry())
        }
    }

    @Test
    fun `explicit new zero and inactive legacy counts cannot activate regeneration`() {
        for (oldCount in listOf(null, -5, 0, 60)) {
            val f = Fixture()
            if (oldCount != null) f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to oldCount, LEGACY_DELAY to 4))
            if (oldCount == 60) f.player.savedPrayerRegenerationPulses = 0
            f.login()
            f.assertInactive()
            assertTrue(f.player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test
    fun `new persisted fields independently override their legacy equivalents`() {
        val f = Fixture()
        f.player.savedPrayerRegenerationPulses = 15
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 60, LEGACY_DELAY to 5))
        f.login()
        assertEquals(15, f.player.prayerRegenerationPulses)
        assertEquals(1005, f.timerExpiry())

        val g = Fixture()
        g.player.savedPrayerRegenerationDelay = 0
        g.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 23, LEGACY_DELAY to 11))
        g.login()
        assertEquals(23, g.player.prayerRegenerationPulses)
        assertEquals(0, g.player.savedPrayerRegenerationDelay)
        assertEquals(1001, g.timerExpiry())
    }

    @Test
    fun `migration is idempotent and removes stale keys without changing unrelated saved state`() {
        val player = Player()
        val retained = mapOf<String, Any>(
            "consumables.potion.antifire_remaining" to 80,
            "consumables.potion.stamina_remaining" to 150,
            "consumables.potion.divine_remaining" to mapOf("retained_effect" to 41),
            "unrelated" to 7,
        )
        player.attr.putAllFromPersistence(retained + mapOf(LEGACY_PULSES to 19, LEGACY_DELAY to 6))
        PrayerRegenerationState.migrate(player)
        player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 66, LEGACY_DELAY to 12))
        PrayerRegenerationState.migrate(player)
        assertEquals(19, player.savedPrayerRegenerationPulses)
        assertEquals(6, player.savedPrayerRegenerationDelay)
        assertEquals(retained, player.attr.toPersistentMap())
    }

    @Test
    fun `native dose starts sixty six pulses and renewing restarts the full phase and buff`() {
        val f = Fixture()
        val effect = PotionEffectRow.getRow("dbrow.effect_prayer_regeneration")
        assertEquals(800, effect.duration)
        f.drink(effect)
        assertEquals(66, f.player.prayerRegenerationPulses)
        assertEquals(1012, f.timerExpiry())
        assertEquals(66, f.player.vars[DISPLAY])
        f.assertBuff(1000)
        f.assertNativeDisplaySent()

        f.at(1012)
        f.pulse()
        assertEquals(65, f.player.prayerRegenerationPulses)
        f.at(1017)
        f.drink(effect)
        assertEquals(66, f.player.prayerRegenerationPulses)
        assertEquals(1029, f.player.prayerRegenerationNextPulseAt)
        assertEquals(1029, f.timerExpiry())
        assertEquals(66, f.player.vars[DISPLAY])
        f.assertBuff(1017)
        assertEquals(2, f.client.messages.filterIsInstance<RunClientScript>().size)
    }

    @Test
    fun `registered timer restores one prayer each interval and expires after sixty six pulses`() {
        val f = Fixture()
        f.drink()
        repeat(66) { pulse ->
            f.at(1000 + (pulse + 1) * 12)
            f.pulse()
            assertEquals(pulse + 1, f.prayer())
            assertEquals(65 - pulse, f.player.vars[DISPLAY])
            if (pulse < 65) assertEquals(f.clock.cycle + 12, f.timerExpiry())
        }
        assertEquals(1792, f.clock.cycle)
        f.assertInactive()
        assertEquals(1, f.expiryMessages())
        f.pulse()
        assertEquals(66, f.prayer())
        assertEquals(1, f.expiryMessages())
    }

    @Test
    fun `full prayer consumes pulses without over restoring and delayed callbacks do not catch up`() {
        val f = Fixture()
        f.player.statMap.setCurrentLevel("stat.prayer", 99)
        f.drink()
        f.at(1070)
        f.pulse()
        assertEquals(99, f.prayer())
        assertEquals(65, f.player.prayerRegenerationPulses)
        assertEquals(1082, f.timerExpiry())
        f.player.statMap.setCurrentLevel("stat.prayer", 98)
        f.at(1082)
        f.pulse()
        assertEquals(99, f.prayer())
        assertEquals(64, f.player.prayerRegenerationPulses)
    }

    @Test
    fun `logout saves remaining phase and relogin resumes it without counting offline time`() {
        val f = Fixture()
        f.drink()
        f.at(1012)
        f.pulse()
        f.at(1017)
        f.logout()
        assertEquals(65, f.player.savedPrayerRegenerationPulses)
        assertEquals(7, f.player.savedPrayerRegenerationDelay)
        f.reload()
        assertEquals(0, f.player.prayerRegenerationPulses)
        assertEquals(0, f.player.prayerRegenerationNextPulseAt)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 66, LEGACY_DELAY to 12))
        f.clock.cycle = 9000
        f.login()
        assertEquals(65, f.player.prayerRegenerationPulses)
        assertEquals(9007, f.timerExpiry())
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.at(9007)
        f.pulse()
        assertEquals(2, f.prayer())
        assertEquals(64, f.player.prayerRegenerationPulses)
        assertEquals(9019, f.timerExpiry())
    }

    @Test
    fun `logout bounds overdue or missing runtime phase and inactive state clears saved pulses`() {
        for ((next, delay) in listOf(0 to 12, 999 to 1, 1000 to 1, 1003 to 3, 5000 to 12)) {
            val f = Fixture()
            f.player.prayerRegenerationPulses = 5
            f.player.prayerRegenerationNextPulseAt = next
            f.logout()
            assertEquals(5, f.player.savedPrayerRegenerationPulses)
            assertEquals(delay, f.player.savedPrayerRegenerationDelay)
        }
        val f = Fixture()
        f.player.savedPrayerRegenerationPulses = 40
        f.player.savedPrayerRegenerationDelay = 9
        f.logout()
        f.assertInactive()
        f.reload()
        f.login()
        f.assertInactive()
    }

    @Test
    fun `bound death hook clears native runtime and saved state without affecting other effects`() {
        val f = Fixture()
        f.drink()
        f.logout()
        val divineTicks = mutableMapOf("retained_divine" to 50)
        f.player.attr[DivineState.savedDivineTicks] = divineTicks
        AntifireState.NORMAL.setRemaining(f.player, 123)
        f.player.savedStaminaTicks = 140
        f.player.timer(PotionEffectService.ANTIFIRE_TIMER, 50)
        val hooks = f.injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))

        hooks.single { it is PrayerRegenerationDeathCleanupHook }.cleanup(f.player)

        f.assertInactive()
        assertSame(divineTicks, f.player.attr[DivineState.savedDivineTicks])
        assertEquals(123, AntifireState.NORMAL.remaining(f.player))
        assertEquals(140, f.player.savedStaminaTicks)
        assertTrue(PotionEffectService.ANTIFIRE_TIMER in f.player.timerMap)
        f.logout()
        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 66, LEGACY_DELAY to 12))
        f.login()
        f.assertInactive()
        assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY_PULSES))
        assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY_DELAY))
    }

    @Test
    fun `clear before migration rejects legacy state and remains idempotent across saves`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 66, LEGACY_DELAY to 12))
        repeat(2) { f.prayerEffects.clear(f.player) }
        f.assertInactive()
        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY_PULSES to 66, LEGACY_DELAY to 12))
        f.login()
        f.assertInactive()
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `packed timing fields have their correct lifetimes and never transmit to clients`() {
        for ((symbol, lifetime) in mapOf(
            "varp.consumables_prayer_regeneration_pulses" to VarpLifetime.Temp,
            "varp.consumables_prayer_regeneration_next_pulse_at" to VarpLifetime.Temp,
            "varp.consumables_prayer_regeneration_remaining_pulses" to VarpLifetime.Perm,
            "varp.consumables_prayer_regeneration_remaining_delay" to VarpLifetime.Perm,
            MIGRATED to VarpLifetime.Perm,
        )) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(lifetime, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    private class Fixture {
        val clock = MapClock(1000)
        val injector = Guice.createInjector(ConsumablesModule(), object : AbstractModule() {
            override fun configure() { bind(MapClock::class.java).toInstance(clock) }
        })
        val effects = injector.getInstance(PotionEffectService::class.java)
        val prayerEffects = injector.getInstance(PrayerRegenerationEffectService::class.java)
        val client = RecordingClient()
        var player = Player(client).apply {
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            statMap.setBaseLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.prayer", 0)
        }
        private val events = EventBus().also { events ->
            with(injector.getInstance(PotionEffectTimerScript::class.java)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun at(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle }
        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }
        fun logout() { assertTrue(events.publish(SessionStateEvent.Logout(player))) }
        fun drink(effect: PotionEffectRow = PotionEffectRow.getRow("dbrow.effect_prayer_regeneration")) {
            effects.apply(ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty()), effect)
        }
        fun prayer() = player.statMap.getCurrentLevel("stat.prayer").toInt()
        fun expiryMessages() = client.messages.count { "Your Prayer Regeneration potion has worn off." in it.toString() }

        fun pulse() {
            val coroutine = player.launch {
                val access = ProtectedAccess(player, this, ProtectedAccessContextFactory.empty())
                assertTrue(events.publish(access, PlayerTimerEvent.Normal(TIMER.asRSCM())))
            }
            assertTrue(coroutine.isIdle)
        }

        fun reload() {
            val original = player
            player = Player(client).apply {
                vars.backing.putAll(original.vars.backing.filterKeys { id ->
                    ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
                })
                attr.putAllFromPersistence(original.attr.toPersistentMap())
                statMap.setBaseLevel("stat.prayer", original.statMap.getBaseLevel("stat.prayer"))
                statMap.setCurrentLevel("stat.prayer", original.statMap.getCurrentLevel("stat.prayer"))
            }
        }

        fun timerExpiry(): Int {
            val packed = checkNotNull(player.timerMap[TIMER.asRSCM().toShort()])
            return player.timerMap.extractExpiry(packed)
        }

        fun assertBuff(cycle: Int) {
            val buffId = "clientscript.[clientscript,buff_bar_start]".asRSCM()
            val packet = client.messages.filterIsInstance<RunClientScript>().last { it.id == buffId }
            assertEquals(RunClientScript(buffId, listOf(1014, cycle)).toString(), packet.toString())
        }

        fun assertNativeDisplaySent() {
            val type = checkNotNull(ServerCacheManager.getVarbit(DISPLAY.asRSCM())).baseVar
            val packed = player.vars[type]
            val expected = if (packed in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(type.id, packed) else VarpLarge(type.id, packed)
            assertTrue(client.messages.any { it.toString() == expected.toString() })
        }

        fun assertInactive() {
            assertEquals(0, player.prayerRegenerationPulses)
            assertEquals(0, player.prayerRegenerationNextPulseAt)
            assertEquals(0, player.savedPrayerRegenerationPulses)
            assertEquals(0, player.savedPrayerRegenerationDelay)
            assertEquals(0, player.vars[DISPLAY])
            assertEquals(1, player.vars[MIGRATED])
            assertFalse(TIMER in player.timerMap)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val LEGACY_PULSES = "consumables.potion.prayer_regeneration_pulses"
        private const val LEGACY_DELAY = "consumables.potion.prayer_regeneration_next_pulse"
        private const val MIGRATED = "varp.consumables_prayer_regeneration_migrated"
        private const val DISPLAY = PotionEffectService.PRAYER_REGENERATION_VARBIT
        private const val TIMER = PotionEffectService.PRAYER_REGENERATION_TIMER

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
