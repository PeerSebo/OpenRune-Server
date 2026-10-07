package org.rsmod.content.other.consumables.potion.cox

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class CoxPrayerEnhanceEffectTest {
    @Test
    fun `native buff definitions match each prayer enhancement tier`() {
        val cases = listOf(CoxPotionTier.Weak to 4150, CoxPotionTier.Normal to 3087, CoxPotionTier.Strong to 4151)
        for ((index, case) in cases.withIndex()) {
            val (tier, struct) = case
            val fixture = Fixture()
            fixture.effect.apply(fixture.access(), tier)
            val id = "clientscript.[clientscript,buff_bar_start]".asRSCM()
            val packet = fixture.client.messages.filterIsInstance<RunClientScript>().single()
            assertEquals(RunClientScript(id, listOf(struct, 1000)).toString(), packet.toString())
            assertEquals(index + 1, fixture.player.vars["varbit.raids_prayer_enhance_tier"])
        }
    }

    @Test
    fun `renewal resets duration warning and native activation display`() {
        val fixture = Fixture()
        fixture.effect.apply(fixture.access(), CoxPotionTier.Strong)
        assertEquals(80, fixture.player.vars[TIMER_DISPLAY])
        assertEquals(6, fixture.player.vars[RATE_DISPLAY])
        assertEquals(1500, fixture.player.vars[EXPIRES_AT])

        fixture.at(1455)
        fixture.effect.process(fixture.access())
        assertEquals(79, fixture.player.vars[TIMER_DISPLAY])
        assertEquals(1, fixture.player.vars[WARNED])
        fixture.at(1456)
        fixture.effect.process(fixture.access())
        assertEquals(1, fixture.client.messages.count { "about to expire" in it.toString() })

        fixture.at(1460)
        fixture.effect.apply(fixture.access(), CoxPotionTier.Weak)
        assertEquals(42, fixture.player.vars[TIMER_DISPLAY])
        assertEquals(11, fixture.player.vars[RATE_DISPLAY])
        assertEquals(1960, fixture.player.vars[EXPIRES_AT])
        assertEquals(0, fixture.player.vars[WARNED])
        assertTrue(CoxPrayerEnhanceEffect.TIMER in fixture.player.timerMap)
    }

    @Test
    fun `expiry and death clear the warning timer and native display`() {
        val fixture = Fixture()
        fixture.effect.apply(fixture.access(), CoxPotionTier.Weak)
        fixture.at(1500)
        fixture.effect.process(fixture.access())
        fixture.assertCleared()

        fixture.at(1600)
        fixture.effect.apply(fixture.access(), CoxPotionTier.Normal)
        CoxPrayerEnhanceDeathCleanupHook(fixture.effect).cleanup(fixture.player)
        fixture.assertCleared()
    }

    @Test
    fun `new state is temporary and never transmitted`() {
        for (symbol in listOf(EXPIRES_AT, FLAGS)) {
            val definition = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, definition.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, definition.transmit, symbol)
        }
    }

    private class Fixture {
        val client = RecordingClient()
        val player = Player(client).apply {
            currentMapClock = 1000
            processedMapClock = 1000
            statMap.setBaseLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.prayer", 0)
        }
        val effect = CoxPrayerEnhanceEffect()

        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())

        fun at(cycle: Int) {
            player.currentMapClock = cycle
            player.processedMapClock = cycle
        }

        fun assertCleared() {
            assertFalse(EXPIRES_AT in player.vars)
            assertEquals(0, player.vars[WARNED])
            assertEquals(0, player.vars[TIMER_DISPLAY])
            assertEquals(0, player.vars[RATE_DISPLAY])
            assertFalse(CoxPrayerEnhanceEffect.TIMER in player.timerMap)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val EXPIRES_AT = "varp.consumables_cox_prayer_enhance_expires_at"
        private const val FLAGS = "varp.consumables_cox_prayer_enhance_flags"
        private const val WARNED = "varbit.consumables_cox_prayer_enhance_warned"
        private const val TIMER_DISPLAY = "varbit.raids_prayerenhance_timer"
        private const val RATE_DISPLAY = "varbit.raids_prayerenhance_rate"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
