package org.rsmod.api.player.vars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints

@ResourceLock("ServerCacheManager")
@ResourceLock("PlayerPersistenceHints")
class VarPlayerPersistenceHintsTest {
    private val hints = mutableListOf<Player>()

    @BeforeEach
    fun listen() {
        PlayerPersistenceHints.bind { hints += it }
    }

    @AfterEach
    fun cleanup() {
        PlayerPersistenceHints.bind {}
        PlayerPersistenceHints.leave()
    }

    @Test
    fun `first explicit permanent zero requests persistence through either setter`() {
        val varp = definition(PERMANENT_VARP, VarpLifetime.Perm)
        for (path in WritePath.entries) {
            val player = player()
            hints.clear()
            assertFalse(varp in player.vars)

            path.set(player, varp, 0)

            assertTrue(varp in player.vars, path.name)
            assertEquals(0, player.vars[varp])
            assertEquals(listOf(player), hints, path.name)
        }
    }

    @Test
    fun `repeated permanent values remain clean while changed values request persistence`() {
        val varp = definition(PERMANENT_VARP, VarpLifetime.Perm)
        for (path in WritePath.entries) {
            val player = player()
            path.set(player, varp, 0)
            hints.clear()

            path.set(player, varp, 0)
            assertTrue(hints.isEmpty(), path.name)
            path.set(player, varp, 62)
            assertEquals(listOf(player), hints, path.name)
            path.set(player, varp, 62)
            assertEquals(listOf(player), hints, path.name)
            path.set(player, varp, 0)
            assertEquals(listOf(player, player), hints, path.name)
        }
    }

    @Test
    fun `clearing a present permanent zero requests persistence exactly once`() {
        val varp = definition(PERMANENT_VARP, VarpLifetime.Perm)
        for (path in WritePath.entries) {
            val player = player()
            path.set(player, varp, 0)
            hints.clear()

            VarPlayerIntMapSetter.clear(player, PERMANENT_VARP)

            assertFalse(varp in player.vars, path.name)
            assertEquals(listOf(player), hints, path.name)
            VarPlayerIntMapSetter.clear(player, PERMANENT_VARP)
            assertEquals(listOf(player), hints, path.name)
        }
    }

    @Test
    fun `temporary writes and clears never request persistence`() {
        val varp = definition(TEMPORARY_VARP, VarpLifetime.Temp)
        for (path in WritePath.entries) {
            val player = player()
            hints.clear()
            for (value in listOf(0, 42, 42, 0)) {
                path.set(player, varp, value)
                assertEquals(value, player.vars[varp])
            }
            assertTrue(varp in player.vars, path.name)

            VarPlayerIntMapSetter.clear(player, TEMPORARY_VARP)
            assertFalse(varp in player.vars, path.name)
            path.set(player, varp, 42)
            VarPlayerIntMapSetter.clear(player, TEMPORARY_VARP)
            VarPlayerIntMapSetter.clear(player, TEMPORARY_VARP)

            assertFalse(varp in player.vars, path.name)
            assertTrue(hints.isEmpty(), path.name)
        }
    }

    @Test
    fun `before engine login writes and clears do not request persistence or send packets`() {
        val varp = definition(PERMANENT_VARP, VarpLifetime.Perm)
        for (clock in listOf(-1, 0)) {
            for (path in WritePath.entries) {
                val client = RecordingClient()
                val player = Player(client).apply { processedMapClock = clock }
                hints.clear()

                path.set(player, varp, 0)
                path.set(player, varp, 62)
                assertEquals(62, player.vars[varp])
                VarPlayerIntMapSetter.clear(player, PERMANENT_VARP)

                assertFalse(varp in player.vars, path.name)
                assertTrue(hints.isEmpty(), "$path at $clock")
                assertTrue(client.messages.isEmpty(), "$path at $clock")
            }
        }
    }

    @Test
    fun `transmission rules remain independent of presence and persistence scope`() {
        val native = definition(PERMANENT_VARP, VarpLifetime.Perm)
        for (scope in VarpLifetime.entries) {
            for (transmit in VarpTransmitLevel.entries) {
                val varp = native.copy(scope = scope, transmit = transmit)
                for (path in WritePath.entries) {
                    val client = RecordingClient()
                    val player = Player(client).apply { processedMapClock = 100 }
                    hints.clear()

                    for (value in listOf(0, 1, 1, 0)) path.set(player, varp, value)

                    val expected = when (transmit) {
                        VarpTransmitLevel.Never -> emptyList()
                        VarpTransmitLevel.OnSetDifferent -> listOf(1, 0)
                        VarpTransmitLevel.OnSetAlways -> listOf(0, 1, 1, 0)
                    }
                    assertEquals(
                        expected,
                        client.messages.filterIsInstance<VarpSmall>().map { it.value },
                        "$path with $scope and $transmit",
                    )
                    assertEquals(expected.size, client.messages.size)
                    assertEquals(if (scope == VarpLifetime.Perm) 3 else 0, hints.size)
                }
            }
        }
    }

    private fun player(): Player = Player().apply { processedMapClock = 100 }

    private fun definition(symbol: String, scope: VarpLifetime): VarpServerType {
        val definition = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
        assertEquals(scope, definition.scope, symbol)
        return definition
    }

    private enum class WritePath {
        Setter,
        Delegate;

        fun set(player: Player, varp: VarpServerType, value: Int) {
            when (this) {
                Setter -> VarPlayerIntMapSetter.set(player, varp, value)
                Delegate -> VarPlayerIntMapDelegate.from(player)[varp] = value
            }
        }
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
    }

    companion object {
        private const val PERMANENT_VARP = "varp.option_master_volume"
        private const val TEMPORARY_VARP = "varp.player_stun_message_cycle"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
