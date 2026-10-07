package org.rsmod.api.db.gateway.service

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.rsmod.api.db.Database
import org.rsmod.api.db.gateway.model.GameDbRequest
import org.rsmod.api.db.gateway.model.GameDbResponse
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.server.services.concurrent.ScheduledService
import org.rsmod.server.services.util.safeShutdown

/**
 * Workers execute database requests; only the game thread invokes responses. Retain PlayerUid
 * rather than Player in delayed callbacks so logout cannot leave stale references.
 */
public class ResponseDbGatewayService @Inject constructor(private val database: Database) :
    ScheduledService {
    private val logger = InlineLogger()

    private val pendingRequests = ConcurrentLinkedQueue<PendingRequest<*>>()
    private val pendingCallbacks = ConcurrentLinkedQueue<PendingCallback<*>>()

    private val consecutiveFailureCount = AtomicInteger(0)
    private val shutdownInProgress = AtomicBoolean(false)

    private lateinit var workerExecutor: ExecutorService
    private lateinit var workerScope: CoroutineScope

    public fun <T> request(request: GameDbRequest<T>, response: GameDbResponse<T>) {
        val pendingRequest = PendingRequest(request, response)
        pendingRequests.add(pendingRequest)
    }

    public fun take(dest: MutableCollection<PendingCallback<*>>, n: Int) {
        for (i in 0 until n) {
            val response = pendingCallbacks.poll() ?: break
            dest += response
        }
    }

    override suspend fun run() {
        if (shutdownInProgress.get()) {
            delay(IDLE_LOOP_DELAY_MS)
            return
        }
        val failureBackoffEnabled = consecutiveFailureCount.get() >= MAX_CONSECUTIVE_FAILURES
        val batchPollCount = if (failureBackoffEnabled) 1 else REQUESTS_PER_BATCH

        handlePendingBatch(batchPollCount)

        if (!failureBackoffEnabled) {
            delay(DELAY_PER_BATCH)
            return
        }

        val backoffDelay = ERROR_BACKOFF_MS
        logger.warn { "Too many consecutive failures, backing off for: ${backoffDelay}ms" }
        delay(backoffDelay)
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, SERVICE_THREAD_NAME).apply { isDaemon = true }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    private fun createWorkerExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, "$SERVICE_THREAD_NAME-worker").apply { isDaemon = true }
        }
        return Executors.newFixedThreadPool(WORKER_THREAD_COUNT, threadFactory)
    }

    override suspend fun setup() {}

    override suspend fun startup() {
        workerExecutor = createWorkerExecutor()
        workerScope = CoroutineScope(SupervisorJob() + workerExecutor.asCoroutineDispatcher())
    }

    /** Begin before shutdown ticks so pending responses reach the game thread before it stops. */
    public suspend fun fastForwardShutdown() {
        val alreadyShuttingDown = shutdownInProgress.getAndSet(true)
        check(!alreadyShuttingDown) { "Response db gateway service is already shutting down." }
        logger.info { "Attempting to shutdown response db gateway service." }
        try {
            handleShutdownRequests()
            workerExecutor.safeShutdown()
            logger.info { "Response db gateway service successfully shut down." }
        } catch (e: Exception) {
            logger.error(e) { "Error occurred while fast-forwarding shutdown." }
        }
    }

    /**
     * Note: It is expected that [fastForwardShutdown] is invoked before this function is called. If
     * [fastForwardShutdown] has not been called, an [IllegalStateException] will be thrown to
     * indicate a violation of the shutdown contract.
     */
    override suspend fun shutdown() {
        val alreadyShuttingDown = shutdownInProgress.getAndSet(true)
        if (alreadyShuttingDown) {
            return
        }
        error("Expected shutdown via [fastForwardShutdown]; direct [shutdown] call is invalid.")
    }

    private fun handlePendingBatch(count: Int) {
        for (i in 0 until count) {
            val request = pendingRequests.poll() ?: break
            workerScope.launch { handleRequestWithTimeout(request, REQUEST_TIMEOUT_MS) }
        }
    }

    private suspend fun handleRequestWithTimeout(request: PendingRequest<*>, timeoutMillis: Long) {
        val result = withTimeoutOrNull(timeoutMillis) { handleRequest(request) }
        if (result == null) {
            logger.warn { "Database request timed out (request=$request)" }
            incrementConnectionAttempts(request, GameDbResult.Err.Timeout)
            incrementConsecutiveFailures()
        }
    }

    private suspend fun handleRequest(request: PendingRequest<*>) {
        try {
            val result = database.withTransaction { connection -> request.request(connection) }
            val response = request.response as GameDbResponse<Any?>
            pendingCallbacks.add(PendingCallback(response, result))
            resetConsecutiveFailures()
        } catch (e: Exception) {
            incrementConnectionAttempts(request, GameDbResult.Err.Exception(e))
            incrementConsecutiveFailures()
        }
    }

    private fun incrementConnectionAttempts(request: PendingRequest<*>, err: GameDbResult.Err) {
        if (request.attempts++ < REQUEST_RETRIES_BEFORE_FAILURE) {
            pendingRequests.add(request)
            return
        }
        pendingCallbacks.add(PendingCallback(request.response, err))
        if (err is GameDbResult.Err.Exception) {
            logger.error(err.cause) { "Reached max retry attempts for database request: $request" }
        } else {
            logger.error { "Reached max retry attempts for database request: $request (err=$err)" }
        }
    }

    private fun incrementConsecutiveFailures() {
        consecutiveFailureCount.updateAndGet { curr -> min(curr + 1, MAX_CONSECUTIVE_FAILURES) }
    }

    private fun resetConsecutiveFailures() {
        val previous = consecutiveFailureCount.getAndSet(0)
        if (previous == 0) {
            return
        }
        logger.info { "Response db gateway service recovered after $previous failure(s)." }
    }

    private suspend fun handleShutdownRequests() {
        var requestCount = 0
        val result =
            withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
                while (isActive) {
                    val request = pendingRequests.poll() ?: break
                    handleRequestWithTimeout(request, SHUTDOWN_REQUEST_TIMEOUT_MS)
                    requestCount++
                }
            }

        if (requestCount > 0) {
            logger.info { "Processed $requestCount pending database requests before shutdown." }
        }

        if (result != null) {
            return
        }

        val leftover = pendingRequests.size
        logger.error { "Shutdown timeout expired! Rejected $leftover pending requests." }
        rejectPendingRequests()
    }

    private fun rejectPendingRequests() {
        while (true) {
            val request = pendingRequests.poll() ?: break
            val callback = PendingCallback(request.response, GameDbResult.Err.InternalShutdownError)
            pendingCallbacks.add(callback)
        }
    }

    private data class PendingRequest<T>(
        val request: GameDbRequest<T>,
        val response: GameDbResponse<T>,
        var attempts: Int = 0,
    )

    public data class PendingCallback<T>(
        val response: GameDbResponse<T>,
        val result: GameDbResult<T>,
    ) {
        public operator fun invoke() {
            response(result)
        }
    }

    private companion object {
        private const val SERVICE_THREAD_NAME = "db-response-gateway"
        private const val WORKER_THREAD_COUNT = 4

        private const val DELAY_PER_BATCH = 250L

        private const val REQUESTS_PER_BATCH = 50

        private const val REQUEST_RETRIES_BEFORE_FAILURE = 3

        private const val REQUEST_TIMEOUT_MS = 5000L

        private const val SHUTDOWN_REQUEST_TIMEOUT_MS = 2000L

        private const val ERROR_BACKOFF_MS = 10_000L

        private const val MAX_CONSECUTIVE_FAILURES = 5

        /**
         * A safeguard timeout to prevent the application from waiting indefinitely during service
         * shutdown.
         *
         * Ensures that the shutdown process can be complete even if requests are stuck or
         * unresponsive.
         */
        private const val SHUTDOWN_TIMEOUT_MS = 30_000L

        /**
         * The delay duration (in ms) between run-loop iterations after [fastForwardShutdown] has
         * been called.
         *
         * This prevents the service from entering a tight loop once fast-forward shutdown has been
         * initiated. The delay allows the scheduler to yield and conserve cpu resources during this
         * idle phase.
         */
        // Required because fast-forward shutdowns occur before the main service executor is shut
        // down. As a result, `run` may still be invoked after `fastForwardShutdown` is called.
        private const val IDLE_LOOP_DELAY_MS = 250L
    }
}
