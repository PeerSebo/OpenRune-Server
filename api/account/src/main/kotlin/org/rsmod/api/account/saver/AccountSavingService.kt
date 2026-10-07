package org.rsmod.api.account.saver

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.journal.AccountSaveLease
import org.rsmod.api.account.saver.journal.SaveRevision
import org.rsmod.api.account.saver.journal.SaveRevisionStore
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.concurrent.ScheduledService

// Serialize saves to avoid overlapping game-database transactions.
public class AccountSavingService
@Inject
constructor(
    private val database: GameDatabase,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
    private val serverConfig: ServerConfig,
    private val preparationHooks: Set<PlayerSavePreparationHook>,
    private val recovery: AccountSaveRecovery,
    private val journal: AccountSaveJournal,
) : ScheduledService {
    public constructor(
        database: GameDatabase,
        repository: CharacterAccountRepository,
        pipelines: Set<CharacterDataStage.Pipeline>,
        serverConfig: ServerConfig,
    ) : this(database, repository, pipelines, serverConfig, emptySet(),
        AccountSaveRecovery(com.fasterxml.jackson.databind.ObjectMapper()),
        AccountSaveJournal(com.fasterxml.jackson.databind.ObjectMapper()))

    private val logger = InlineLogger()

    private val pendingRequests = AccountSaveQueue(MAX_OUTSTANDING_REQUESTS)
    private val overloadReported = AtomicBoolean(false)

    private val pendingBatch = ArrayDeque<AccountSaveRequest>(REQUESTS_PER_BATCH)
    private var consecutiveFailureCount = AtomicInteger(0)
    private val nextSequence = AtomicLong()
    private val failedSequence = ConcurrentHashMap<Int, Long>()

    public fun queue(request: AccountSaveRequest) {
        if (request.player.pendingAccountCommit != null) {
            safeInvokeCallback(request, AccountSaveResponse.QueueFull(request.player))
            return
        }
        if (!pendingRequests.reserve()) {
            if (overloadReported.compareAndSet(false, true)) {
                logger.warn { "Account save queue is full; retaining player state for retry." }
            }
            safeInvokeCallback(request, AccountSaveResponse.QueueFull(request.player))
            return
        }
        if (request.preparedSegments == null) {
            try {
                prepare(request)
            } catch (failure: Exception) {
                pendingRequests.cancelReservation()
                request.preparedSegments = null
                failedSequence.merge(request.characterId, request.sequence, ::maxOf)
                logger.error(failure) { "Could not prepare account save for: ${request.player}" }
                safeInvokeCallback(request, AccountSaveResponse.PreparationFailure(request.player))
                return
            }
        }
        pendingRequests.addReserved(request)
    }

    private fun prepare(request: AccountSaveRequest) {
        request.sequence = nextSequence.incrementAndGet()
        val lease = AccountSaveLease.get(request.player)
        check(lease.claimed) { "Save session has not been claimed" }
        request.saveGeneration = lease.revision.generation
        for (hook in preparationHooks) hook.prepare(request.player)
        request.preparedSegments = buildList {
            add(repository.prepareSave(request.player, serverConfig.world))
            for (pipeline in pipelines) add(pipeline.prepareSave(request.player))
        }
        request.recoverySnapshot = recovery.capture(request)
    }

    public fun queueAtomic(
        first: org.rsmod.game.entity.Player,
        second: org.rsmod.game.entity.Player,
        audit: Map<String, Any?> = emptyMap(),
    ): AtomicAccountSave? {
        require(first !== second && first.characterId > 0 && second.characterId > 0 && first.characterId != second.characterId)
        if (first.pendingAccountCommit != null || second.pendingAccountCommit != null ||
            first.loggingOut || second.loggingOut || !pendingRequests.reserve()) return null
        val requests = listOf(first, second).map { AccountSaveRequest(it.accountId, it.characterId, it, {}) }
        val group = AtomicAccountSave(requests, audit)
        for (request in requests) request.player.pendingAccountCommit = group
        try {
            for (request in requests) {
                prepare(request)
                check(request.recoverySnapshot?.get("unrecoverableSegments") == 0) { "Joint saves require every recovery codec" }
                request.atomicGroup = group
            }
            pendingRequests.addReserved(requests.first())
            return group
        } catch (failure: Exception) {
            for (request in requests) request.player.pendingAccountCommit = null
            pendingRequests.cancelReservation()
            logger.error(failure) { "Could not prepare joint character save" }
            return null
        }
    }

    override suspend fun run() {
        val failureBackoffEnabled = consecutiveFailureCount.get() >= MAX_CONSECUTIVE_FAILURES
        val batchPollCount = if (failureBackoffEnabled) 1 else REQUESTS_PER_BATCH

        batchPendingRequests(batchPollCount)
        handlePendingBatch()

        if (pendingRequests.outstanding() < MAX_OUTSTANDING_REQUESTS / 2 &&
            overloadReported.compareAndSet(true, false)) {
            logger.info { "Account save queue recovered." }
        }

        if (!failureBackoffEnabled) {
            val backlog = !pendingRequests.isEmpty()
            delay(if (backlog) DELAY_WHEN_BACKLOG_MS else DELAY_PER_BATCH)
            return
        }

        val backoffDelay = ERROR_BACKOFF_MS
        logger.warn { "Too many consecutive failures, backing off for: ${backoffDelay}ms" }
        delay(backoffDelay)
    }

    private fun batchPendingRequests(count: Int) {
        for (i in 0 until count) {
            val request = pendingRequests.poll() ?: break
            pendingBatch += request
        }
    }

    private suspend fun handlePendingBatch() {
        while (pendingBatch.isNotEmpty()) {
            val request = pendingBatch.first()
            handleRequestWithTimeout(request, REQUEST_TIMEOUT_MS)
            // Keep the in-flight request until it finishes. Cancellation must leave it and the
            // unprocessed remainder available for the shutdown drain.
            pendingBatch.removeFirst()
        }
    }

    private suspend fun handleRequestWithTimeout(request: AccountSaveRequest, timeoutMillis: Long): Boolean {
        if (pendingRequests.isCommitted(request)) {
            invokeSaveCallback(request)
            return true
        }
        val result = withTimeoutOrNull(timeoutMillis) {
            try {
                saveSegments(request)
                Result.success(Unit)
            } catch (e: CancellationException) {
                // Cancellation retains the current batch entry for the final shutdown drain.
                throw e
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }
        }
        if (result == null) {
            logger.warn { "Account save timed out for: ${request.player}" }
            incrementSaveAttempts(request)
            incrementConsecutiveFailures()
            return false
        }
        if (result.isFailure) {
            incrementSaveAttempts(request)
            incrementConsecutiveFailures()
            logger.warn(checkNotNull(result.exceptionOrNull())) { "Could not save player account: ${request.player}" }
            return false
        }
        // Timeout and retry decisions stay outside the cancelled scope, so a late JDBC completion
        // cannot both reply to a caller and enqueue the same request again.
        pendingRequests.markCommitted(request)
        for (member in request.atomicGroup?.requests ?: listOf(request)) {
            failedSequence.computeIfPresent(member.characterId) { _, failed ->
                failed.takeIf { it > member.sequence }
            }
        }
        try {
            request.atomicGroup?.let { group ->
                for (member in group.requests) {
                    journal.committed(member.characterId, SaveRevision(checkNotNull(member.saveGeneration), member.sequence))
                    recovery.committed(member.characterId, member.sequence)
                }
                journal.committedAtomic(group)
            }
            journal.committed(request.characterId, SaveRevision(checkNotNull(request.saveGeneration), request.sequence))
            recovery.committed(request.characterId, request.sequence)
        } catch (failure: Exception) {
            logger.warn(failure) { "Saved character ${request.characterId}, but could not mark its recovery files resolved." }
        }
        invokeSaveCallback(request)
        resetConsecutiveFailures()
        return true
    }

    private suspend fun saveSegments(request: AccountSaveRequest) {
        request.atomicGroup?.let { group ->
            journal.persistAtomic(group)
            database.withTransaction { journal.saveAtomic(it, group) }
            return
        }
        journal.persist(request)
        database.withTransaction { connection ->
            val current = SaveRevisionStore.lock(connection, request.characterId)
            check(current.generation == request.saveGeneration) { "A newer login owns character ${request.characterId}" }
            if (current.sequence < request.sequence) {
                for (segment in checkNotNull(request.preparedSegments)) {
                    segment.save(connection, request.characterId)
                }
                SaveRevisionStore.update(connection, request.characterId,
                    SaveRevision(current.generation, request.sequence))
            }
        }
    }

    private fun invokeSaveCallback(request: AccountSaveRequest) {
        safeInvokeCallback(request, AccountSaveResponse.Success(request.player))
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, SERVICE_THREAD_NAME).apply { isDaemon = true }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    override suspend fun setup() {
        database.withTransaction { SaveRevisionStore.install(it) }
        val unresolved = recovery.unresolvedCount()
        if (unresolved > 0) {
            logger.warn {
                "$unresolved terminal-save recovery record(s) remain in .data/account-save-recovery. " +
                    "Versioned journals are checked before login; legacy records require manual review."
            }
        }
        logger.debug {
            "Account saving service accepting $REQUESTS_PER_BATCH requests / $DELAY_PER_BATCH ms."
        }
    }

    override suspend fun startup() {}

    override suspend fun shutdown() {
        logger.info { "Attempting to shutdown account saving service." }
        try {
            handleShutdownRequests()
            check(failedSequence.isEmpty() && pendingBatch.isEmpty() && pendingRequests.isEmpty()) {
                val pendingIds = pendingBatch.map { it.characterId } + pendingRequests.snapshot().map { it.characterId }
                val unsavedIds = (failedSequence.keys + pendingIds).toSortedSet()
                "Account saves remain uncommitted for character(s): $unsavedIds. " +
                    "Review account-save-recovery files and save failure logs before restoring data."
            }
            logger.info { "Account saving service successfully shut down." }
        } catch (t: Throwable) {
            logger.error(t) { "Account saving service failed to shut down." }
            throw t
        }
    }

    private suspend fun handleShutdownRequests() {
        var saveCount = 0
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
            while (isActive) {
                if (pendingBatch.isEmpty()) {
                    pendingBatch.addLast(pendingRequests.poll() ?: break)
                }
                val request = pendingBatch.first()
                if (handleRequestWithTimeout(request, SHUTDOWN_REQUEST_TIMEOUT_MS)) {
                    saveCount++
                }
                pendingBatch.removeFirst()
            }
        }

        if (saveCount > 0) {
            logger.info { "Saved $saveCount pending requests before shut down." }
        }

        // A blocking JDBC call may finish through the timeout's undispatched fast path, returning
        // normally after the deadline made isActive false. Queue emptiness, rather than the timeout
        // result, proves that every request was handled; leftovers still need failure callbacks.
        if (pendingBatch.isEmpty() && pendingRequests.isEmpty()) {
            return
        }

        logger.error { "Force-save failed due to timeout. Rejecting all pending requests..." }

        val leftover = pendingBatch.size + pendingRequests.snapshot().size
        val emergencyRejection =
            withTimeoutOrNull(SHUTDOWN_EMERGENCY_TIMEOUT_MS) { rejectPendingRequests() }

        if (emergencyRejection != null) {
            logger.info { "Sent out $leftover pending request max-retry responses." }
            return
        }

        val usernames = pendingRequests.snapshot().take(100).joinToString(",") { it.player.displayName }
        logger.error {
            "Emergency shutdown measure timed out! " +
                "(${pendingRequests.snapshot().size} pending requests, usernames=$usernames)"
        }
    }

    private suspend fun rejectPendingRequests() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = pendingBatch.removeFirstOrNull() ?: pendingRequests.poll() ?: break
            if (pendingRequests.isCommitted(request)) {
                invokeSaveCallback(request)
                continue
            }
            val response = if (preserveFailure(request)) {
                AccountSaveResponse.InternalShutdownError(request.player)
            } else {
                AccountSaveResponse.RecoveryFailure(request.player)
            }
            safeInvokeCallback(request, response)
        }
    }

    private fun incrementSaveAttempts(request: AccountSaveRequest) {
        if (request.atomicGroup != null) {
            request.attempts++
            pendingRequests.retry(request)
            return
        }
        if (request.attempts++ < REQUEST_RETRIES_BEFORE_FAILURE) {
            pendingRequests.retry(request)
            return
        }
        val response = if (preserveFailure(request)) {
            AccountSaveResponse.ExcessiveRetries(request.player)
        } else {
            AccountSaveResponse.RecoveryFailure(request.player)
        }
        safeInvokeCallback(request, response)
        logger.error { "Reached max retry attempts for player: ${request.player}" }
    }

    private fun preserveFailure(request: AccountSaveRequest): Boolean {
        failedSequence.merge(request.characterId, request.sequence, ::maxOf)
        request.atomicGroup?.let { group ->
            return try {
                journal.persistAtomic(group)
                true
            } catch (failure: Exception) {
                logger.error(failure) { "Could not preserve joint character save ${group.id}" }
                false
            }
        }
        return try {
            val path = recovery.preserve(request)
            val missing = request.recoverySnapshot?.get("unrecoverableSegments") as? Int
            if (missing == 0) {
                logger.error { "Uncommitted save preserved at $path. Recovery requires manual review; it is never automatically replayed." }
                true
            } else {
                logger.error { "Partial save recovery written to $path; $missing pipeline snapshot(s) are missing. Retaining the player for retry." }
                false
            }
        } catch (failure: Exception) {
            logger.error(failure) { "Could not preserve failed save for character ${request.characterId}. The latest state is not durably saved." }
            false
        }
    }

    // A failing callback must not prevent the remaining requests from completing.
    private fun safeInvokeCallback(request: AccountSaveRequest, response: AccountSaveResponse) {
        try {
            request.atomicGroup?.complete(response is AccountSaveResponse.Success)
            request.callback(response)
        } catch (e: Exception) {
            val message = "Error handling save request callback for: $request (response=$response)"
            logger.error(e) { message }
        } finally {
            pendingRequests.complete(request)
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
        logger.info { "Account saving service recovered after $previous failure(s)." }
    }

    private companion object {
        private const val MAX_OUTSTANDING_REQUESTS = 4096
        private const val SERVICE_THREAD_NAME = "account-writer"

        private const val DELAY_PER_BATCH = 100L

        private const val DELAY_WHEN_BACKLOG_MS = 1L

        private const val REQUESTS_PER_BATCH = 25

        private const val REQUEST_RETRIES_BEFORE_FAILURE = 3

        private const val REQUEST_TIMEOUT_MS = 5000L

        private const val SHUTDOWN_REQUEST_TIMEOUT_MS = 2000L

        private const val ERROR_BACKOFF_MS = 10_000L

        private const val MAX_CONSECUTIVE_FAILURES = 10

        /** Caps the pending-save shutdown drain before emergency failure callbacks. */
        private const val SHUTDOWN_TIMEOUT_MS = 30_000L

        /**
         * Caps emergency failure callbacks so backup/recovery handlers cannot stall shutdown
         * indefinitely.
         */
        private const val SHUTDOWN_EMERGENCY_TIMEOUT_MS = 30_000L
    }
}
