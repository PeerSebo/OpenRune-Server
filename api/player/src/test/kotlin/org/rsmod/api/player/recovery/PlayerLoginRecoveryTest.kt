package org.rsmod.api.player.recovery

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
import org.rsmod.api.attr.AttributeMap
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class PlayerLoginRecoveryTest {
    @Test
    fun `legacy exits retain their packed value through migration and a permanent state reload`() {
        for (exit in listOf(CoordGrid.ZERO, CoordGrid(3215, 3210), CoordGrid(3201, 3202, 3))) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(LegacyExit to exit.packed))

            assertEquals(exit, player.loginExitCoord)
            assertFalse(LegacyExit in player.attr.toPersistentMap())
            val restored = reload(player)
            assertEquals(exit, restored.takeLoginExitCoord())
            assertNull(restored.takeLoginExitCoord())
            assertNull(reload(restored).takeLoginExitCoord())
        }
    }

    @Test
    fun `zero remains a present destination and differs from an absent exit`() {
        val player = Player()
        assertNull(player.loginExitCoord)
        player.loginExitCoord = CoordGrid.ZERO

        val restored = reload(player)
        assertEquals(CoordGrid.ZERO, restored.loginExitCoord)
        assertEquals(1, restored.vars[Pending])
        restored.loginExitCoord = null
        assertNull(reload(restored).loginExitCoord)
    }

    @Test
    fun `new saved exit outranks stale legacy data even before the migration marker is set`() {
        val player = Player()
        val currentExit = CoordGrid(3012, 3456, 1)
        set(player, Packed, currentExit.packed)
        set(player, Pending, 1)
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))

        assertEquals(currentExit, player.takeLoginExitCoord())
        assertFalse(LegacyExit in player.attr.toPersistentMap())
        assertNull(reload(player).loginExitCoord)
    }

    @Test
    fun `explicit cleared presence prevents legacy resurrection without a marker`() {
        val player = Player()
        set(player, Pending, 0)
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))

        assertNull(player.takeLoginExitCoord())
        assertEquals(1, player.vars[Migrated])
        assertFalse(LegacyExit in player.attr.toPersistentMap())
    }

    @Test
    fun `a migration marker prevents a newly restored old attribute from becoming an exit`() {
        val player = Player()
        set(player, Migrated, 1)
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))

        assertNull(player.loginExitCoord)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `partial new state does not import an unrelated legacy destination`() {
        val player = Player()
        set(player, Packed, 0)
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))

        assertNull(player.loginExitCoord)
        assertFalse(LegacyExit in player.attr.toPersistentMap())
    }

    @Test
    fun `normal return clears legacy state before it can be migrated or saved again`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))
        player.loginExitCoord = null

        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))
        assertNull(restored.takeLoginExitCoord())
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `setting a new exit replaces old pending data without moving the player`() {
        val player = Player().apply { coords = CoordGrid(7000, 7000, 2) }
        val source = player.coords
        val exit = CoordGrid(3215, 3210)
        player.attr.putAllFromPersistence(mapOf(LegacyExit to CoordGrid(3333, 3333).packed))
        player.loginExitCoord = exit

        assertEquals(source, player.coords)
        assertEquals(exit, player.loginExitCoord)
        assertTrue(player.attr.toPersistentMap().isEmpty())
        player.coords = requireNotNull(player.takeLoginExitCoord())
        assertEquals(exit, player.coords)
    }

    @Test
    fun `migration and clearing preserve unrelated attributes and player vars`() {
        val player = Player()
        set(player, "varp.option_run", 1)
        player.attr.putAllFromPersistence(mapOf(
            LegacyExit to CoordGrid(3215, 3210).packed,
            "poh_rooms" to mapOf("0_3_3" to 4),
            "unrelated.counter" to 9,
        ))

        player.takeLoginExitCoord()

        assertEquals(1, player.vars["varp.option_run"])
        assertEquals(mapOf("poh_rooms" to mapOf("0_3_3" to 4), "unrelated.counter" to 9), player.attr.toPersistentMap())
    }

    @Test
    fun `invalid legacy types cannot prevent login or create a destination`() {
        for (value in listOf<Any>("broken", listOf(1, 2), mapOf("packed" to 1))) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(LegacyExit to value))
            assertNull(player.takeLoginExitCoord())
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
    }

    @Test
    fun `repeated reads of migrated state do not signal an attribute persistence mutation`() {
        val player = Player()
        player.loginExitCoord = CoordGrid(3215, 3210)
        val previous = AttributeMap.persistenceMutationSink
        var mutations = 0
        AttributeMap.persistenceMutationSink = { mutations++ }
        try {
            repeat(3) { assertEquals(CoordGrid(3215, 3210), player.loginExitCoord) }
            assertEquals(0, mutations)
        } finally {
            AttributeMap.persistenceMutationSink = previous
        }
    }

    @Test
    fun `recovery state is permanent and never sends private coordinates to the client`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client; processedMapClock = 100 }
        player.loginExitCoord = CoordGrid(3215, 3210)
        player.takeLoginExitCoord()

        for (name in listOf(Packed, Pending, Migrated)) {
            val type = requireNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
        assertTrue(client.messages.isEmpty())
    }

    private fun reload(player: Player): Player = Player().also { restored ->
        restored.vars.backing.putAll(player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        })
        restored.attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    private fun set(player: Player, name: String, value: Int) =
        VarPlayerIntMapSetter.set(player, name, value)

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val LegacyExit = "instance_exit_coord"
        private const val Packed = "varp.player_login_exit_coord"
        private const val Pending = "varp.player_login_exit_pending"
        private const val Migrated = "varp.player_login_exit_migrated"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
