package org.rsmod.content.other.consumables.potion

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathCleanupHook
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
class StaminaStateTest {
    @Test
    fun `login imports old remaining duration against world time and schedules the native countdown`() {
        val f = Fixture()
        f.player.currentMapClock = 0
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 137, "unrelated" to 7))

        f.login()

        assertEquals(1137, f.player.staminaExpiresAt)
        assertEquals(137, f.player.savedStaminaTicks)
        assertEquals(1, f.player.vars[MIGRATED])
        assertEquals(1, f.player.vars[ACTIVE])
        assertEquals(14, f.player.vars[DURATION])
        assertEquals(1112, f.timerExpiry())
        assertEquals(mapOf("unrelated" to 7), f.player.attr.toPersistentMap())
    }

    @Test
    fun `logout saves exact ticks and reconnect resumes that duration without counting offline time`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 137))
        f.login()
        f.at(1037)

        f.logout()

        assertEquals(100, f.player.savedStaminaTicks)
        assertEquals(10, f.player.vars[DURATION])
        f.reload()
        assertEquals(0, f.player.staminaExpiresAt)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))
        f.clock.cycle = 9000
        f.login()
        assertEquals(9100, f.player.staminaExpiresAt)
        assertEquals(100, f.player.savedStaminaTicks)
        assertEquals(9075, f.timerExpiry())
        assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY))
    }

    @Test
    fun `new accounts negative legacy duration and explicit zero stay inactive`() {
        for (legacy in listOf(null, -20, 150)) {
            val f = Fixture()
            if (legacy != null) f.player.attr.putAllFromPersistence(mapOf(LEGACY to legacy))
            if (legacy == 150) f.player.savedStaminaTicks = 0

            f.login()

            f.assertInactive()
            assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY))
        }
    }

    @Test
    fun `existing duration wins over old attributes and short restored effects expire without a repeat warning`() {
        val f = Fixture()
        f.player.savedStaminaTicks = 20
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))

        f.login()

        assertEquals(1020, f.player.staminaExpiresAt)
        assertEquals(1020, f.timerExpiry())
        f.at(1020)
        f.process()
        f.assertInactive()
        assertFalse(f.messages().any { "about to expire" in it })
        assertEquals(1, f.messages().count { "Your stamina enhancement has expired." in it })
    }

    @Test
    fun `a fresh dose refreshes expiry display and timer while stale callbacks cannot warn early`() {
        val f = Fixture()
        val effect = PotionEffectRow.getRow("dbrow.effect_stamina")
        assertTrue(effect.stamina)
        assertTrue(effect.duration > 100)
        f.drink(effect)
        assertEquals(1000 + effect.duration, f.player.staminaExpiresAt)
        assertEquals(durationUnits(effect.duration, 10), f.player.vars[DURATION])
        assertEquals(1, f.player.vars[ACTIVE])
        assertTrue(f.client.messages.filterIsInstance<RunClientScript>().any { it.id == 5931 })

        f.at(1050)
        f.drink(effect)
        f.at(1000 + effect.duration - 25)
        f.process()
        assertFalse(f.messages().any { "about to expire" in it })
        assertEquals(1050 + effect.duration - 25, f.timerExpiry())

        f.at(1050 + effect.duration - 25)
        f.process()
        assertEquals(1, f.messages().count { "Your stamina enhancement is about to expire." in it })
        assertEquals(1050 + effect.duration, f.timerExpiry())
        f.at(1050 + effect.duration)
        f.process()
        f.assertInactive()
        assertEquals(1, f.messages().count { "Your stamina enhancement has expired." in it })
    }

    @Test
    fun `expired effects cannot return from stale saved attributes even before the next logout`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 30))
        f.login()
        f.at(1030)

        f.process()

        f.assertInactive()
        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 30))
        f.login()
        f.assertInactive()
        assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY))
    }

    @Test
    fun `clear before migration rejects legacy duration and remains cleared after reconnect`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))

        StaminaState.clear(f.player)

        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))
        repeat(2) { f.login() }
        f.assertInactive()
        assertFalse(f.player.attr.toPersistentMap().containsKey(LEGACY))
    }

    @Test
    fun `bound death cleanup clears stamina timing and display while leaving other potion state alone`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 137))
        f.login()
        AntifireState.NORMAL.setRemaining(f.player, 123)
        val divineTicks = mutableMapOf("retained_divine" to 50)
        f.player.attr[DivineState.savedDivineTicks] = divineTicks
        f.player.timer(PotionEffectService.ANTIFIRE_TIMER, 50)
        val hooks = f.injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))

        val staminaHook = hooks.single { it is StaminaDeathCleanupHook }
        staminaHook.cleanup(f.player)

        f.assertInactive()
        assertEquals(123, AntifireState.NORMAL.remaining(f.player))
        assertSame(divineTicks, f.player.attr[DivineState.savedDivineTicks])
        assertTrue(PotionEffectService.ANTIFIRE_TIMER in f.player.timerMap)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 137))
        StaminaState.migrate(f.player)
        f.assertInactive()
    }

    @Test
    fun `logout after an unprocessed expiry saves zero and does not revive the effect`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 30))
        f.login()
        f.at(1040)

        f.logout()

        assertEquals(0, f.player.savedStaminaTicks)
        assertEquals(0, f.player.vars[ACTIVE])
        assertEquals(0, f.player.vars[DURATION])
        f.reload()
        f.login()
        f.assertInactive()
    }

    @Test
    fun `migration changes only legacy stamina while packed fields have the required lifetimes`() {
        val player = Player()
        val retained = mapOf<String, Any>(
            "consumables.potion.antifire_remaining" to 73,
            "consumables.potion.divine_remaining" to mapOf("effect" to 41),
            "unrelated" to 7,
        )
        player.attr.putAllFromPersistence(retained + (LEGACY to 111))
        repeat(2) { StaminaState.migrate(player) }
        assertEquals(111, player.savedStaminaTicks)
        assertEquals(retained, player.attr.toPersistentMap())

        for ((symbol, lifetime) in mapOf(
            "varp.consumables_stamina_expires_at" to VarpLifetime.Temp,
            "varp.consumables_stamina_remaining" to VarpLifetime.Perm,
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
        val client = RecordingClient()
        var player = Player(client).apply { currentMapClock = clock.cycle }
        private val events = EventBus().also { events ->
            with(injector.getInstance(PotionEffectTimerScript::class.java)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun at(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle }
        fun login() { events.publish(SessionStateEvent.Login(player)) }
        fun logout() { events.publish(SessionStateEvent.Logout(player)) }
        fun process() { effects.processStamina(access()) }
        fun drink(effect: PotionEffectRow) { effects.apply(access(), effect) }
        fun messages() = client.messages.map(Any::toString)
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())

        fun reload() {
            val original = player
            player = Player(client).apply {
                vars.backing.putAll(original.vars.backing.filterKeys { id ->
                    ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
                })
                attr.putAllFromPersistence(original.attr.toPersistentMap())
            }
        }

        fun timerExpiry(): Int {
            val packed = checkNotNull(player.timerMap[TIMER.asRSCM().toShort()])
            return player.timerMap.extractExpiry(packed)
        }

        fun assertInactive() {
            assertEquals(0, player.staminaExpiresAt)
            assertEquals(0, player.savedStaminaTicks)
            assertEquals(0, player.vars[ACTIVE])
            assertEquals(0, player.vars[DURATION])
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
        private const val LEGACY = "consumables.potion.stamina_remaining"
        private const val MIGRATED = "varp.consumables_stamina_migrated"
        private const val ACTIVE = PotionEffectService.STAMINA_VARBIT
        private const val DURATION = PotionEffectService.STAMINA_DURATION_VARBIT
        private const val TIMER = PotionEffectService.STAMINA_TIMER

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
