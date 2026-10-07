package org.rsmod.content.other.emirsarena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.emirsarena.duel.Duel
import org.rsmod.content.other.emirsarena.duel.DuelRules
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class EmirsArenaStateTest {
    @Test
    fun `old records and duel settings import once and survive permanent-only reload`() {
        val player = Player()
        player.attr.putAllFromPersistence(LEGACY_VALUES + ("unrelated" to 9))
        val opponent = Player()
        val duel = Duel(player, opponent, ranked = false)
        player.attr[EmirsArenaAttributes.DUEL] = duel
        player.attr[EmirsArenaAttributes.CHALLENGE_TARGET] = opponent

        repeat(2) { EmirsArenaState.migrate(player) }

        assertRecord(player)
        assertSame(duel, player.attr[EmirsArenaAttributes.DUEL])
        assertSame(opponent, player.attr[EmirsArenaAttributes.CHALLENGE_TARGET])
        assertEquals(mapOf("unrelated" to 9), player.attr.toPersistentMap())
        val restored = reload(player)
        restored.attr.putAllFromPersistence(LEGACY_VALUES.mapValues { 99 })
        assertRecord(restored)
        assertEquals(mapOf("unrelated" to 9), restored.attr.toPersistentMap())
    }

    @Test
    fun `explicit modern zero takes precedence for every counter and saved rule field`() {
        val player = Player()
        player.attr.putAllFromPersistence(LEGACY_VALUES)
        for (name in LEGACY_VALUES.keys) {
            VarPlayerIntMapSetter.set(player, "varp.$name", 0)
        }

        EmirsArenaState.migrate(player)

        for (name in LEGACY_VALUES.keys) assertEquals(0, player.vars["varp.$name"], name)
        assertEquals(0, EmirsArenaState.savedPreset(player))
        assertEquals(0, EmirsArenaState.lastRules(player))
        val restored = reload(player)
        restored.attr.putAllFromPersistence(LEGACY_VALUES)
        assertEquals(0, EmirsArenaState.winStreak(restored))
        assertEquals(0, EmirsArenaState.savedPreset(restored))
        assertEquals(0, EmirsArenaState.lastRules(restored))
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `mixed modern and old fields retain modern values while importing the remaining state`() {
        val player = Player()
        player.attr.putAllFromPersistence(LEGACY_VALUES)
        VarPlayerIntMapSetter.set(player, "varp.emirs_arena_ranked_wins", 42)
        VarPlayerIntMapSetter.set(player, "varp.emirs_arena_last_rules", DuelRules.NONE.packed)

        EmirsArenaState.migrate(player)

        assertEquals(42, EmirsArenaState.wins(player, ranked = true))
        assertEquals(12, EmirsArenaState.wins(player, ranked = false))
        assertEquals(4, EmirsArenaState.losses(player, ranked = false))
        assertEquals(3, EmirsArenaState.losses(player, ranked = true))
        assertEquals(8, EmirsArenaState.winStreak(player))
        assertEquals(DuelRules.WHIP.packed, EmirsArenaState.savedPreset(player))
        assertEquals(0, EmirsArenaState.lastRules(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `accounts without saved settings keep missing distinct from unrestricted zero rules`() {
        val player = Player()

        EmirsArenaState.migrate(player)

        assertEquals(0, EmirsArenaState.wins(player, ranked = true))
        assertEquals(0, EmirsArenaState.losses(player, ranked = false))
        assertEquals(0, EmirsArenaState.winStreak(player))
        assertNull(EmirsArenaState.savedPreset(player))
        assertNull(EmirsArenaState.lastRules(player))
        val restored = reload(player)
        restored.attr.putAllFromPersistence(LEGACY_VALUES)
        assertNull(EmirsArenaState.savedPreset(restored))
        assertNull(EmirsArenaState.lastRules(restored))
        assertEquals(0, EmirsArenaState.wins(restored, ranked = true))
        assertTrue(restored.attr.toPersistentMap().isEmpty())

        EmirsArenaState.savePreset(restored, DuelRules.NONE.packed)
        EmirsArenaState.saveLastRules(restored, DuelRules.NONE.packed)
        val unrestricted = reload(restored)
        assertEquals(0, EmirsArenaState.savedPreset(unrestricted))
        assertEquals(0, EmirsArenaState.lastRules(unrestricted))
    }

    @Test
    fun `writes before login preserve other old records and prevent stale streak restoration`() {
        val player = Player()
        player.attr.putAllFromPersistence(LEGACY_VALUES)

        EmirsArenaState.setWinStreak(player, 0)
        EmirsArenaState.savePreset(player, 0)
        EmirsArenaState.saveLastRules(player, 0)
        EmirsArenaState.recordWin(player, ranked = false)
        EmirsArenaState.recordLoss(player, ranked = true)

        val restored = reload(player)
        restored.attr.putAllFromPersistence(LEGACY_VALUES)
        assertEquals(0, EmirsArenaState.winStreak(restored))
        assertEquals(0, EmirsArenaState.savedPreset(restored))
        assertEquals(0, EmirsArenaState.lastRules(restored))
        assertEquals(13, EmirsArenaState.wins(restored, ranked = false))
        assertEquals(4, EmirsArenaState.losses(restored, ranked = true))
        assertEquals(20, EmirsArenaState.wins(restored, ranked = true))
        assertEquals(4, EmirsArenaState.losses(restored, ranked = false))
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `migration preserves exact signed rule bits for the existing rule validator`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(
            "emirs_arena_duel_preset" to Int.MIN_VALUE,
            "emirs_arena_last_rules" to -1,
        ))

        EmirsArenaState.migrate(player)

        assertEquals(Int.MIN_VALUE, EmirsArenaState.savedPreset(player))
        assertEquals(-1, EmirsArenaState.lastRules(player))
        assertFalse(DuelRules(checkNotNull(EmirsArenaState.savedPreset(player))).isValid)
        assertFalse(DuelRules(checkNotNull(EmirsArenaState.lastRules(player))).isValid)
    }

    @Test
    fun `packed state and import marker are permanent and never transmitted to the client`() {
        for (symbol in LEGACY_VALUES.keys.map { "varp.$it" } + MIGRATED) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    private fun assertRecord(player: Player) {
        assertEquals(12, EmirsArenaState.wins(player, ranked = false))
        assertEquals(4, EmirsArenaState.losses(player, ranked = false))
        assertEquals(20, EmirsArenaState.wins(player, ranked = true))
        assertEquals(3, EmirsArenaState.losses(player, ranked = true))
        assertEquals(8, EmirsArenaState.winStreak(player))
        assertEquals(DuelRules.WHIP.packed, EmirsArenaState.savedPreset(player))
        assertEquals(DuelRules.BOXING.packed, EmirsArenaState.lastRules(player))
        assertEquals(1, player.vars[MIGRATED])
    }

    private fun reload(player: Player): Player = Player().apply {
        vars.backing.putAll(player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        })
        attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    companion object {
        private const val MIGRATED = "varp.emirs_arena_state_migrated"
        private val LEGACY_VALUES = mapOf(
            "emirs_arena_legacy_wins" to 12,
            "emirs_arena_legacy_losses" to 4,
            "emirs_arena_ranked_wins" to 20,
            "emirs_arena_ranked_losses" to 3,
            "emirs_arena_win_streak" to 8,
            "emirs_arena_duel_preset" to DuelRules.WHIP.packed,
            "emirs_arena_last_rules" to DuelRules.BOXING.packed,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
