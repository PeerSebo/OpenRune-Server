package org.rsmod.api.net.rsprot

import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import java.util.ArrayDeque
import java.util.Queue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.message.OutgoingGameMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player
import sun.misc.Unsafe

class RspClientCloseTest {
    @Test fun `close leaves the socket open for logout delivery and schedules only one fallback`() {
        val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        try {
            @Suppress("UNCHECKED_CAST")
            val session = unsafe.allocateInstance(Session::class.java) as Session<Player>
            val status = Session::class.java.getDeclaredField("channelStatus").apply { isAccessible = true }
            val open = status.type.enumConstants.single { it.toString() == "OPEN" }
            status.set(session, open)
            field("ctx").set(session, channel.pipeline().firstContext())
            field("disconnectionHook").set(session, AtomicReference<Runnable>())
            field("outgoingMessageQueues").set(session,
                arrayOf<Queue<OutgoingGameMessage>>(ArrayDeque()))
            val tasks = mutableListOf<Runnable>()
            val client = RspClient(session, unsafe.allocateInstance(Infos::class.java) as Infos,
                Executor { tasks += it })
            client.close()
            client.close()
            assertEquals(1, tasks.size)
            assertSame(open, status.get(session))
            assertTrue(channel.isOpen)
            assertTrue(channel.config().isAutoRead)
            tasks.single().run()
            channel.runPendingTasks()
            assertEquals("CLOSING", status.get(session).toString())
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun field(name: String) = Session::class.java.getDeclaredField(name).apply { isAccessible = true }

    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
        .apply { isAccessible = true }.get(null) as Unsafe
}
