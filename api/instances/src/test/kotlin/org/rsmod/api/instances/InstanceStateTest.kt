package org.rsmod.api.instances

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
class InstanceStateTest {
    @Test
    fun `membership preserves the complete long including unsigned low bits and zero`() {
        val player = Player()
        assertNull(player.currentInstanceId())
        val values = listOf(0L, 1L, Int.MAX_VALUE.toLong(), 0xffffffffL, 0x12345678abcdef01L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)
        for (value in values) {
            val id = InstanceId(value)
            player.assignInstance(id)
            assertEquals(id, player.currentInstanceId())
            assertTrue(player.inInstance(id))
            assertFalse(player.inInstance(InstanceId(value xor 1L)))
            assertEquals(1, player.vars["varbit.player_in_instance"])
        }
    }

    @Test
    fun `clearing membership removes all parts and leaves other state alone`() {
        val player = Player()
        player.assignInstance(InstanceId(Long.MIN_VALUE))
        player.recordInstanceKillBest("obor", 15)
        player.clearInstance()
        player.clearInstance()

        assertNull(player.currentInstanceId())
        assertEquals(0, player.vars["varbit.player_in_instance"])
        assertEquals(15, player.instanceKillBest("obor"))
        for (symbol in stateVars) assertEquals(0, player.vars[symbol])
    }

    @Test
    fun `membership is server only temporary state and cannot survive a save reload`() {
        val player = Player()
        player.assignInstance(InstanceId(0x1234567887654321L))
        val restored = Player()
        for ((id, value) in player.vars) {
            val type = requireNotNull(ServerCacheManager.getVarp(id))
            if (type.scope == VarpLifetime.Perm) VarPlayerIntMapSetter.set(restored, type, value)
        }

        assertNull(restored.currentInstanceId())
        for (symbol in stateVars) {
            val type = requireNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    companion object {
        private val stateVars = listOf("varp.player_instance_id_low", "varp.player_instance_id_high", "varp.player_instance_id_present")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
