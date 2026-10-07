package org.rsmod.server.services

import java.util.Queue
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.rsmod.server.services.concurrent.ScheduledListenerService
import org.rsmod.server.services.concurrent.ScheduledService
import org.rsmod.server.services.util.safeShutdown

public class ServiceManager
private constructor(
    private val services: List<Service>,
    private val listeners: List<ListenerService>,
    private val scheduled: List<ScheduledService>,
    private val scheduledListeners: List<ScheduledListenerService>,
) {
    private val activeExecutors = mutableListOf<ExecutorService>()
    private val activeScopes = mutableMapOf<ScheduledService, CoroutineScope>()
    private val scheduledErrors = ConcurrentLinkedQueue<Throwable>()

    private val shutdownLatch = CountDownLatch(1)
    private val shutdownRequest = AtomicBoolean(false)
    private val shutdownInProgress = AtomicBoolean(false)
    private val shutdownComplete = CountDownLatch(1)

    @Volatile private var shutdownResult: ShutdownResult? = null

    public suspend fun awaitStartup(): StartResult {
        val started = ConcurrentLinkedQueue<Service>()

        suspend fun safeShutdownResult(t: Throwable): StartResult.Error {
            val startedCopy = started.toList()
            val errors = ConcurrentLinkedQueue<Throwable>()
            for (phase in Service.ShutdownPhase.entries) {
                supervisorScope {
                    val jobs = startedCopy.filter { it.shutdownPhase == phase }.map { service ->
                        async {
                            try {
                                service.shutdown()
                                started.remove(service)
                            } catch (shutdownEx: Throwable) {
                                errors += shutdownEx
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }
            return if (errors.isEmpty()) {
                StartResult.Error.Clean(t)
            } else {
                StartResult.Error.Lingering(t, errors.first(), started)
            }
        }

        val startupJobs = supervisorScope { services.map { startService(it, started) } }
        try {
            startupJobs.awaitAll()
        } catch (t: Throwable) {
            val shutdownResult = safeShutdownResult(t)
            return shutdownResult
        }

        val listenerSignals = supervisorScope { listeners.map { signalStartup(it) } }
        try {
            listenerSignals.awaitAll()
        } catch (t: Throwable) {
            val shutdownResult = safeShutdownResult(t)
            return shutdownResult
        }

        val tmpExecutors = mutableListOf<ExecutorService>()
        try {
            for (service in scheduled) {
                val executor = service.createExecutor()
                tmpExecutors += executor
                cacheCoroutineScope(service, executor)
            }
            activeExecutors += tmpExecutors
        } catch (t: Throwable) {
            tmpExecutors.forEach(::safeShutdown)
            val shutdownResult = safeShutdownResult(t)
            return shutdownResult
        }

        val scheduledSignals = supervisorScope { scheduledListeners.map { signalStartup(it) } }
        try {
            scheduledSignals.awaitAll()
        } catch (t: Throwable) {
            activeExecutors.forEach(::safeShutdown)
            val shutdownResult = safeShutdownResult(t)
            return shutdownResult
        }

        try {
            for (service in scheduled) {
                scheduleService(service)
            }
        } catch (t: Throwable) {
            activeExecutors.forEach(::safeShutdown)
            val shutdownResult = safeShutdownResult(t)
            return shutdownResult
        }

        return StartResult.Healthy
    }

    /**
     * Phases: signal listeners, stop jobs/executors, then stop workers before shared resources.
     * Concurrent callers await the same cleanup; later calls return AlreadyShutDown or the failure report.
     * @param signalTimeoutSecs Listener preparation budget, including player saves.
     * @param cleanupTimeoutSecs Job/executor stop budget.
     * @param shutdownTimeoutSecs Shared budget for all service shutdown calls.
     */
    public fun awaitShutdown(
        signalTimeoutSecs: Int = 30,
        cleanupTimeoutSecs: Int = 30,
        shutdownTimeoutSecs: Int = 30,
    ): ShutdownResult {
        shutdownLatch.await()

        val canShutdown = shutdownInProgress.compareAndSet(false, true)
        if (!canShutdown) {
            shutdownComplete.await()
            return shutdownResult as? ShutdownResult.Report ?: ShutdownResult.AlreadyShutDown
        }

        try {
            signalShutdown(signalTimeoutSecs * 1000L)
            cleanupThreads(cleanupTimeoutSecs * 1000L)
            shutdownServices(shutdownTimeoutSecs * 1000L)
        } catch (t: Throwable) {
            scheduledErrors += t
        } finally {
            shutdownResult =
                if (scheduledErrors.isNotEmpty()) ShutdownResult.Report(scheduledErrors.toList())
                else ShutdownResult.Clean
            shutdownComplete.countDown()
        }
        return checkNotNull(shutdownResult)
    }

    public fun shutdown() {
        val requestShutdown = shutdownRequest.compareAndSet(false, true)
        if (requestShutdown) {
            shutdownLatch.countDown()
        }
    }

    private fun CoroutineScope.startService(service: Service, started: Queue<Service>) = async {
        started.add(service)
        service.startup()
    }

    private fun CoroutineScope.signalStartup(service: ListenerService) = async {
        service.signalStartup()
    }

    private fun cacheCoroutineScope(service: ScheduledService, executor: ExecutorService) {
        val coroutineScope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        activeScopes[service] = coroutineScope
    }

    private fun signalStartup(service: ScheduledListenerService): Deferred<Unit> {
        val coroutineScope = activeScopes.getValue(service)
        return coroutineScope.async { service.signalStartup() }
    }

    private fun scheduleService(service: ScheduledService) {
        val coroutineScope = activeScopes.getValue(service)
        coroutineScope.launch {
            try {
                service.setup()
                while (isActive && !shutdownRequest.get()) {
                    service.run()
                }
            } catch (_: CleanupException) {
            } catch (t: Throwable) {
                scheduledErrors += t
                shutdown()
            }
        }
    }

    private fun signalShutdown(timeoutMillis: Long) = runBlocking {
        try {
            withTimeout(timeoutMillis) {
                supervisorScope {
                    try {
                        signalListenerShutdown()
                    } catch (t: Throwable) {
                        scheduledErrors += t
                    }
                    try {
                        signalScheduledShutdown()
                    } catch (t: Throwable) {
                        scheduledErrors += t
                    }
                }
            }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
        }
    }

    /** Allows the process owner to arm its hard-stop watchdog before beginning service cleanup. */
    public fun awaitShutdownRequest() {
        shutdownLatch.await()
    }

    private suspend fun CoroutineScope.signalListenerShutdown() {
        val signalJobs = listeners.map { service -> async { service.signalShutdown() } }
        signalJobs.awaitAll()
    }

    private suspend fun signalScheduledShutdown() {
        val signalJobs =
            scheduledListeners.map { service ->
                val serviceScope = activeScopes.getValue(service)
                serviceScope.async { service.signalShutdown() }
            }
        signalJobs.awaitAll()
    }

    private fun cleanupThreads(timeoutMillis: Long) = runBlocking {
        val activeScopes = activeScopes.values
        val activeJobs = activeScopes.mapNotNull { it.coroutineContext[Job] }.filter(Job::isActive)
        activeJobs.forEach(::safeCancel)
        activeExecutors.forEach(ExecutorService::shutdown)
        try {
            withTimeout(timeoutMillis) { activeJobs.joinAll() }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
            activeExecutors.forEach(ExecutorService::shutdownNow)
            // Blocking JDBC can outlive coroutine cancellation and interruption. Do not drain the
            // same queue or close its database until that worker really stops. GameBootstrap's
            // outer watchdog is the hard-stop boundary if it cannot terminate.
            activeJobs.joinAll()
        }
    }

    private fun shutdownServices(timeoutMillis: Long) = runBlocking {
        try {
            withTimeout(timeoutMillis) {
                supervisorScope {
                    for (phase in Service.ShutdownPhase.entries) {
                        val shutdownJobs = services.filter { it.shutdownPhase == phase }.map { service ->
                            async {
                                try {
                                    service.shutdown()
                                } catch (t: Throwable) {
                                    scheduledErrors += t
                                }
                            }
                        }
                        shutdownJobs.awaitAll()
                    }
                }
            }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
        }
    }

    private fun safeCancel(job: Job) {
        job.cancel(CleanupException())
    }

    private fun safeShutdown(executor: ExecutorService, timeoutSecs: Long = 15L) {
        executor.safeShutdown(timeoutSecs)
    }

    public sealed class StartResult {
        public data object Healthy : StartResult()

        public sealed class Error : StartResult() {
            public abstract val throwable: Throwable

            public data class Clean(override val throwable: Throwable) : Error()

            public data class Lingering(
                override val throwable: Throwable,
                val shutdownEx: Throwable,
                val pending: Collection<Service>,
            ) : Error()
        }
    }

    public sealed class ShutdownResult {
        public data object Clean : ShutdownResult()

        public data object AlreadyShutDown : ShutdownResult()

        public data class Report(val errors: Collection<Throwable>) : ShutdownResult()
    }

    private class CleanupException : CancellationException("Coroutine cancelled by ServiceManager")

    public companion object {
        public fun create(services: Set<Service>): ServiceManager {
            val listeners = services.filterIsInstance<ListenerService>()
            val scheduled = services.filterIsInstance<ScheduledService>()
            val scheduledListeners = services.filterIsInstance<ScheduledListenerService>()
            return ServiceManager(services.toList(), listeners, scheduled, scheduledListeners)
        }
    }
}
