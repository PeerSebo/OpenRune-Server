package org.rsmod.content.other.xprates

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class XpRateStateTest {
    @Test
    fun `old saved fields import once and survive permanent JSON save reload`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 625, RESET_KEY to 7, "unrelated" to 9))

        repeat(2) { XpRateState.migrate(player) }

        assertEquals(625, XpRateState.personalRateHundredths(player))
        assertEquals(7, XpRateState.levelsResetStamp(player))
        assertEquals(mapOf("unrelated" to 9), player.attr.toPersistentMap())
        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 100, RESET_KEY to 99))
        assertEquals(625, XpRateState.personalRateHundredths(restored))
        assertEquals(7, XpRateState.levelsResetStamp(restored))
        assertEquals(mapOf("unrelated" to 9), restored.attr.toPersistentMap())
    }

    @Test
    fun `zero and signed legacy values are preserved without clamping`() {
        for (value in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to value, RESET_KEY to value))

            XpRateState.migrate(player)

            val restored = reload(player)
            assertEquals(value, XpRateState.personalRateHundredths(restored))
            assertEquals(value, XpRateState.levelsResetStamp(restored))
        }
    }

    @Test
    fun `explicit modern zero wins before and after save reload`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 625, RESET_KEY to 7))
        VarPlayerIntMapSetter.set(player, PERSONAL, 0)
        VarPlayerIntMapSetter.set(player, RESET, 0)

        XpRateState.migrate(player)

        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 100, RESET_KEY to 1))
        assertEquals(0, XpRateState.personalRateHundredths(restored))
        assertEquals(0, XpRateState.levelsResetStamp(restored))
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `missing fields stay absent after migration and ignore later stale attributes`() {
        val player = Player()

        XpRateState.migrate(player)

        assertNull(XpRateState.personalRateHundredths(player))
        assertNull(XpRateState.levelsResetStamp(player))
        assertFalse(PERSONAL in player.vars)
        assertFalse(RESET in player.vars)
        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 625, RESET_KEY to 7))
        assertNull(XpRateState.personalRateHundredths(restored))
        assertNull(XpRateState.levelsResetStamp(restored))
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `reading or writing personal rate does not consume the independent reset migration`() {
        val player = Player()

        assertNull(XpRateState.personalRateHundredths(player))
        XpRateState.setPersonalRateHundredths(player, 450)
        player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 100, RESET_KEY to 0))

        assertEquals(0, player.vars[RESET_MIGRATED])
        assertEquals(0, XpRateState.levelsResetStamp(player))
        assertEquals(450, XpRateState.personalRateHundredths(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `reset migration does not consume a later personal rate import`() {
        val player = Player()
        XpRateState.markLevelsReset(player)
        player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 625, RESET_KEY to 7))

        assertEquals(0, player.vars[PERSONAL_MIGRATED])
        assertEquals(625, XpRateState.personalRateHundredths(player))
        assertEquals(1, XpRateState.levelsResetStamp(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `modern personal value preserves an independently imported reset stamp`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(PERSONAL_KEY to 625, RESET_KEY to -4))
        VarPlayerIntMapSetter.set(player, PERSONAL, 350)

        XpRateState.migrate(player)

        assertEquals(350, XpRateState.personalRateHundredths(player))
        assertEquals(-4, XpRateState.levelsResetStamp(player))
        assertEquals(1, player.vars[PERSONAL_MIGRATED])
        assertEquals(1, player.vars[RESET_MIGRATED])
    }

    @Test
    fun `all state and migration markers are permanent and never transmitted`() {
        for (symbol in listOf(PERSONAL, PERSONAL_MIGRATED, RESET, RESET_MIGRATED)) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    private fun reload(player: Player): Player {
        val persistent = player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }
        val mapper = ObjectMapper()
        val saved = mapper.writeValueAsString(persistent)
        val restored = mapper.readValue(saved, object : TypeReference<Map<Int, Int>>() {})
        return Player().apply {
            vars.backing.putAll(restored)
            attr.putAllFromPersistence(player.attr.toPersistentMap())
        }
    }

    companion object {
        private const val PERSONAL_KEY = "xp_rates_personal_hundredths"
        private const val RESET_KEY = "xp_rates_levels_reset"
        private const val PERSONAL = "varp.$PERSONAL_KEY"
        private const val RESET = "varp.$RESET_KEY"
        private const val PERSONAL_MIGRATED = "varp.xp_rates_personal_migrated"
        private const val RESET_MIGRATED = "varp.xp_rates_levels_reset_migrated"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
