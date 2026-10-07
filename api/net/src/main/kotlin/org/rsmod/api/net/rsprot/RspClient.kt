package org.rsmod.api.net.rsprot

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ExperimentalUnsignedTypes
private typealias Service = NetworkService<Player>

@OptIn(ExperimentalUnsignedTypes::class)
class RspClient(
    private val session: Session<Player>,
    private val infos: Infos,
    private val closeExecutor: Executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS),
) :
    Client<NetworkService<Player>, OutgoingGameMessage> {
    private val closeRequested = AtomicBoolean()

    override fun close() {
        if (closeRequested.compareAndSet(false, true)) {
            // RSProx closes its client socket on upstream EOF; let it deliver Logout first.
            closeExecutor.execute { session.requestClose() }
        }
    }

    override fun write(message: OutgoingGameMessage) {
        session.queue(message)
    }

    override fun read(player: Player) {
        session.processIncomingPackets(player)
    }

    override fun flush() {
        session.flush()
    }

    override fun flushHighPriority() {
        session.discardLowPriorityCategoryPackets()
        session.flush()
    }

    override fun unregister(service: NetworkService<Player>, player: Player) {
        service.infoProtocols.dealloc(infos)
    }
}
