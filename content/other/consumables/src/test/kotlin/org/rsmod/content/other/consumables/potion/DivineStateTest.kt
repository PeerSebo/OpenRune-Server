package org.rsmod.content.other.consumables.potion

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.vars.VarPlayerIntMapSetter

@ResourceLock("ServerCacheManager")
class DivineStateTest {
    @Test
    fun `all known legacy effects migrate independently and resume against the login clock`() {
        val f = PotionLifecycleFixture()
        val legacy = DivineEffectState.entries.associate { it.key to 30 + it.ordinal }
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to legacy, "unrelated" to 7))
        f.player.currentMapClock = 0

        f.divine.onLogin(f.player)

        DivineEffectState.entries.forEach {
            assertEquals(30 + it.ordinal, it.remaining(f.player))
            assertEquals(1030 + it.ordinal, it.expiresAt(f.player))
        }
        assertEquals(1005, f.timer(DivineEffectService.DIVINE_TIMER))
        assertEquals(mapOf("unrelated" to 7), f.player.attr.toPersistentMap())
        assertEquals(255, f.player.vars[DivineState.MIGRATED])
    }

    @Test
    fun `explicit zero and existing positive state win over old saves`() {
        val f = PotionLifecycleFixture()
        DivineEffectState.ATTACK.setRemaining(f.player, 0)
        DivineEffectState.COMBAT.setRemaining(f.player, 91)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to mapOf(
            DivineEffectState.ATTACK.key to 450,
            DivineEffectState.COMBAT.key to 490,
            DivineEffectState.MAGIC.key to -1,
        )))

        f.divine.onLogin(f.player)

        assertEquals(0, DivineEffectState.ATTACK.expiresAt(f.player))
        assertEquals(1091, DivineEffectState.COMBAT.expiresAt(f.player))
        assertEquals(0, DivineEffectState.MAGIC.expiresAt(f.player))
        assertNull(f.player.attr[DivineState.savedDivineTicks])
    }

    @Test
    fun `unknown historical entries survive known migration autosave and reconnect without activating`() {
        val f = PotionLifecycleFixture()
        val unknown = mapOf("retired_divine_effect" to 123, "future_effect" to 0)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to (unknown + (DivineEffectState.ATTACK.key to 41))))
        f.divine.onLogin(f.player)
        f.at(1011)
        f.prepare()
        f.restore(f.saved(), 9000)

        assertEquals(unknown, f.player.attr[DivineState.savedDivineTicks])
        assertEquals(mapOf(DivineEffectState.ATTACK.key to 30), DivineState.saved(f.player))
        assertEquals(9030, DivineEffectState.ATTACK.expiresAt(f.player))
        assertEquals(1, DivineState.active(f.player).size)
    }

    @Test
    fun `stale legacy entries cannot resurrect effects after expiry death or reset`() {
        for (clear in listOf("expiry", "death", "reset")) {
            val f = PotionLifecycleFixture()
            val legacy = mapOf(LEGACY to mapOf(DivineEffectState.ATTACK.key to 30))
            f.player.attr.putAllFromPersistence(legacy)
            f.divine.onLogin(f.player)
            when (clear) {
                "expiry" -> { f.at(1030); f.divine.process(f.access()) }
                "death" -> f.death()
                else -> f.divine.cancelMaintenance(f.player)
            }
            val save = f.saved()
            f.restore(save.copy(attrs = save.attrs + legacy), 9000)

            assertTrue(DivineState.active(f.player).isEmpty(), clear)
            assertTrue(DivineState.saved(f.player).isEmpty(), clear)
            assertNull(f.timer(DivineEffectService.DIVINE_TIMER), clear)
            assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"), clear)
            assertNull(f.player.attr[DivineState.savedDivineTicks], clear)
        }
    }

    @Test
    fun `warning mask distinguishes effects and redose resets only its own warning`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_divine_super_attack_boost")
        f.drink("dbrow.effect_divine_super_combat_boost")
        f.at(1475)
        f.divine.process(f.access())
        assertTrue(DivineEffectState.ATTACK.warned(f.player))
        assertTrue(DivineEffectState.COMBAT.warned(f.player))

        f.drink("dbrow.effect_divine_super_attack_boost")

        assertFalse(DivineEffectState.ATTACK.warned(f.player))
        assertTrue(DivineEffectState.COMBAT.warned(f.player))
        assertEquals(1500, f.timer(DivineEffectService.DIVINE_TIMER))
        f.at(1500)
        f.divine.process(f.access())
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.defence"))
    }

    @Test
    fun `death clears all warnings saved duration and unknown legacy entries`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_divine_super_combat_boost")
        f.at(1475)
        f.divine.process(f.access())
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to mapOf("retired_effect" to 123)))
        f.death()
        f.prepare()

        assertEquals(0, f.player.vars[DivineState.WARNED])
        assertTrue(DivineState.active(f.player).isEmpty())
        assertTrue(DivineState.saved(f.player).isEmpty())
        assertNull(f.player.attr[DivineState.savedDivineTicks])
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
    }

    @Test
    fun `mixed migration mask does not prevent importing newly mapped effects`() {
        val f = PotionLifecycleFixture()
        VarPlayerIntMapSetter.set(f.player, DivineState.MIGRATED, DivineEffectState.ATTACK.bit)
        f.player.attr.putAllFromPersistence(mapOf(LEGACY to mapOf(
            DivineEffectState.ATTACK.key to 100,
            DivineEffectState.COMBAT.key to 200,
        )))
        f.divine.onLogin(f.player)

        assertEquals(0, DivineEffectState.ATTACK.expiresAt(f.player))
        assertEquals(1200, DivineEffectState.COMBAT.expiresAt(f.player))
    }

    @Test
    fun `state is server only with permanent remaining clocks and temporary expiry and warnings`() {
        val permanent = DivineEffectState.entries.map { it.remainingVarp } + DivineState.MIGRATED
        val temporary = DivineEffectState.entries.map { it.expiryVarp } + DivineState.WARNED
        for ((symbols, scope) in listOf(permanent to VarpLifetime.Perm, temporary to VarpLifetime.Temp)) {
            for (symbol in symbols) {
                val type = requireNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
                assertEquals(scope, type.scope, symbol)
                assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
            }
        }
    }

    companion object {
        private const val LEGACY = "consumables.potion.divine_remaining"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
