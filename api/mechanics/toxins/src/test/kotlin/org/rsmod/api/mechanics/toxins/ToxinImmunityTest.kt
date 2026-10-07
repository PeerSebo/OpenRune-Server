package org.rsmod.api.mechanics.toxins

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class ToxinImmunityTest {
    @Test
    fun `cache keeps expiry clocks temporary and saved immunity permanent without transmission`() {
        for (name in RuntimeVars + SavedVars) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(
                if (name in RuntimeVars) VarpLifetime.Temp else VarpLifetime.Perm,
                type.scope,
                name,
            )
            assertEquals(VarpTransmitLevel.Never, type.transmit, name)
        }
    }

    @Test
    fun `login imports legacy durations once and uses the world clock for timers and native bars`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(
            LegacyPoison to 170, LegacyVenom to 50, LegacyWarning to false, "unrelated.state" to 7,
        ))
        f.player.currentMapClock = 2

        f.login(500)

        assertEquals(670, f.value(PoisonUntil))
        assertEquals(550, f.value(VenomUntil))
        assertEquals(0, f.value(DisplayUntil))
        assertEquals(550, f.timerExpiry())
        assertEquals(-40, f.orb)
        assertEquals(listOf(RunClientScript(5931, listOf(3104, 500))), f.scripts)
        assertEquals(1, f.value(Migrated))
        assertEquals(mapOf("unrelated.state" to 7), f.player.attr.toPersistentMap())
        assertTrue(ToxinImmunity.hasPoisonImmunity(f.player))
        assertTrue(ToxinImmunity.hasVenomImmunity(f.player))
    }

    @Test
    fun `explicit zero and existing values win over each corresponding legacy value`() {
        val f = Fixture()
        f.set(SavedPoison, 0)
        f.set(SavedVenom, 30)
        f.set(SavedWarning, 0)
        f.player.attr.putAllFromPersistence(mapOf(
            LegacyPoison to 200, LegacyVenom to 100, LegacyWarning to true,
        ))

        f.login(500)

        assertFalse(ToxinImmunity.hasPoisonImmunity(f.player))
        assertEquals(0, f.value(PoisonUntil))
        assertEquals(530, f.value(VenomUntil))
        assertEquals(0, f.value(SavedWarning))
        assertEquals(530, f.timerExpiry())
        assertEquals(-39, f.orb)
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `an explicit unwarned flag wins while missing durations still import`() {
        val f = Fixture()
        f.set(SavedWarning, 0)
        f.player.attr.putAllFromPersistence(mapOf(LegacyPoison to 100, LegacyWarning to true))

        f.login(500)

        assertEquals(600, f.value(PoisonUntil))
        assertEquals(0, f.value(WarnedUntil))
        assertEquals(550, f.timerExpiry())
        f.timer(550)
        assertEquals(1, f.warnings)
    }

    @Test
    fun `completed migration cannot resurrect legacy values even when new durations are absent`() {
        val f = Fixture()
        f.set(Migrated, 1)
        f.player.attr.putAllFromPersistence(mapOf(
            LegacyPoison to 200, LegacyVenom to 100, LegacyWarning to true,
        ))

        f.login(500)

        assertFalse(ToxinImmunity.hasPoisonImmunity(f.player))
        assertFalse(ToxinImmunity.hasVenomImmunity(f.player))
        assertEquals(0, f.orb)
        assertNull(f.timerExpiry())
        assertTrue(f.scripts.isEmpty())
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `negative legacy durations are normalized and a warning without immunity is cleared`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(
            LegacyPoison to -50, LegacyVenom to -100, LegacyWarning to true,
        ))

        f.login(500)

        for (name in RuntimeVars + listOf(SavedPoison, SavedVenom, SavedWarning)) {
            assertEquals(0, f.value(name), name)
        }
        assertNull(f.timerExpiry())
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `logout and login pause remaining immunity and rebase only temporary clocks`() {
        val f = Fixture()
        f.login(100)
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200, venomDuration = 80)
        f.player.currentMapClock = 130
        f.logout()
        assertEquals(170, f.value(SavedPoison))
        assertEquals(50, f.value(SavedVenom))

        val restored = f.reload()
        assertTrue(RuntimeVars.none { it in restored.player.vars })
        restored.login(500)

        assertEquals(670, restored.value(PoisonUntil))
        assertEquals(550, restored.value(VenomUntil))
        assertEquals(550, restored.timerExpiry())
        assertEquals(-40, restored.orb)
        assertEquals(listOf(RunClientScript(5931, listOf(3104, 500))), restored.scripts)
        assertTrue(restored.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `warnings occur once at fifty ticks and expiry clears timers and native orb`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200)
        assertEquals(250, f.timerExpiry())
        f.timer(249)
        assertEquals(0, f.warnings)
        assertEquals(250, f.timerExpiry())

        f.timer(250)
        assertEquals(1, f.warnings)
        assertEquals(300, f.value(WarnedUntil))
        assertEquals(1, f.value(SavedWarning))
        assertEquals(300, f.timerExpiry())
        assertEquals(-2, f.orb)
        f.timer(251)
        assertEquals(1, f.warnings)

        f.timer(300)
        assertEquals(1, f.expiryMessages)
        assertEquals(0, f.value(SavedPoison))
        assertEquals(0, f.value(SavedWarning))
        assertEquals(0, f.orb)
        assertNull(f.timerExpiry())
        f.timer(301)
        assertEquals(1, f.expiryMessages)
    }

    @Test
    fun `warned immunity survives reload without repeating its warning`() {
        val f = Fixture()
        f.login(100)
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 80)
        f.timer(130)
        assertEquals(1, f.warnings)
        f.player.currentMapClock = 140
        f.logout()
        assertEquals(40, f.value(SavedPoison))
        assertEquals(1, f.value(SavedWarning))

        val restored = f.reload()
        restored.login(500)
        assertEquals(540, restored.value(WarnedUntil))
        assertEquals(540, restored.timerExpiry())
        restored.timer(539)
        assertEquals(0, restored.warnings)
        restored.timer(540)
        assertEquals(0, restored.warnings)
        assertEquals(1, restored.expiryMessages)
    }

    @Test
    fun `a legacy warned flag suppresses duplicate warning after import`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(LegacyPoison to 40, LegacyWarning to true))
        f.login(500)
        assertEquals(540, f.value(WarnedUntil))
        assertEquals(540, f.timerExpiry())
        f.timer(539)
        assertEquals(0, f.warnings)
        f.timer(540)
        assertEquals(1, f.expiryMessages)
    }

    @Test
    fun `shorter grants preserve expiry and warnings while longer grants rearm both phases`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 80, venomDuration = 10)
        f.timer(130)
        assertEquals(1, f.warnings)
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 20)
        assertEquals(180, f.value(PoisonUntil))
        assertEquals(180, f.value(WarnedUntil))
        assertEquals(180, f.timerExpiry())

        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200, venomDuration = 60)
        assertEquals(330, f.value(PoisonUntil))
        assertEquals(190, f.value(VenomUntil))
        assertEquals(0, f.value(WarnedUntil))
        assertEquals(190, f.timerExpiry())
        f.timer(190)
        assertEquals(280, f.timerExpiry())
        f.timer(280)
        assertEquals(2, f.warnings)
    }

    @Test
    fun `venom expiry restarts the native poison bar with its remaining duration`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200, venomDuration = 60)
        assertEquals(-40, f.orb)
        assertEquals(160, f.timerExpiry())
        assertEquals(listOf(RunClientScript(5931, listOf(3104, 100))), f.scripts)

        f.timer(160)

        assertFalse(ToxinImmunity.hasVenomImmunity(f.player))
        assertTrue(ToxinImmunity.hasPoisonImmunity(f.player))
        assertEquals(-5, f.orb)
        assertEquals(300, f.value(DisplayUntil))
        assertEquals(250, f.timerExpiry())
        assertEquals(listOf(
            RunClientScript(5931, listOf(3104, 100)),
            RunClientScript(5931, listOf(3105, 160)),
        ), f.scripts)
    }

    @Test
    fun `long poison immunity refreshes the capped native bar without expiring protection`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 2500)
        assertEquals(-38, f.orb)
        assertEquals(1240, f.timerExpiry())
        f.timer(1240)
        assertEquals(-38, f.orb)
        assertEquals(2380, f.timerExpiry())
        assertTrue(ToxinImmunity.hasPoisonImmunity(f.player))
        f.timer(2380)
        assertEquals(-8, f.orb)
        assertEquals(2550, f.timerExpiry())
        assertEquals(0, f.warnings)
        assertEquals(0, f.expiryMessages)
        assertEquals(listOf(
            RunClientScript(5931, listOf(3105, 100)),
            RunClientScript(5931, listOf(3105, 1240)),
            RunClientScript(5931, listOf(3105, 2380)),
        ), f.scripts)
    }

    @Test
    fun `native orb units round up and poison stays below the venom range`() {
        for ((duration, poisonOrb, venomOrb) in listOf(
            Triple(1, -1, -39), Triple(30, -1, -39), Triple(31, -2, -40),
            Triple(1140, -38, -76), Triple(1141, -38, -77),
        )) {
            val poison = Fixture()
            ToxinImmunity.grantImmunity(poison.player, poisonDuration = duration)
            assertEquals(poisonOrb, poison.orb, "poison duration=$duration")
            val venom = Fixture()
            ToxinImmunity.grantImmunity(venom.player, poisonDuration = 0, venomDuration = duration)
            assertEquals(venomOrb, venom.orb, "venom duration=$duration")
            venom.timer(100 + duration)
            assertEquals(0, venom.orb)
            assertEquals(0, venom.expiryMessages)
            assertNull(venom.timerExpiry())
        }
    }

    @Test
    fun `ordinary cures remove infections but preserve immunity and its timer`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200, venomDuration = 60)
        f.set("varp.poison_severity", 10)
        f.set("varp.venom_strikes", 1)
        f.set("varp.disease_drain", 5)
        Toxin.syncStatusOrbs(f.player)
        assertEquals(1_000_000, f.orb)
        assertEquals(1, f.value("varp.disease"))

        f.player.cureAllToxins()

        assertTrue(ToxinImmunity.hasPoisonImmunity(f.player))
        assertTrue(ToxinImmunity.hasVenomImmunity(f.player))
        assertEquals(-40, f.orb)
        assertEquals(0, f.value("varp.disease"))
        assertEquals(160, f.timerExpiry())
        assertFalse(PlayerPoison.tryPoison(f.player, initialDamage = 6))
        assertFalse(PlayerVenom.tryVenom(f.player))
    }

    @Test
    fun `clear erases current and saved immunity and stale legacy data cannot restore it`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 200, venomDuration = 60)
        f.logout()
        f.player.attr.putAllFromPersistence(mapOf(
            LegacyPoison to 200, LegacyVenom to 60, LegacyWarning to true,
        ))

        ToxinImmunity.clear(f.player)

        for (name in RuntimeVars + listOf(SavedPoison, SavedVenom, SavedWarning)) {
            assertEquals(0, f.value(name), name)
        }
        assertEquals(1, f.value(Migrated))
        assertNull(f.timerExpiry())
        assertEquals(0, f.orb)
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.logout()
        val restored = f.reload()
        restored.player.attr.putAllFromPersistence(mapOf(LegacyPoison to 200, LegacyVenom to 60))
        restored.login(500)
        assertFalse(ToxinImmunity.hasPoisonImmunity(restored.player))
        assertFalse(ToxinImmunity.hasVenomImmunity(restored.player))
        assertEquals(0, restored.orb)
        assertNull(restored.timerExpiry())
        assertTrue(restored.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `nonpositive grants do not create immunity or a timer`() {
        val f = Fixture()
        ToxinImmunity.grantImmunity(f.player, poisonDuration = 0, venomDuration = -1)
        assertNull(ToxinImmunity.statusOrbValue(f.player))
        assertNull(f.timerExpiry())
        assertTrue(f.client.messages.isEmpty())
    }

    private class Fixture {
        val client = RecordingClient()
        val player = Player().apply {
            client = this@Fixture.client
            currentMapClock = 100
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val worldClock = MapClock(100)
        private val events = EventBus()
        val orb get() = value("varp.poison")
        val scripts get() = client.messages.filterIsInstance<RunClientScript>()
        val warnings get() = client.messages.count { "about to wear off" in it.toString() }
        val expiryMessages get() = client.messages.count { "resistance has worn off" in it.toString() }

        init {
            with(PlayerPoisonTimerScript(worldClock)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun value(symbol: String) = player.vars[symbol]
        fun set(symbol: String, value: Int) = VarPlayerIntMapSetter.set(player, symbol, value)

        fun login(clock: Int) {
            worldClock.cycle = clock
            assertTrue(events.publish(SessionStateEvent.Login(player)))
            player.currentMapClock = clock
        }

        fun logout() {
            assertTrue(events.publish(SessionStateEvent.Logout(player)))
        }

        fun timer(clock: Int) {
            player.currentMapClock = clock
            ToxinImmunity.onTimer(player)
        }

        fun timerExpiry(): Int? = player.timerMap[ToxinImmunity.TIMER.asRSCM().toShort()]?.let {
            player.timerMap.extractExpiry(it)
        }

        fun reload() = Fixture().also { restored ->
            restored.player.vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
            restored.player.attr.putAllFromPersistence(player.attr.toPersistentMap())
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
        private const val PoisonUntil = "varp.toxin_poison_immunity_until"
        private const val VenomUntil = "varp.toxin_venom_immunity_until"
        private const val WarnedUntil = "varp.toxin_poison_immunity_warned_until"
        private const val DisplayUntil = "varp.toxin_poison_immunity_display_until"
        private const val SavedPoison = "varp.toxin_poison_immunity_remaining"
        private const val SavedVenom = "varp.toxin_venom_immunity_remaining"
        private const val SavedWarning = "varp.toxin_poison_immunity_warned"
        private const val Migrated = "varp.toxin_immunity_migrated"
        private const val LegacyPoison = "mechanics.toxins.poison_immunity_remaining"
        private const val LegacyVenom = "mechanics.toxins.venom_immunity_remaining"
        private const val LegacyWarning = "mechanics.toxins.poison_immunity_warned"
        private val RuntimeVars = listOf(PoisonUntil, VenomUntil, WarnedUntil, DisplayUntil)
        private val SavedVars = listOf(SavedPoison, SavedVenom, SavedWarning, Migrated)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
