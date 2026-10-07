package org.rsmod.content.other.consumables.potion

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
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.stat
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.other.consumables.ConsumablesModule
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.content.other.consumables.heart.HeartType
import org.rsmod.content.other.consumables.heart.heartCooldownExpiresAt
import org.rsmod.content.other.consumables.heart.saturatedHeartExpiresAt
import org.rsmod.content.other.consumables.heart.savedSaturatedHeartActive
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class MenaphiteRemedyStateTest {
    @Test
    fun `a dose immediately restores six plus sixteen percent of each combat stat only`() {
        val f = Fixture()
        for (stat in RESTORED + EXCLUDED) f.level(stat, base = 50, current = 10)

        f.drink()

        for (stat in RESTORED) assertEquals(24, f.player.stat(stat), stat)
        for (stat in EXCLUDED) assertEquals(10, f.player.stat(stat), stat)
        assertEquals(1500, f.player.menaphiteRemedyExpiresAt)
        assertEquals(500, f.player.savedMenaphiteRemedyTicks)
        assertEquals(1025, f.timerExpiry())
    }

    @Test
    fun `restoration rounds down the percentage caps at base and leaves existing boosts alone`() {
        val f = Fixture()
        f.level("stat.attack", base = 99, current = 0)
        f.level("stat.strength", base = 50, current = 49)
        f.level("stat.defence", base = 50, current = 50)
        f.level("stat.ranged", base = 50, current = 55)
        f.level("stat.magic", base = 1, current = 0)

        f.drink()

        assertEquals(21, f.player.stat("stat.attack"))
        assertEquals(50, f.player.stat("stat.strength"))
        assertEquals(50, f.player.stat("stat.defence"))
        assertEquals(55, f.player.stat("stat.ranged"))
        assertEquals(1, f.player.stat("stat.magic"))
    }

    @Test
    fun `bound timer restores every twenty five ticks and the five hundredth tick only expires`() {
        val f = Fixture()
        f.level("stat.attack", base = 99, current = 0)
        f.drink()
        var pulses = 0
        for (cycle in 1001..1500) {
            f.level("stat.attack", base = 99, current = 0)
            f.at(cycle)
            f.fireDueTimer()
            if (f.player.stat("stat.attack") > 0) {
                pulses++
                assertEquals(0, (cycle - 1000) % 25)
                assertEquals(21, f.player.stat("stat.attack"))
            }
            if (cycle < 1500) assertEquals(1500, f.player.menaphiteRemedyExpiresAt)
        }

        assertEquals(19, pulses)
        assertEquals(0, f.player.stat("stat.attack"))
        f.assertInactive()
        f.reload()
        f.login()
        f.assertInactive()
    }

    @Test
    fun `renewal restarts both duration and pulse interval`() {
        val f = Fixture()
        f.level("stat.attack", base = 50, current = 0)
        f.drink()
        f.at(1024)
        f.drink()
        assertEquals(28, f.player.stat("stat.attack"))
        assertEquals(1524, f.player.menaphiteRemedyExpiresAt)
        assertEquals(1049, f.timerExpiry())

        f.at(1025)
        f.fireDueTimer()
        assertEquals(28, f.player.stat("stat.attack"))
        f.at(1049)
        f.fireDueTimer()
        assertEquals(42, f.player.stat("stat.attack"))
        assertEquals(1074, f.timerExpiry())
    }

    @Test
    fun `login imports exact old duration using the new clock without an immediate restoration`() {
        val f = Fixture()
        f.player.currentMapClock = 0
        f.level("stat.attack", base = 50, current = 10)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 137, "unrelated" to 9))

        f.login()

        assertEquals(1137, f.player.menaphiteRemedyExpiresAt)
        assertEquals(137, f.player.savedMenaphiteRemedyTicks)
        assertEquals(1025, f.timerExpiry())
        assertEquals(10, f.player.stat("stat.attack"))
        assertEquals(1, f.player.vars[MIGRATED])
        assertEquals(mapOf("unrelated" to 9), f.player.attr.toPersistentMap())
    }

    @Test
    fun `logout stores exact ticks and relog restarts the pulse phase without consuming offline time`() {
        val f = Fixture()
        f.drink()
        f.at(1011)
        f.logout()

        assertEquals(489, f.player.savedMenaphiteRemedyTicks)
        val persisted = f.permanentVars()
        assertEquals(489, persisted[REMAINING.asRSCM()])
        assertEquals(1, persisted[MIGRATED.asRSCM()])
        assertFalse(EXPIRY.asRSCM() in persisted)
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
        f.reload()
        assertEquals(0, f.player.menaphiteRemedyExpiresAt)
        f.clock.cycle = 9000
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 500))

        f.login()

        assertEquals(9489, f.player.menaphiteRemedyExpiresAt)
        assertEquals(9025, f.timerExpiry())
        assertEquals(489, f.player.savedMenaphiteRemedyTicks)
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
    }

    @Test
    fun `short restored durations schedule their expiry without a final restoration`() {
        for (duration in listOf(1, 24, 25, 26)) {
            val f = Fixture()
            f.player.attr.putAllFromPersistence(mapOf(LEGACY to duration))
            f.level("stat.attack", base = 50, current = 0)
            f.login()
            assertEquals(1000 + minOf(duration, 25), f.timerExpiry())
            if (duration == 26) {
                f.at(1025)
                f.fireDueTimer()
                assertEquals(14, f.player.stat("stat.attack"))
                assertEquals(1026, f.timerExpiry())
            }
            f.level("stat.attack", base = 50, current = 0)
            f.at(1000 + duration)
            f.fireDueTimer()
            assertEquals(0, f.player.stat("stat.attack"))
            f.assertInactive()
        }
    }

    @Test
    fun `fresh zero and negative legacy durations all remain inactive`() {
        for (remaining in listOf(null, 0, -20)) {
            val f = Fixture()
            if (remaining != null) f.player.attr.putAllFromPersistence(mapOf(LEGACY to remaining))
            f.login()
            f.assertInactive()
            assertFalse(LEGACY in f.player.attr.toPersistentMap())
        }
    }

    @Test
    fun `explicit current duration including zero wins before the migration marker is written`() {
        for (remaining in listOf(0, 77)) {
            val f = Fixture()
            f.player.savedMenaphiteRemedyTicks = remaining
            f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))

            f.login()

            assertEquals(remaining, f.player.savedMenaphiteRemedyTicks)
            if (remaining == 0) f.assertInactive()
            else assertEquals(1077, f.player.menaphiteRemedyExpiresAt)
            assertEquals(1, f.player.vars[MIGRATED])
            assertFalse(LEGACY in f.player.attr.toPersistentMap())
        }
    }

    @Test
    fun `a migration marker blocks stale legacy duration even when the remaining varp is absent`() {
        val f = Fixture()
        MenaphiteRemedyState.clear(f.player)
        f.player.vars.backing.remove(REMAINING.asRSCM())
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))

        f.login()

        f.assertInactive()
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
    }

    @Test
    fun `a new dose replaces legacy duration and does not restore it on a later login`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 200))
        f.drink()
        assertEquals(500, f.player.savedMenaphiteRemedyTicks)
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
        f.at(1007)
        f.logout()
        f.reload()
        f.clock.cycle = 7000
        f.login()
        assertEquals(7493, f.player.menaphiteRemedyExpiresAt)
    }

    @Test
    fun `missing runtime state or a late callback clears saved duration without restoring stats`() {
        for (cycle in listOf(1000, 1500, 1600)) {
            val f = Fixture()
            f.drink()
            if (cycle == 1000) f.player.menaphiteRemedyExpiresAt = 0
            f.level("stat.attack", base = 50, current = 0)
            f.at(cycle)

            f.specialEffects.processMenaphiteRemedy(f.access())

            assertEquals(0, f.player.stat("stat.attack"))
            f.assertInactive()
        }
    }

    @Test
    fun `logout after expiry clears pending timer and saves an explicit inactive state`() {
        val f = Fixture()
        f.drink()
        f.at(1505)

        f.logout()

        f.assertInactive()
        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 500))
        f.login()
        f.assertInactive()
    }

    @Test
    fun `remedy cancels saturated heart maintenance while keeping its cooldown and existing boost`() {
        val f = Fixture()
        f.level("stat.magic", base = 50, current = 50)
        val heart = f.injector.getInstance(HeartEffectService::class.java)
        heart.activate(f.access(), HeartType.Saturated)
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertEquals(59, f.player.stat("stat.magic"))
        val cooldown = f.player.heartCooldownExpiresAt
        StatBoostDecayPrevention.add(f.player, "stat.attack", "unrelated")

        f.drink()

        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.magic"))
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        assertFalse(f.player.savedSaturatedHeartActive)
        assertEquals(0, f.player.saturatedHeartExpiresAt)
        assertEquals(cooldown, f.player.heartCooldownExpiresAt)
        assertTrue(HeartEffectService.TIMER in f.player.timerMap)
        assertEquals(59, f.player.stat("stat.magic"))
    }

    @Test
    fun `bound death cleanup clears runtime saved and legacy remedy without touching other effects`() {
        val f = Fixture()
        f.drink()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 400, "unrelated" to 11))
        f.player.staminaExpiresAt = 1600
        f.player.savedStaminaTicks = 600
        val divine = mutableMapOf("retained" to 73)
        f.player.attr[DivineState.savedDivineTicks] = divine
        val hooks = f.injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))

        hooks.single { it is MenaphiteRemedyDeathCleanupHook }.cleanup(f.player)

        f.assertInactive()
        assertEquals(1600, f.player.staminaExpiresAt)
        assertEquals(600, f.player.savedStaminaTicks)
        assertSame(divine, f.player.attr[DivineState.savedDivineTicks])
        assertEquals(11, f.player.attr.toPersistentMap()["unrelated"])
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
        f.logout()
        f.reload()
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to 400))
        f.clock.cycle = 9000
        f.login()
        f.assertInactive()
        assertFalse(LEGACY in f.player.attr.toPersistentMap())
    }

    @Test
    fun `packed expiry is temporary and remaining duration and migration marker are permanent private varps`() {
        for ((symbol, scope) in mapOf(
            EXPIRY to VarpLifetime.Temp,
            REMAINING to VarpLifetime.Perm,
            MIGRATED to VarpLifetime.Perm,
        )) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(scope, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    private class Fixture {
        val clock = MapClock(1000)
        val injector = Guice.createInjector(ConsumablesModule(), object : AbstractModule() {
            override fun configure() { bind(MapClock::class.java).toInstance(clock) }
        })
        val specialEffects = injector.getInstance(PotionSpecialEffectService::class.java)
        private val effects = injector.getInstance(PotionEffectService::class.java)
        private val client = RecordingClient()
        var player = newPlayer()
        private val events = EventBus().also { events ->
            with(injector.getInstance(PotionEffectTimerScript::class.java)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        private fun newPlayer() = Player(client).apply {
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            coords = CoordGrid(3222, 3218)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
        }

        fun at(cycle: Int) {
            clock.cycle = cycle
            player.currentMapClock = cycle
            player.processedMapClock = cycle
        }

        fun level(stat: String, base: Int, current: Int) {
            player.statMap.setBaseLevel(stat, base.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
        }

        fun drink() = effects.apply(access(), PotionEffectRow.getRow("dbrow.effect_menaphite_remedy"))
        fun login() { events.publish(SessionStateEvent.Login(player)) }
        fun logout() { events.publish(SessionStateEvent.Logout(player)) }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())

        fun timerExpiry(): Int {
            val packed = checkNotNull(player.timerMap[TIMER.asRSCM().toShort()])
            return player.timerMap.extractExpiry(packed)
        }

        fun fireDueTimer() {
            if (TIMER !in player.timerMap || clock.cycle < timerExpiry()) return
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, ProtectedAccessContextFactory.empty()) {
                events.publish(this, PlayerTimerEvent.Normal(TIMER.asRSCM()))
            })
        }

        fun permanentVars() = player.vars.backing.filterKeys { id ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        }

        fun reload() {
            val vars = permanentVars()
            val attrs = player.attr.toPersistentMap()
            player = newPlayer().apply {
                this.vars.backing.putAll(vars)
                attr.putAllFromPersistence(attrs)
            }
        }

        fun assertInactive() {
            assertEquals(0, player.menaphiteRemedyExpiresAt)
            assertEquals(0, player.savedMenaphiteRemedyTicks)
            assertEquals(1, player.vars[MIGRATED])
            assertFalse(TIMER in player.timerMap)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val EXPIRY = "varp.consumables_menaphite_remedy_expires_at"
        private const val REMAINING = "varp.consumables_menaphite_remedy_remaining"
        private const val MIGRATED = "varp.consumables_menaphite_remedy_migrated"
        private const val LEGACY = "consumables.potion.menaphite_remedy_remaining"
        private const val TIMER = PotionSpecialEffectService.MENAPHITE_REMEDY_TIMER
        private val RESTORED = listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
        private val EXCLUDED = listOf("stat.hitpoints", "stat.prayer", "stat.agility", "stat.slayer")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
