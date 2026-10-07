package org.rsmod.api.poh

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.recovery.takeLoginExitCoord
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class PohRecoveryTest {
    @Test
    fun `house entry prepares persistent recovery before any logout hook can run`() {
        for (location in PohLocation.entries) {
            val player = Player()
            player.beginHouseRecovery(location.exitCoords)
            val restored = reload(player)

            assertEquals(location.exitCoords, restored.takeLoginExitCoord())
            restored.endHouseRecovery()
            assertNull(reload(restored).takeLoginExitCoord())
        }
    }

    @Test
    fun `leaving by portal or teleport cancels the house return`() {
        val player = Player()
        player.beginHouseRecovery(PohLocation.TAVERLEY.exitCoords)
        player.endHouseRecovery()
        assertNull(reload(player).takeLoginExitCoord())
        assertEquals(0, player.vars["varp.poh_login_exit_coord"])
    }

    @Test
    fun `house cleanup preserves a later recovery destination owned by another activity`() {
        val player = Player()
        player.beginHouseRecovery(PohLocation.TAVERLEY.exitCoords)
        val otherExit = CoordGrid(3000, 3200)
        player.loginExitCoord = otherExit
        player.endHouseRecovery()

        assertEquals(otherExit, reload(player).takeLoginExitCoord())
    }

    @Test
    fun `players without a house marker retain their recovery destination`() {
        val player = Player()
        player.loginExitCoord = CoordGrid(3000, 3200)
        player.endHouseRecovery()
        assertEquals(CoordGrid(3000, 3200), player.loginExitCoord)
    }

    @Test
    fun `house recovery ownership is permanent and never transmitted`() {
        val type = requireNotNull(ServerCacheManager.getVarp("varp.poh_login_exit_coord".asRSCM()))
        assertEquals(VarpLifetime.Perm, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    private fun reload(player: Player): Player = Player().also { restored ->
        for ((id, value) in player.vars) {
            val type = requireNotNull(ServerCacheManager.getVarp(id))
            if (type.scope == VarpLifetime.Perm) VarPlayerIntMapSetter.set(restored, type, value)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
