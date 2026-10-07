package org.rsmod.content.other.consumables.potion

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.DragonfireProtection
import org.rsmod.api.combat.commons.DragonfireProtection.DragonfireType
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.other.consumables.ConsumablesModule
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class AntifireStateTest {
    @Test
    fun `login imports both old durations with separate native countdowns and world based timers`() {
        val f = Fixture()
        f.player.currentMapClock = 0
        f.player.attr.putAllFromPersistence(mapOf(legacy(NORMAL) to 137, legacy(SUPER) to 211, "unrelated" to 7))

        f.login()

        assertEquals(1137, NORMAL.expiresAt(f.player))
        assertEquals(1211, SUPER.expiresAt(f.player))
        assertEquals(137, NORMAL.remaining(f.player))
        assertEquals(211, SUPER.remaining(f.player))
        assertEquals(5, f.player.vars[NORMAL.displayVarbit])
        assertEquals(11, f.player.vars[SUPER.displayVarbit])
        assertEquals(1112, f.timerExpiry(NORMAL))
        assertEquals(1186, f.timerExpiry(SUPER))
        for (state in AntifireState.entries) assertEquals(1, f.player.vars[state.migratedVarp])
        assertEquals(mapOf("unrelated" to 7), f.player.attr.toPersistentMap())
    }

    @Test
    fun `logout stores exact remaining ticks and reconnect does not consume offline duration`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(legacy(NORMAL) to 137, legacy(SUPER) to 211))
        f.login()
        f.at(1037)

        f.logout()

        assertEquals(100, NORMAL.remaining(f.player))
        assertEquals(174, SUPER.remaining(f.player))
        assertEquals(4, f.player.vars[NORMAL.displayVarbit])
        assertEquals(9, f.player.vars[SUPER.displayVarbit])
        f.reload()
        for (state in AntifireState.entries) assertEquals(0, state.expiresAt(f.player))
        f.player.attr.putAllFromPersistence(mapOf(legacy(NORMAL) to 500, legacy(SUPER) to 500))
        f.clock.cycle = 9000
        f.login()
        assertEquals(9100, NORMAL.expiresAt(f.player))
        assertEquals(9174, SUPER.expiresAt(f.player))
        assertEquals(9075, f.timerExpiry(NORMAL))
        assertEquals(9149, f.timerExpiry(SUPER))
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `new accounts and negative legacy durations stay inactive`() {
        for (legacyDuration in listOf(null, -20)) {
            val f = Fixture()
            if (legacyDuration != null) {
                f.player.attr.putAllFromPersistence(AntifireState.entries.associate { legacy(it) to legacyDuration })
            }
            f.login()
            for (state in AntifireState.entries) f.assertInactive(state)
            assertTrue(f.player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test
    fun `explicit current durations including zero win before migration and each marker is independent`() {
        for (state in AntifireState.entries) {
            val other = AntifireState.entries.single { it != state }
            for (current in listOf(0, 77)) {
                val f = Fixture()
                f.player.attr.putAllFromPersistence(mapOf(legacy(state) to 200, legacy(other) to 111))
                state.setRemaining(f.player, current)

                f.login()

                assertEquals(current, state.remaining(f.player))
                assertEquals(111, other.remaining(f.player))
                assertEquals(1111, other.expiresAt(f.player))
                if (current == 0) f.assertInactive(state)
                else assertEquals(1077, state.expiresAt(f.player))
                assertTrue(f.player.attr.toPersistentMap().isEmpty())
            }
        }
    }

    @Test
    fun `clearing one legacy effect leaves the other importable and stale data cannot restore the cleared one`() {
        for (state in AntifireState.entries) {
            val other = AntifireState.entries.single { it != state }
            val f = Fixture()
            f.player.attr.putAllFromPersistence(mapOf(legacy(state) to 200, legacy(other) to 111))

            state.clear(f.player)

            f.login()
            f.assertInactive(state)
            assertEquals(111, other.remaining(f.player))
            f.reload()
            f.player.attr.putAllFromPersistence(mapOf(legacy(state) to 200))
            f.login()
            f.assertInactive(state)
            assertEquals(111, other.remaining(f.player))
        }
    }

    @Test
    fun `regular and extended doses preserve each native interval buff and independent expiry`() {
        for (state in AntifireState.entries) {
            val f = Fixture()
            val other = AntifireState.entries.single { it != state }
            f.drink(other)
            val otherExpiry = other.expiresAt(f.player)
            val extended = f.drink(state, extended = true)
            assertEquals(1000 + extended.duration, state.expiresAt(f.player))
            assertEquals(durationUnits(extended.duration, state.countdownInterval), f.player.vars[state.displayVarbit])
            assertEquals(1000 + extended.duration - 25, f.timerExpiry(state))
            val buffId = "clientscript.[clientscript,buff_bar_start]".asRSCM()
            val packet = f.client.messages.filterIsInstance<RunClientScript>().last { it.id == buffId }
            assertEquals(RunClientScript(buffId, listOf(state.buffStruct, 1000)).toString(), packet.toString())

            f.at(1050)
            val regular = f.drink(state)
            assertTrue(regular.duration < extended.duration)
            assertEquals(1050 + regular.duration, state.expiresAt(f.player))
            assertEquals(1050 + regular.duration - 25, f.timerExpiry(state))
            assertEquals(otherExpiry, other.expiresAt(f.player))
        }
    }

    @Test
    fun `refreshed doses ignore early callbacks and preserve warning and expiry messages`() {
        for (state in AntifireState.entries) {
            val f = Fixture()
            val effect = f.drink(state)
            f.at(1050)
            f.drink(state)
            f.at(1000 + effect.duration - 25)

            f.process(state)

            assertFalse(f.messages().any { "about to expire" in it })
            assertEquals(1050 + effect.duration - 25, f.timerExpiry(state))
            f.at(1050 + effect.duration - 25)
            f.process(state)
            assertEquals(1, f.messages().count { "Your ${state.potionName} is about to expire." in it })
            assertEquals(1050 + effect.duration, f.timerExpiry(state))
            f.at(1050 + effect.duration)
            f.process(state)
            f.assertInactive(state)
            assertEquals(1, f.messages().count { "Your ${state.potionName} has expired." in it })
        }
    }

    @Test
    fun `short restored durations expire directly and cannot return from stale attributes before logout`() {
        for (duration in listOf(20, 25)) {
            val f = Fixture()
            f.player.attr.putAllFromPersistence(AntifireState.entries.associate { legacy(it) to duration })
            f.login()
            for (state in AntifireState.entries) assertEquals(1000 + duration, f.timerExpiry(state))
            f.at(1000 + duration)

            for (state in AntifireState.entries) f.process(state)

            for (state in AntifireState.entries) f.assertInactive(state)
            assertFalse(f.messages().any { "about to expire" in it })
            f.reload()
            f.player.attr.putAllFromPersistence(AntifireState.entries.associate { legacy(it) to 200 })
            f.login()
            for (state in AntifireState.entries) f.assertInactive(state)
            assertTrue(f.player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test
    fun `super protection takes priority then expires back to the independently active normal potion`() {
        val f = Fixture()
        assertEquals(50, f.maxHit(DragonfireType.Chromatic))
        f.player.attr.putAllFromPersistence(mapOf(legacy(NORMAL) to 200, legacy(SUPER) to 80))
        f.login()
        assertEquals(0, f.maxHit(DragonfireType.Chromatic))
        assertEquals(0, f.maxHit(DragonfireType.Metal))
        assertEquals(50, f.maxHit(DragonfireType.WyvernIce))

        f.at(1080)
        f.process(SUPER)

        f.assertInactive(SUPER)
        assertEquals(1200, NORMAL.expiresAt(f.player))
        assertEquals(35, f.maxHit(DragonfireType.Chromatic))
        assertEquals(35, f.maxHit(DragonfireType.Metal))
        f.at(1200)
        f.process(NORMAL)
        assertEquals(50, f.maxHit(DragonfireType.Chromatic))
        assertEquals(50, f.maxHit(DragonfireType.Metal))
    }

    @Test
    fun `normal protection still combines with shield or chromatic magic prayer and never protects wyvern ice`() {
        val f = Fixture()
        f.drink(NORMAL)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        assertEquals(0, f.maxHit(DragonfireType.Chromatic))
        assertEquals(35, f.maxHit(DragonfireType.Metal))
        assertEquals(20, f.maxHit(DragonfireType.WyvernIce))

        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 0)
        f.player.worn[0] = InvObj("obj.antidragonbreathshield", 1)
        assertEquals(0, f.maxHit(DragonfireType.Chromatic))
        assertEquals(0, f.maxHit(DragonfireType.Metal))
        assertEquals(50, f.maxHit(DragonfireType.WyvernIce))
        NORMAL.clear(f.player)
        assertEquals(10, f.maxHit(DragonfireType.Chromatic))
        assertEquals(10, f.maxHit(DragonfireType.Metal))
    }

    @Test
    fun `bound antifire death cleanup clears both protections while preserving unrelated potion payloads`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(AntifireState.entries.associate { legacy(it) to 137 })
        f.login()
        f.player.staminaExpiresAt = 1200
        f.player.savedStaminaTicks = 100
        val divine = mutableMapOf("retained" to 73)
        f.player.attr[DivineState.savedDivineTicks] = divine
        f.player.savedMenaphiteRemedyTicks = 111
        val hooks = f.injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))

        hooks.single { it is AntifireDeathCleanupHook }.cleanup(f.player)

        for (state in AntifireState.entries) f.assertInactive(state)
        assertEquals(50, f.maxHit(DragonfireType.Chromatic))
        assertEquals(1200, f.player.staminaExpiresAt)
        assertEquals(100, f.player.savedStaminaTicks)
        assertSame(divine, f.player.attr[DivineState.savedDivineTicks])
        assertEquals(111, f.player.savedMenaphiteRemedyTicks)
        f.player.attr.putAllFromPersistence(AntifireState.entries.associate { legacy(it) to 137 })
        for (state in AntifireState.entries) {
            state.migrate(f.player)
            f.assertInactive(state)
        }
    }

    @Test
    fun `logout after an unprocessed expiry stores zero without ending the other effect`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(legacy(NORMAL) to 20, legacy(SUPER) to 100))
        f.login()
        f.at(1030)

        f.logout()

        assertEquals(0, NORMAL.remaining(f.player))
        assertEquals(0, f.player.vars[NORMAL.displayVarbit])
        assertEquals(70, SUPER.remaining(f.player))
        f.reload()
        f.clock.cycle = 2000
        f.login()
        f.assertInactive(NORMAL)
        assertEquals(2070, SUPER.expiresAt(f.player))
    }

    @Test
    fun `packed private expiry is temporary while exact saved durations and migration markers persist`() {
        for (state in AntifireState.entries) {
            for ((symbol, lifetime) in mapOf(
                state.expiryVarp to VarpLifetime.Temp,
                state.remainingVarp to VarpLifetime.Perm,
                state.migratedVarp to VarpLifetime.Perm,
            )) {
                val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
                assertEquals(lifetime, type.scope, symbol)
                assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
            }
        }
    }

    private class Fixture {
        val clock = MapClock(1000)
        val injector = Guice.createInjector(ConsumablesModule(), object : AbstractModule() {
            override fun configure() { bind(MapClock::class.java).toInstance(clock) }
        })
        val effects = injector.getInstance(PotionEffectService::class.java)
        val client = RecordingClient()
        var player = newPlayer().apply { currentMapClock = clock.cycle }
        private val events = EventBus().also { events ->
            with(injector.getInstance(PotionEffectTimerScript::class.java)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        private fun newPlayer() = Player(client).apply {
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }

        fun at(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle }
        fun login() { events.publish(SessionStateEvent.Login(player)) }
        fun logout() { events.publish(SessionStateEvent.Logout(player)) }
        fun process(state: AntifireState) { effects.processDragonfireProtection(access(), state == SUPER) }
        fun drink(state: AntifireState, extended: Boolean = false): PotionEffectRow {
            val effect = PotionEffectRow.getRow("dbrow.effect_${if (extended) "extended_" else ""}${state.key}")
            effects.apply(access(), effect)
            return effect
        }

        fun messages() = client.messages.map(Any::toString)
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        fun maxHit(type: DragonfireType) = DragonfireProtection.resolveMaxHit(player, type, 50)

        fun reload() {
            val original = player
            player = newPlayer().apply {
                vars.backing.putAll(original.vars.backing.filterKeys { id ->
                    ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
                })
                attr.putAllFromPersistence(original.attr.toPersistentMap())
            }
        }

        fun timerExpiry(state: AntifireState): Int {
            val packed = checkNotNull(player.timerMap[state.timer.asRSCM().toShort()])
            return player.timerMap.extractExpiry(packed)
        }

        fun assertInactive(state: AntifireState) {
            assertEquals(0, state.expiresAt(player), state.name)
            assertEquals(0, state.remaining(player), state.name)
            assertEquals(0, player.vars[state.displayVarbit], state.name)
            assertEquals(1, player.vars[state.migratedVarp], state.name)
            assertFalse(state.timer in player.timerMap, state.name)
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
        private val NORMAL = AntifireState.NORMAL
        private val SUPER = AntifireState.SUPER
        private fun legacy(state: AntifireState) = "consumables.potion.${state.key}_remaining"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
