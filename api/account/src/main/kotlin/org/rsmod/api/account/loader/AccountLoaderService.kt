package org.rsmod.api.account.loader

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.loader.request.AccountLoadRequest
import org.rsmod.api.account.loader.request.AccountLoadResponse
import org.rsmod.api.account.saver.AccountSaveRecovery
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.journal.AccountSaveLease
import org.rsmod.api.account.saver.journal.SaveRevisionStore
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.server.services.concurrent.ScheduledService

public class AccountLoaderService
@Inject
constructor(
    private val database: GameDatabase,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
    private val serverConfig: org.rsmod.api.server.config.ServerConfig,
    private val journal: AccountSaveJournal,
    private val recovery: AccountSaveRecovery,
) : ScheduledService {
    private val logger = InlineLogger()

    private val shuttingDown = AtomicBoolean(false)

    private val pendingRequests = ArrayBlockingQueue<AccountLoadRequest>(MAX_PENDING_REQUESTS)

    private val pendingBatch = ArrayList<AccountLoadRequest>(REQUESTS_PER_BATCH)
    private var consecutiveFailureCount = AtomicInteger(0)

    public fun queue(request: AccountLoadRequest): Boolean = synchronized(pendingRequests) {
        !shuttingDown.get() && pendingRequests.offer(request)
    }

    public fun isShuttingDown(): Boolean = shuttingDown.get()

    /** Call before queueing so the failure-backoff window can expire and requests resume. */
    public fun isTemporarilyRejectingRequests(): Boolean =
        consecutiveFailureCount.get() >= MAX_CONSECUTIVE_FAILURES && pendingRequests.isNotEmpty()

    override suspend fun run() {
        val failureBackoffEnabled = consecutiveFailureCount.get() >= MAX_CONSECUTIVE_FAILURES
        val batchPollCount = if (failureBackoffEnabled) 1 else REQUESTS_PER_BATCH

        batchPendingRequests(batchPollCount)
        handlePendingBatch()

        if (!failureBackoffEnabled) {
            delay(DELAY_PER_BATCH)
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
        if (pendingBatch.isNotEmpty()) {
            try {
                handleRequests(pendingBatch)
                resetConsecutiveFailures()
                pendingBatch.clear()
            } catch (e: Exception) {
                val size = pendingBatch.size
                incrementConsecutiveFailures()
                rejectPendingBatch()
                logger.error(e) { "Error processing account batch. (size=$size)" }
            }
        }
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, SERVICE_THREAD_NAME).apply { isDaemon = true }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    override suspend fun setup() {
        database.withTransaction { SaveRevisionStore.install(it) }
        logger.debug {
            "Account loader service accepting $REQUESTS_PER_BATCH requests / $DELAY_PER_BATCH ms."
        }
    }

    override suspend fun startup() {}

    override suspend fun shutdown() {
        logger.info { "Attempting to shutdown account loader service." }
        try {
            synchronized(pendingRequests) { shuttingDown.set(true) }
            rejectPendingRequests()
            pendingBatch.clear()
            logger.info { "Account loader service successfully shut down." }
        } catch (t: Throwable) {
            logger.error(t) { "Account loader service failed to shut down." }
        }
    }

    private fun invokeCallback(request: AccountLoadRequest, response: AccountLoadResponse) {
        try {
            request.callback(response)
        } catch (failure: Exception) {
            logger.error(failure) { "Account load callback failed for '${request.accountName}'." }
        }
    }

    private fun rejectPendingRequests() {
        while (true) {
            val request = pendingRequests.poll() ?: break
            invokeCallback(request, AccountLoadResponse.Err.ShutdownInProgress)
        }
    }

    private suspend fun handleRequests(requests: List<AccountLoadRequest>) = supervisorScope {
        val readOnly = requests.filterIsInstance<AccountLoadRequest.ReadOnly>()
        val writeRequired = requests.filterIsInstance<AccountLoadRequest.WriteRequired>()

        if (readOnly.isNotEmpty()) {
            val timeout = REQUEST_READ_TIMEOUT_MS
            val readOnlyJobs =
                readOnly.map { request -> async { handleRequestWithTimeout(request, timeout) } }
            readOnlyJobs.awaitAll()
        }

        for (request in writeRequired) {
            handleRequestWithTimeout(request, REQUEST_WRITE_TIMEOUT_MS)
        }
    }

    private suspend fun handleRequestWithTimeout(request: AccountLoadRequest, timeoutMillis: Long) {
        val result = try {
            withTimeoutOrNull(timeoutMillis) { handleRequest(request) }
        } catch (failure: RecoveryBlocked) {
            logger.error(failure) { "Save recovery blocked login for '${request.accountName}'." }
            invokeCallback(request, AccountLoadResponse.Err.Exception(failure))
            return
        }
        if (result == null) {
            logger.warn { "Account load timed out for: '${request.accountName}' ($request)" }
            invokeCallback(request, AccountLoadResponse.Err.Timeout)
        }
    }

    private suspend fun handleRequest(request: AccountLoadRequest) {
        val startedAt = System.nanoTime()
        val response = database.withTransaction { connection -> connection.handleRequest(request) }
        val elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (serverConfig.loginTimingLogs && elapsedMs >= ACCOUNT_DB_TIMING_INFO_MS) {
            val level =
                if (elapsedMs >= ACCOUNT_DB_TIMING_WARN_MS) {
                    "warn"
                } else {
                    "info"
                }
            val message = "Account load DB user='${request.accountName}' elapsed=${elapsedMs}ms"
            if (level == "warn") {
                logger.warn { message }
            } else {
                logger.info { message }
            }
        }
        invokeCallback(request, response)
    }

    private fun DatabaseConnection.handleRequest(request: AccountLoadRequest): AccountLoadResponse {
        val metadataList = repository.selectAndCreateMetadataList(this, request.accountName)
        if (metadataList == null) {
            val response = accountNotFoundResponse(request)
            return response
        }
        val revision = try {
            journal.recover(this, metadataList.characterId, metadataList.accountData.accountId, repository, pipelines)
                .also { recovery.requireSafeLogin(metadataList.characterId, it) }
        } catch (failure: Exception) {
            throw RecoveryBlocked(metadataList.characterId, failure)
        }
        val recovered = checkNotNull(repository.selectAndCreateMetadataList(this, request.accountName))
        for (pipeline in pipelines) pipeline.append(this, recovered)
        recovered.add(AccountSaveLease, AccountSaveLease(revision))
        val response =
            AccountLoadResponse.Ok.LoadAccount(request.auth, recovered.accountData, recovered)
        return response
    }

    private fun DatabaseConnection.accountNotFoundResponse(request: AccountLoadRequest) =
        when (request) {
            is AccountLoadRequest.StrictSearch -> AccountLoadResponse.Err.AccountNotFound
            is AccountLoadRequest.SearchOrCreateWithPassword -> createAccountResponse(request)
        }

    private fun DatabaseConnection.createAccountResponse(
        request: AccountLoadRequest.SearchOrCreateWithPassword
    ): AccountLoadResponse =
        try {
            val metadataList = createMetadataList(request.accountName, request.hashedPassword())
            AccountLoadResponse.Ok.NewAccount(request.auth, metadataList.accountData, metadataList)
        } catch (e: Exception) {
            AccountLoadResponse.Err.Exception(e)
        }

    private fun DatabaseConnection.createMetadataList(
        accountName: String,
        hashedPassword: String,
    ): CharacterMetadataList {
        val accountId = repository.insertOrSelectAccountId(this, accountName, hashedPassword)
        if (accountId == null) {
            throw IllegalStateException("Could not insert or select account id for: '$accountName'")
        }

        val characterId = repository.insertAndSelectCharacterId(this, accountId)
        if (characterId == null) {
            throw IllegalStateException("Could not insert character for: '$accountName' ($accountId)")
        }

        val metadataList = repository.selectAndCreateMetadataList(this, accountName)
        if (metadataList == null) {
            throw IllegalStateException("Could not select character after creation: '$accountName'")
        }
        val revision = SaveRevisionStore.lock(this, characterId)
        metadataList.add(AccountSaveLease, AccountSaveLease(revision))
        return metadataList
    }

    private fun rejectPendingBatch() {
        for (request in pendingBatch) {
            invokeCallback(request, AccountLoadResponse.Err.InternalServiceError)
        }
        pendingBatch.clear()
    }

    private fun incrementConsecutiveFailures() {
        consecutiveFailureCount.updateAndGet { curr -> min(curr + 1, MAX_CONSECUTIVE_FAILURES) }
    }

    private fun resetConsecutiveFailures() {
        val previous = consecutiveFailureCount.getAndSet(0)
        if (previous == 0) {
            return
        }
        logger.info { "Account loader service recovered after $previous failure(s)." }
    }

    private class RecoveryBlocked(characterId: Int, cause: Exception) :
        IllegalStateException("Review save journal for character $characterId before login", cause)

    private companion object {
        private const val SERVICE_THREAD_NAME = "account-reader"

        private const val MAX_PENDING_REQUESTS = 2000

        private const val DELAY_PER_BATCH = 250L

        private const val REQUESTS_PER_BATCH = 50

        private const val REQUEST_READ_TIMEOUT_MS = 1000L

        private const val REQUEST_WRITE_TIMEOUT_MS = 5000L

        private const val ERROR_BACKOFF_MS = 10_000L

        private const val MAX_CONSECUTIVE_FAILURES = 5

        private const val ACCOUNT_DB_TIMING_INFO_MS = 100L

        private const val ACCOUNT_DB_TIMING_WARN_MS = 500L
    }
}
