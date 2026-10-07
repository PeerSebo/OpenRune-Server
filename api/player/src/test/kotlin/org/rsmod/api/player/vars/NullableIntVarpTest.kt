package org.rsmod.api.player.vars

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class NullableIntVarpTest {
    private var Player.value by nullableIntVarp("varp.player_stun_message_cycle")
    private var ProtectedAccess.value by nullableIntVarp("varp.player_stun_message_cycle")
    private var Player.nativeValue by nullableIntVarp("varp.com_mode")

    @Test fun `absence explicit zero and negative sentinel remain distinct`() {
        val player = Player()
        assertNull(player.value)
        for (value in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            player.value = value
            assertEquals(value, player.value)
            assertTrue("varp.player_stun_message_cycle" in player.vars)
            player.value = null
            assertNull(player.value)
            assertFalse("varp.player_stun_message_cycle" in player.vars)
        }
        player.value = null
        assertNull(player.value)
    }

    @Test fun `protected access uses the same nullable value and clearing`() {
        val player = Player()
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        access.value = 0
        assertEquals(0, player.value)
        player.value = -1
        assertEquals(-1, access.value)
        access.value = null
        assertNull(player.value)
    }

    @Test fun `clearing client-visible varps synchronizes zero and repeated clearing is silent`() {
        val messages = mutableListOf<Any>()
        val player = Player().apply {
            processedMapClock = 100
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }
        player.nativeValue = 1
        player.nativeValue = null
        assertEquals(listOf(1, 0), messages.filterIsInstance<VarpSmall>().map { it.value })
        assertNull(player.nativeValue)
        val count = messages.size
        player.nativeValue = null
        assertEquals(count, messages.size)
        player.value = 42
        player.value = null
        assertEquals(count, messages.size)
    }

    @Test fun `nullable variables reject bitfields instead of erasing adjacent state`() {
        assertThrows(IllegalArgumentException::class.java) {
            nullableIntVarp("varbit.option_hide_rooftops")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VarPlayerIntMapSetter.clear(Player(), "varbit.option_hide_rooftops")
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
