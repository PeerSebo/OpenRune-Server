package org.rsmod.api.db.gateway

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.rsmod.api.db.gateway.service.ResponseDbGatewayService

public class GameDbSynchronizer @Inject constructor(private val gateway: ResponseDbGatewayService) {
    private val callbackBuffer = mutableListOf<ResponseDbGatewayService.PendingCallback<*>>()

    /**
     * Invokes up to [count] pending callbacks from the [ResponseDbGatewayService] on the caller
     * thread.
     *
     * _Note: This method should only be called by the game-thread._
     */
    public fun invokeCallbacks(count: Int) {
        gateway.take(callbackBuffer, count)
        for (callback in callbackBuffer) {
            callback.invoke()
        }
        callbackBuffer.clear()
    }

    /**
     * Run exactly once before shutdown ticks while the game thread can still dispatch pending
     * database responses.
     */
    public fun blockingFastForwardShutdown(): Unit = runBlocking {
        val timeout = TimeUnit.MINUTES.toMillis(WATCHDOG_TIMEOUT_MINS.toLong())
        val success = withTimeoutOrNull(timeout) { gateway.fastForwardShutdown() }
        if (success == null) {
            logger.error {
                "Shutdown watchdog timeout exceeded ($WATCHDOG_TIMEOUT_MINS minutes). " +
                    "Some database gateways may not have completed shutdown properly."
            }
        }
    }

    private companion object {
        private const val WATCHDOG_TIMEOUT_MINS = 2
        private val logger = InlineLogger()
    }
}
