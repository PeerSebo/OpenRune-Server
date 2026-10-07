package org.rsmod.api.player.output

import dev.openrune.types.varp.VarpServerType
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient

class VarpSyncTest {
    @Test
    fun `native long varps retain their packet type even for small values`() {
        val packets = mutableListOf<Any>()
        val client = object : Client<Any, Any> by NoopClient {
            override fun write(message: Any) { packets += message }
        }
        val type = VarpServerType(id = 5753, longValue = true)
        for (value in listOf(-1, 0, 1, Int.MAX_VALUE)) {
            VarpSync.writeVarp(client, type, value)
        }
        assertEquals(listOf(-1, 0, 1, Int.MAX_VALUE).map { VarpLong(5753, it.toLong()) }, packets)
    }

    @Test
    fun `ordinary varps keep the existing byte and integer packet boundaries`() {
        val packets = mutableListOf<Any>()
        val client = object : Client<Any, Any> by NoopClient {
            override fun write(message: Any) { packets += message }
        }
        for (value in listOf(-129, -128, 127, 128)) {
            VarpSync.writeVarp(client, VarpServerType(id = 1), value)
        }
        assertEquals(listOf(VarpLarge(1, -129), VarpSmall(1, -128), VarpSmall(1, 127), VarpLarge(1, 128)), packets)
    }
}
