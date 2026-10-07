package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.sailing.SailingSessionState.helmInteractionWorld
import org.rsmod.content.skills.sailing.SailingSessionState.shipyardReturn
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class SailingSessionStateTest {
    @Test
    fun `local aliases retain the native scripts and cargo categories`() {
        assertEquals(8912, "clientscript.sailing_port_task_board_init".asRSCM())
        assertEquals(8901, "clientscript.sailing_port_task_info_init".asRSCM())
        assertEquals(2294, SailingCargoCategories.COURIER_CARGO)
        assertEquals(2295, SailingCargoCategories.BOUNTY_HARVEST)
        assertEquals(2296, SailingCargoCategories.SHIPWRECK_SALVAGE)
        assertEquals(2289, SailingCargoCategories.FILLED_FISH_CRATE)
        assertEquals(2280, SailingCargoCategories.REPAIR_KIT)
    }

    @Test
    fun `all legacy packed coordinates including zero survive import and reload`() {
        for (packed in listOf(0, CoordGrid(2808, 3437, 0).packed, CoordGrid(3200, 3200, 3).packed)) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(Legacy to packed, "unrelated" to 9))
            assertEquals(CoordGrid(packed), player.shipyardReturn)
            assertEquals(mapOf("unrelated" to 9), player.attr.toPersistentMap())
            assertEquals(CoordGrid(packed), reload(player).shipyardReturn)
        }
    }

    @Test
    fun `cleared and current return destinations take precedence over stale legacy saves`() {
        val player = Player()
        player.shipyardReturn = null
        player.attr.putAllFromPersistence(mapOf(Legacy to CoordGrid(2808, 3437).packed))
        assertNull(player.shipyardReturn)
        assertNull(reload(player).shipyardReturn)
        player.shipyardReturn = CoordGrid(0)
        player.attr.putAllFromPersistence(mapOf(Legacy to CoordGrid(2808, 3437).packed))
        assertEquals(CoordGrid(0), player.shipyardReturn)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `explicit absent destination wins before migration while packed zero remains valid`() {
        val absent = Player()
        VarPlayerIntMapSetter.set(absent, "varp.sailing_shipyard_return_saved", 0)
        absent.attr.putAllFromPersistence(mapOf(Legacy to 123))
        assertNull(absent.shipyardReturn)
        val zero = Player()
        VarPlayerIntMapSetter.set(zero, "varp.sailing_shipyard_return_coord", 0)
        zero.attr.putAllFromPersistence(mapOf(Legacy to 123))
        assertEquals(CoordGrid(0), zero.shipyardReturn)
    }

    @Test
    fun `helm world zero is distinguishable from no interaction and never persists`() {
        val player = Player()
        assertNull(player.helmInteractionWorld)
        for (world in listOf(0, 1, 65535)) {
            player.helmInteractionWorld = world
            assertEquals(world, player.helmInteractionWorld)
            assertNull(reload(player).helmInteractionWorld)
            player.helmInteractionWorld = null
            assertNull(player.helmInteractionWorld)
        }
    }

    @Test
    fun `cache preserves temporary helm state and permanent return state without transmission`() {
        for ((symbol, scope) in listOf(
            "varp.sailing_helm_interaction_world" to VarpLifetime.Temp,
            "varp.sailing_shipyard_return_coord" to VarpLifetime.Perm,
            "varp.sailing_shipyard_return_saved" to VarpLifetime.Perm,
            "varp.sailing_shipyard_return_migrated" to VarpLifetime.Perm,
        )) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(scope, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
    }

    private fun reload(player: Player): Player = Player().also { restored ->
        for ((id, value) in player.vars) {
            if (ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm) {
                restored.vars.backing[id] = value
            }
        }
        restored.attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    companion object {
        private const val Legacy = "sailing_shipyard_return_coord"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
