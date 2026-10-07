package org.rsmod.content.skills.hunter

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
class HunterPlayerVarsTest {
    @Test
    fun `legacy state imports once without consuming pending harvest or sack rewards`() {
        val player = Player()
        val retained = mapOf(
            "hunter_pending_herbiboar_harvest" to "obj.grimy_guam_leaf:3",
            "hunter_sack_pending_expert" to "obj.logs,10,true",
        )
        player.attr.putAllFromPersistence(legacy + retained)

        assertTrue(player.falconryInvested)
        assertEquals(321, player.herbiboarCount)
        assertEquals(9, player.pursuitRingUsed)
        assertEquals(retained, player.attr.toPersistentMap())
    }

    @Test
    fun `explicit saved zero wins independently before first migration`() {
        for (symbol in listOf("hunter_falconry_invested", "hunter_herbiboar_count", "hunter_pursuit_ring_used")) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, "varp.$symbol", 0)
            player.attr.putAllFromPersistence(legacy)
            player.migrateHunterPlayerState()
            assertEquals(symbol != "hunter_falconry_invested", player.falconryInvested)
            assertEquals(if (symbol == "hunter_herbiboar_count") 0 else 321, player.herbiboarCount)
            assertEquals(if (symbol == "hunter_pursuit_ring_used") 0 else 9, player.pursuitRingUsed)
        }
    }

    @Test
    fun `first write migrates before mutation and stale saves cannot restore broken ring charges`() {
        val player = Player()
        player.attr.putAllFromPersistence(legacy)
        player.pursuitRingUsed = 0
        player.herbiboarCount++
        player.falconryInvested = false
        player.activeMagicBoxCount = 2
        player.activePitfallCount = 1

        val reconnected = Player()
        reconnected.vars.backing.putAll(player.vars.backing.filter { (id, _) ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        reconnected.attr.putAllFromPersistence(legacy)
        reconnected.migrateHunterPlayerState()

        assertFalse(reconnected.falconryInvested)
        assertEquals(322, reconnected.herbiboarCount)
        assertEquals(0, reconnected.pursuitRingUsed)
        assertEquals(0, reconnected.activeMagicBoxCount)
        assertEquals(0, reconnected.activePitfallCount)
        assertTrue(reconnected.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `new accounts retain defaults when stale attributes appear later`() {
        val player = Player()
        player.migrateHunterPlayerState()
        player.attr.putAllFromPersistence(legacy)
        assertFalse(player.falconryInvested)
        assertEquals(0, player.herbiboarCount)
        assertEquals(0, player.pursuitRingUsed)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `full width tracking totals and charge progress survive migration unchanged`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf("hunter_herbiboar_count" to Int.MAX_VALUE, "hunter_pursuit_ring_used" to 7))
        assertEquals(Int.MAX_VALUE, player.herbiboarCount)
        assertEquals(7, player.pursuitRingUsed)
    }

    @Test
    fun `packed hunter state has permanent saves and temporary trap counts without transmission`() {
        for ((symbol, scope) in listOf(
            "hunter_falconry_invested" to VarpLifetime.Perm,
            "hunter_herbiboar_count" to VarpLifetime.Perm,
            "hunter_pursuit_ring_used" to VarpLifetime.Perm,
            "hunter_player_state_migrated" to VarpLifetime.Perm,
            "hunter_magic_box_count" to VarpLifetime.Temp,
            "hunter_pitfall_count" to VarpLifetime.Temp,
        )) {
            val type = checkNotNull(ServerCacheManager.getVarp("varp.$symbol".asRSCM()))
            assertEquals(scope, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    companion object {
        private val legacy = mapOf(
            "hunter_falconry_invested" to true,
            "hunter_herbiboar_count" to 321,
            "hunter_pursuit_ring_used" to 9,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
