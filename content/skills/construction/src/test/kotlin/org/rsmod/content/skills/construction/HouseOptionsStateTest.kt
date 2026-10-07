package org.rsmod.content.skills.construction

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
class HouseOptionsStateTest {
    @Test fun `legacy preference survives reload and does not overwrite explicit zero`() {
        for (value in 0..1) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf("poh_tele_inside" to value, "unrelated" to 9))
            HouseOptionsState.migrate(player)
            assertEquals(value, HouseOptionsState.teleportInside(player))
            assertEquals(mapOf("unrelated" to 9), player.attr.toPersistentMap())
            val restored = Player().apply {
                vars.backing.putAll(player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
            }
            assertEquals(value, HouseOptionsState.teleportInside(restored))
        }
        val cleared = Player()
        VarPlayerIntMapSetter.set(cleared, Preference, 0)
        cleared.attr.putAllFromPersistence(mapOf("poh_tele_inside" to 1))
        assertEquals(0, HouseOptionsState.teleportInside(cleared))
        assertTrue(cleared.attr.toPersistentMap().isEmpty())
    }

    @Test fun `option writes and migration marker prevent old preference resurrection`() {
        val player = Player()
        assertEquals(0, HouseOptionsState.teleportInside(player))
        HouseOptionsState.setTeleportInside(player, 1)
        assertEquals(1, HouseOptionsState.teleportInside(player))
        HouseOptionsState.setTeleportInside(player, 0)
        player.attr.putAllFromPersistence(mapOf("poh_tele_inside" to 1))
        assertEquals(0, HouseOptionsState.teleportInside(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
        val marked = Player()
        VarPlayerIntMapSetter.set(marked, Migrated, 1)
        marked.attr.putAllFromPersistence(mapOf("poh_tele_inside" to 1))
        assertEquals(0, HouseOptionsState.teleportInside(marked))
    }

    @Test fun `house preference and marker are permanent server state`() {
        for (name in listOf(Preference, Migrated)) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    companion object {
        private const val Preference = "varp.poh_teleport_inside"
        private const val Migrated = "varp.poh_teleport_inside_migrated"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
