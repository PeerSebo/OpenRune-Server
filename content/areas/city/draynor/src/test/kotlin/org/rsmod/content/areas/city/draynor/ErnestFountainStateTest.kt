package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class ErnestFountainStateTest {
    @Test fun `both legacy values migrate and survive permanent-only save reload`() {
        for (value in listOf(false, true)) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(Legacy to value, "unrelated" to 3))
            ErnestFountainState.migrate(player)
            assertEquals(value, ErnestFountainState.get(player))
            assertEquals(mapOf("unrelated" to 3), player.attr.toPersistentMap())
            val restored = Player().apply {
                vars.backing.putAll(player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
            }
            assertEquals(value, ErnestFountainState.get(restored))
            assertTrue(restored.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `explicit modern false and completed migration override stale legacy true`() {
        for (symbol in listOf(Value, Migrated)) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, symbol, if (symbol == Migrated) 1 else 0)
            player.attr.putAllFromPersistence(mapOf(Legacy to true))
            assertFalse(ErnestFountainState.get(player))
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `writing or clearing state cannot be undone by old saved attributes`() {
        val player = Player()
        for (value in listOf(true, false)) {
            player.attr.putAllFromPersistence(mapOf(Legacy to !value))
            ErnestFountainState.set(player, value)
            assertTrue(player.attr.toPersistentMap().isEmpty())
            player.attr.putAllFromPersistence(mapOf(Legacy to !value))
            assertEquals(value, ErnestFountainState.get(player))
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `first login without old state records completed migration`() {
        val player = Player()
        ErnestFountainState.migrate(player)
        assertFalse(ErnestFountainState.get(player))
        assertEquals(1, player.vars[Migrated])
        player.attr.putAllFromPersistence(mapOf(Legacy to true))
        ErnestFountainState.migrate(player)
        assertFalse(ErnestFountainState.get(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test fun `state and migration marker are permanent without client transmission`() {
        for (symbol in listOf(Value, Migrated)) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    companion object {
        private const val Value = "varp.ernest_piranhas_dead"
        private const val Migrated = "varp.ernest_piranhas_dead_migrated"
        private const val Legacy = "ernest_piranhas_dead"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
