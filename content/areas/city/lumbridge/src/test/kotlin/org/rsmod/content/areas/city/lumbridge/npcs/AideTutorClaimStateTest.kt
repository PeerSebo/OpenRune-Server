package org.rsmod.content.areas.city.lumbridge.npcs

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.areas.city.lumbridge.LumbridgeModule
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class AideTutorClaimStateTest {
    @Test
    fun `a claim is available exactly at the shared cooldown deadline`() {
        val f = Fixture()
        f.login()
        assertFalse(f.onCooldown())

        f.claim()

        assertEquals(4000, f.player.aideTutorClaimReadyAt)
        assertEquals(3000, f.player.aideTutorClaimRemainingTicks)
        f.at(3999)
        assertTrue(f.onCooldown())
        f.at(4000)
        assertFalse(f.onCooldown())
        f.prepare()
        assertEquals(0, f.player.aideTutorClaimRemainingTicks)
    }

    @Test
    fun `bound autosave hook snapshots remaining ticks without resetting the active deadline`() {
        val f = Fixture()
        f.login()
        f.claim()
        f.at(1041)

        f.prepare()

        assertEquals(2959, f.player.aideTutorClaimRemainingTicks)
        assertEquals(4000, f.player.aideTutorClaimReadyAt)
        assertTrue(f.onCooldown())
        val saved = f.saved()
        assertFalse(READY.asRSCM() in saved.vars)
        assertFalse(LEGACY in saved.attrs)
        f.restore(saved, 10)
        assertEquals(2969, f.player.aideTutorClaimReadyAt)
        f.at(2968)
        assertTrue(f.onCooldown())
        f.at(2969)
        assertFalse(f.onCooldown())
    }

    @Test
    fun `logout and repeated delayed save attempts preserve the paused duration`() {
        val f = Fixture()
        f.login()
        f.claim()
        f.at(1200)
        f.logout()
        val paused = f.saved()
        assertEquals(2800, f.player.aideTutorClaimRemainingTicks)
        assertEquals(0, f.player.aideTutorClaimReadyAt)

        f.at(1800)
        f.logout()
        f.prepare()
        f.at(20000)
        f.prepare()

        assertEquals(paused, f.saved())
        f.restore(paused, 30)
        assertEquals(2830, f.player.aideTutorClaimReadyAt)
    }

    @Test
    fun `legacy saves migrate once using the login world clock and remove the old attribute`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 1500))
        f.player.currentMapClock = -1

        f.login()

        assertEquals(2500, f.player.aideTutorClaimReadyAt)
        assertEquals(1500, f.player.aideTutorClaimRemainingTicks)
        assertEquals(1, f.player.vars[MIGRATED])
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 2900))
        f.player.currentMapClock = 1000
        f.prepare()
        assertEquals(1500, f.player.aideTutorClaimRemainingTicks)
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
    }

    @Test
    fun `save preparation before runtime restoration preserves a legacy duration`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 1500))

        f.prepare()

        assertEquals(1500, f.player.aideTutorClaimRemainingTicks)
        assertFalse(READY.asRSCM() in f.saved().vars)
        f.restore(f.saved(), 10)
        assertEquals(1510, f.player.aideTutorClaimReadyAt)
    }

    @Test
    fun `explicit new zero and positive saved values take priority over legacy values`() {
        for (remaining in listOf(0, 480)) {
            val f = Fixture()
            f.restore(Saved(mapOf(REMAINING.asRSCM() to remaining), mapOf(LEGACY to 2100)), 100)
            assertEquals(remaining, f.player.aideTutorClaimRemainingTicks)
            assertEquals(if (remaining == 0) 0 else 580, f.player.aideTutorClaimReadyAt)
            assertFalse(LEGACY in f.player.attr.toPersistentMap())
        }
    }

    @Test
    fun `permanent migration marker prevents an obsolete attribute reviving a cleared cooldown`() {
        val f = Fixture()
        f.restore(Saved(mapOf(MIGRATED.asRSCM() to 1), mapOf(LEGACY to 2000)), 100)

        assertFalse(f.onCooldown())
        assertEquals(0, f.player.aideTutorClaimRemainingTicks)
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
    }

    @Test
    fun `expired and negative legacy durations cannot restart on a new clock`() {
        val f = Fixture()
        f.restore(Saved(emptyMap(), mapOf(LEGACY to -500)), 100)
        assertFalse(f.onCooldown())
        assertEquals(0, f.player.aideTutorClaimRemainingTicks)

        f.claim()
        f.at(6000)
        f.logout()
        f.restore(f.saved(), 10)
        assertFalse(f.onCooldown())
        assertEquals(0, f.player.aideTutorClaimRemainingTicks)
    }

    @Test
    fun `a later claim replaces an expired autosave without changing its old snapshot`() {
        val f = Fixture()
        f.login()
        f.claim()
        f.at(3500)
        f.prepare()
        val prior = f.saved()

        f.at(4000)
        f.claim()
        f.at(4070)
        f.prepare()
        val current = f.saved()

        f.restore(prior, 10)
        assertEquals(510, f.player.aideTutorClaimReadyAt)
        f.restore(current, 10)
        assertEquals(2940, f.player.aideTutorClaimReadyAt)
    }

    @Test
    fun `cooldowns remain isolated and their storage is server only with the proper lifetime`() {
        val first = Fixture()
        val second = Fixture()
        first.login()
        second.login()
        first.claim()
        assertFalse(second.onCooldown())
        assertEquals(0, second.player.aideTutorClaimRemainingTicks)
        for ((symbol, lifetime) in listOf(
            READY to VarpLifetime.Temp,
            REMAINING to VarpLifetime.Perm,
            MIGRATED to VarpLifetime.Perm,
        )) {
            val type = requireNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(lifetime, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    private class Fixture {
        val clock = MapClock(1000)
        private val injector = Guice.createInjector(LumbridgeModule(), object : AbstractModule() {
            override fun configure() { bind(MapClock::class.java).toInstance(clock) }
        })
        private val events = EventBus().also {
            val context = ScriptContext(it, CheatCommandMap(), EngineQueueCache())
            with(injector.getInstance(AideTutorClaimPersistenceScript::class.java)) { context.startup() }
        }
        var player = newPlayer()

        private fun newPlayer() = Player().apply {
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
        }

        fun at(tick: Int) {
            clock.cycle = tick
            player.currentMapClock = tick
            player.processedMapClock = tick
        }

        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }

        fun logout() {
            player.loggingOut = true
            assertTrue(events.publish(SessionStateEvent.Logout(player)))
        }

        fun claim() = access().markAideTutorClaimed()
        fun onCooldown() = access().aideTutorClaimOnCooldown()

        fun prepare() {
            val hooks = injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerSavePreparationHook>>() {}))
            hooks.single { it is AideTutorClaimSavePreparationHook }.prepare(player)
        }

        fun saved() = Saved(
            player.vars.backing.filterKeys { ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm },
            player.attr.toPersistentMap(),
        )

        fun restore(saved: Saved, tick: Int) {
            clock.cycle = tick
            player = newPlayer().apply {
                vars.backing.putAll(saved.vars)
                attr.putAllFromPersistence(saved.attrs)
            }
            login()
        }

        private fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
    }

    private data class Saved(val vars: Map<Int, Int>, val attrs: Map<String, Any>)

    companion object {
        private const val LEGACY = "aide_tutor_claim_remaining_ticks"
        private const val READY = "varp.lumbridge_tutor_claim_ready_at"
        private const val REMAINING = "varp.lumbridge_tutor_claim_remaining_ticks"
        private const val MIGRATED = "varp.lumbridge_tutor_claim_migrated"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
