package org.rsmod.content.other.ironman

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
class IronmanTutorStateTest {
    @Test fun `both legacy values migrate and survive permanent-only save reload`() {
        for (value in listOf(false, true)) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(Legacy to value, "unrelated" to 3))
            IronmanTutorState.migrate(player)
            assertEquals(value, IronmanTutorState.get(player))
            assertEquals(mapOf("unrelated" to 3), player.attr.toPersistentMap())
            val restored = Player().apply {
                vars.backing.putAll(player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
            }
            assertEquals(value, IronmanTutorState.get(restored))
            assertTrue(restored.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `explicit modern false and completed migration override stale legacy true`() {
        for (symbol in listOf(Value, Migrated)) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, symbol, if (symbol == Migrated) 1 else 0)
            player.attr.putAllFromPersistence(mapOf(Legacy to true))
            assertFalse(IronmanTutorState.get(player))
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `writing or clearing state cannot be undone by old saved attributes`() {
        val player = Player()
        for (value in listOf(true, false)) {
            player.attr.putAllFromPersistence(mapOf(Legacy to !value))
            IronmanTutorState.set(player, value)
            assertTrue(player.attr.toPersistentMap().isEmpty())
            player.attr.putAllFromPersistence(mapOf(Legacy to !value))
            assertEquals(value, IronmanTutorState.get(player))
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test fun `first login without old state records completed migration`() {
        val player = Player()
        IronmanTutorState.migrate(player)
        assertFalse(IronmanTutorState.get(player))
        assertEquals(1, player.vars[Migrated])
        player.attr.putAllFromPersistence(mapOf(Legacy to true))
        IronmanTutorState.migrate(player)
        assertFalse(IronmanTutorState.get(player))
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
        private const val Value = "varp.ironman_tutor_adam_met"
        private const val Migrated = "varp.ironman_tutor_adam_met_migrated"
        private const val Legacy = "ironman_tutor_adam_met"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
