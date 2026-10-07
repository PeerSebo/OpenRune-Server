package org.rsmod.content.other.consumables.potion

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
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.stat.stat
import org.rsmod.content.other.consumables.potion.cox.CoxPotionTimerScript
import org.rsmod.content.other.consumables.potion.nmz.NightmareZonePotionTimerScript
import org.rsmod.content.other.consumables.potion.overload.OverloadRegistry
import org.rsmod.content.other.consumables.potion.overload.OverloadType
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class OverloadLifecycleTest {
    @Test
    fun `each overload schedules five ten damage pulses at two tick intervals`() {
        for (type in OverloadType.entries) {
            val f = Fixture()
            val timers = OverloadRegistry[type]
            f.drink(type)
            assertEquals(1002, f.base.timer(timers.damageTimer))
            assertEquals(1025, f.base.timer(timers.effectTimer))
            repeat(5) { index ->
                f.base.at(1002 + index * 2)
                f.pulse(timers.damageTimer)
                assertEquals(List(index + 1) { 10 }, f.base.hits)
                assertEquals(99 - (index + 1) * 10, f.base.player.stat("stat.hitpoints"))
                assertEquals(4 - index, type.state.read(f.base.player)?.damageHitsRemaining)
                if (index < 4) assertEquals(1004 + index * 2, f.base.timer(timers.damageTimer))
            }
            f.pulse(timers.damageTimer)
            assertEquals(5, f.base.hits.size)
        }
    }

    @Test
    fun `warning is once per application and expiry heals only once`() {
        for (type in OverloadType.entries) {
            val f = Fixture()
            val timer = OverloadRegistry[type].effectTimer
            f.drink(type)
            f.base.level("stat.hitpoints", 20)
            f.base.at(1483)
            f.pulse(timer)
            f.pulse(timer)
            assertEquals(1, f.messages("wear off in 10 seconds"))
            assertTrue(requireNotNull(type.state.read(f.base.player)).warned)

            f.base.at(1500)
            f.pulse(timer)
            assertEquals(70, f.base.player.stat("stat.hitpoints"))
            assertNull(type.state.read(f.base.player))
            assertNull(f.base.timer(timer))
            assertNull(f.base.timer(OverloadRegistry[type].damageTimer))
            f.pulse(timer)
            assertEquals(70, f.base.player.stat("stat.hitpoints"))
            assertEquals(1, f.messages("have worn off"))
            assertEquals(99, f.base.player.stat("stat.attack"))
        }
    }

    @Test
    fun `renewal resets warning damage countdown and stat refresh strength`() {
        val f = Fixture()
        f.base.drink("dbrow.effect_cox_overload_weak")
        f.base.at(1483)
        f.pulse(OverloadRegistry.COX_EFFECT_TIMER)
        assertEquals(1, f.messages("wear off in 10 seconds"))
        f.base.drink("dbrow.effect_cox_overload_strong")
        val state = requireNotNull(OverloadType.COX.state.read(f.base.player))
        assertEquals(1983, state.expiresAt)
        assertEquals(5, state.damageHitsRemaining)
        assertFalse(state.warned)
        assertEquals(PotionStatBoost(6, 16), state.boost)
        assertEquals(1485, f.base.timer(OverloadRegistry.COX_DAMAGE_TIMER))
        f.base.level("stat.attack", 80)
        f.base.at(1500)
        f.pulse(OverloadRegistry.COX_EFFECT_TIMER)
        assertEquals(101, f.base.player.stat("stat.attack"))
        assertEquals(0, f.messages("have worn off"))
        f.base.at(1966)
        f.pulse(OverloadRegistry.COX_EFFECT_TIMER)
        assertEquals(2, f.messages("wear off in 10 seconds"))
    }

    @Test
    fun `registered death cleanup prevents remaining hits boosts and expiry healing`() {
        for (type in OverloadType.entries) {
            val f = Fixture()
            f.drink(type)
            f.base.at(1002)
            f.pulse(OverloadRegistry[type].damageTimer)
            f.base.death()
            f.base.level("stat.hitpoints", 10)
            assertNull(type.state.read(f.base.player))
            assertNull(f.base.timer(OverloadRegistry[type].damageTimer))
            assertNull(f.base.timer(OverloadRegistry[type].effectTimer))
            f.base.at(1500)
            f.pulse(OverloadRegistry[type].damageTimer)
            f.pulse(OverloadRegistry[type].effectTimer)
            assertEquals(listOf(10), f.base.hits)
            assertEquals(10, f.base.player.stat("stat.hitpoints"))
            assertEquals(0, f.messages("have worn off"))
        }
    }

    @Test
    fun `overload families have independent deadlines and damage counts`() {
        val f = Fixture()
        f.drink(OverloadType.COX)
        f.base.at(1010)
        f.drink(OverloadType.NIGHTMARE_ZONE)
        f.pulse(OverloadRegistry.COX_DAMAGE_TIMER)
        assertEquals(4, OverloadType.COX.state.read(f.base.player)?.damageHitsRemaining)
        assertEquals(5, OverloadType.NIGHTMARE_ZONE.state.read(f.base.player)?.damageHitsRemaining)
        f.base.at(1500)
        f.pulse(OverloadRegistry.COX_EFFECT_TIMER)
        assertNull(OverloadType.COX.state.read(f.base.player))
        assertEquals(1510, OverloadType.NIGHTMARE_ZONE.state.read(f.base.player)?.expiresAt)
        assertNotNull(f.base.timer(OverloadRegistry.NIGHTMARE_ZONE_DAMAGE_TIMER))
    }

    @Test
    fun `logout clears overloads and permanent saves cannot restore temporary state`() {
        val f = Fixture()
        for (type in OverloadType.entries) f.drink(type)
        val snapshot = f.base.saved()
        for (prefix in listOf("consumables_cox_overload", "consumables_nmz_overload")) {
            for (field in listOf("expires_at", "boost_constant", "boost_percent", "damage_hits", "warned")) {
                val type = requireNotNull(ServerCacheManager.getVarp("varp.${prefix}_$field".asRSCM()))
                assertEquals(VarpLifetime.Temp, type.scope)
                assertEquals(VarpTransmitLevel.Never, type.transmit)
                assertFalse(snapshot.vars.containsKey(type.id))
            }
        }
        assertTrue(f.events.publish(SessionStateEvent.Logout(f.base.player)))
        for (type in OverloadType.entries) {
            assertNull(type.state.read(f.base.player))
            assertNull(f.base.timer(OverloadRegistry[type].damageTimer))
        }
        f.base.restore(snapshot, 9000)
        for (type in OverloadType.entries) assertNull(type.state.read(f.base.player))
    }

    @Test
    fun `each native overload tier starts its own client buff definition`() {
        val cases = listOf(
            Triple("dbrow.effect_cox_overload_weak", 1, 4152),
            Triple("dbrow.effect_cox_overload", 2, 3089),
            Triple("dbrow.effect_cox_overload_strong", 3, 4153),
            Triple("dbrow.effect_nzone_overload", 0, 3088),
        )
        for ((effect, tier, struct) in cases) {
            val f = Fixture()
            f.base.drink(effect)
            val id = "clientscript.[clientscript,buff_bar_start]".asRSCM()
            val packet = f.base.client.messages.filterIsInstance<RunClientScript>().single { it.id == id }
            assertEquals(RunClientScript(id, listOf(struct, 1000)).toString(), packet.toString())
            if (tier > 0) assertEquals(tier, f.base.player.vars["varbit.raids_overload_tier"])
        }
    }

    private class Fixture {
        val base = PotionLifecycleFixture()
        val events = EventBus().also {
            val context = ScriptContext(it, CheatCommandMap(), EngineQueueCache())
            with(base.injector.getInstance(CoxPotionTimerScript::class.java)) { context.startup() }
            with(base.injector.getInstance(NightmareZonePotionTimerScript::class.java)) { context.startup() }
        }

        fun drink(type: OverloadType) = base.drink(when (type) {
            OverloadType.COX -> "dbrow.effect_cox_overload"
            OverloadType.NIGHTMARE_ZONE -> "dbrow.effect_nzone_overload"
        })

        fun pulse(timer: String) {
            base.player.clearTimer(timer)
            val coroutine = base.player.launch {
                assertTrue(events.publish(base.access(), PlayerTimerEvent.Normal(timer.asRSCM())))
            }
            assertTrue(coroutine.isIdle)
        }

        fun messages(text: String) = base.client.messages.count { it.toString().contains(text) }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
