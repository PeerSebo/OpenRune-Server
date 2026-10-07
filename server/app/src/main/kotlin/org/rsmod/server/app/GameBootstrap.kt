package org.rsmod.server.app

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import org.rsmod.api.db.jdbc.EmbeddedSameInstancePostgres
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.central.embed.CentralEmbeddedLifecycle
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.Service
import org.rsmod.server.services.ServiceManager

class GameBootstrap @Inject constructor(
    services: Set<Service>,
    private val serverConfig: ServerConfig,
    private val centralEmbedded: CentralEmbeddedLifecycle,
    private val openRuneCentral: OpenRuneCentralWorldLink,
) {
    private val serviceManager = ServiceManager.create(services)
    private val stopRequestWatcher = StopRequestFileWatcher.create(
        System.getProperty(StopRequestFileWatcher.PROPERTY), serviceManager::shutdown,
    )
    private val logger = InlineLogger()
    private val embeddedShutdownLock = Any()
    private var embeddedStopped = false
    private val startupComplete = CountDownLatch(1)

    @Volatile private var startupReady = false
    private val watchdogStarted = AtomicBoolean(false)
    private val shutdownCompleted = AtomicBoolean(false)

    suspend fun startupUntilReady(): Thread {
        // Install our single lifecycle owner before PostgreSQL starts, including Ctrl+C during
        // startup. The hook waits for startup's own cleanup or successful initialization.
        val shutdownHook = Thread(::shutdown, "ShutdownHook")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        // Central must remain available for game logouts. Ktor's independent JVM hook otherwise
        // stops its listener and connection pool concurrently with the game shutdown hook.
        System.setProperty("io.ktor.server.engine.ShutdownHook", "false")
        try {
            EmbeddedSameInstancePostgres.ensureStarted(serverConfig)
            centralEmbedded.startIfConfigured()
            openRuneCentral.startInboundWatch()
            val startupResult = serviceManager.awaitStartup()
            if (startupResult is ServiceManager.StartResult.Error) {
                throw startupResult.throwable
            }
            startupReady = true
            return shutdownHook
        } catch (t: Throwable) {
            try {
                stopEmbeddedServices()
            } catch (cleanupFailure: Throwable) {
                t.addSuppressed(cleanupFailure)
            }
            throw t
        } finally {
            startupComplete.countDown()
        }
    }

    fun awaitShutdown(shutdownHook: Thread) {
        // The main entry point calls this only after plugin loading and the ready message. A stop
        // requested during startup remains on disk and is handled after initialization completes.
        stopRequestWatcher?.start()
        serviceManager.awaitShutdownRequest()
        startShutdownWatchdog()
        try {
            serviceManager.awaitShutdownOrThrow()
        } finally {
            stopEmbeddedServices()
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (_: IllegalStateException) {
            }
        }
    }

    suspend fun startup() {
        val hook = startupUntilReady()
        awaitShutdown(hook)
    }

    private fun shutdown() {
        startShutdownWatchdog()
        try {
            startupComplete.await()
            if (startupReady) {
                serviceManager.shutdown()
                serviceManager.awaitShutdownOrThrow(
                    signalTimeoutSecs = SIGNAL_TIMEOUT_SECS,
                    cleanupTimeoutSecs = CLEANUP_TIMEOUT_SECS,
                    shutdownTimeoutSecs = SHUTDOWN_TIMEOUT_SECS,
                )
            }
        } finally {
            stopEmbeddedServices()
        }
    }

    private fun startShutdownWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) return
        Thread(
                {
                    try {
                        Thread.sleep(FORCE_EXIT_AFTER_MS)
                        if (!shutdownCompleted.get()) {
                            EmbeddedSameInstancePostgres.forceStopNow()
                            Runtime.getRuntime().halt(1)
                        }
                    } catch (_: InterruptedException) {
                    }
                },
                "shutdown-watchdog",
            )
            .apply {
                isDaemon = true
                start()
            }
    }

    private fun stopEmbeddedServices() {
        stopRequestWatcher?.close()
        // The main waiter and JVM hook may finish together. Both must wait for Central's listener
        // and pool to close before the owned PostgreSQL process is stopped.
        synchronized(embeddedShutdownLock) {
            if (embeddedStopped) return
            var failure: Throwable? = null
            try {
                centralEmbedded.stopIfRunning()
            } catch (cause: Throwable) {
                logger.error(cause) { "Embedded Central failed to stop cleanly." }
                failure = cause
            } finally {
                try {
                    EmbeddedSameInstancePostgres.stop()
                } catch (cause: Throwable) {
                    failure?.addSuppressed(cause) ?: run { failure = cause }
                }
                embeddedStopped = true
                shutdownCompleted.set(true)
            }
            failure?.let { throw it }
        }
    }

    private fun ServiceManager.awaitShutdownOrThrow(
        signalTimeoutSecs: Int = SIGNAL_TIMEOUT_SECS,
        cleanupTimeoutSecs: Int = CLEANUP_TIMEOUT_SECS,
        shutdownTimeoutSecs: Int = SHUTDOWN_TIMEOUT_SECS,
    ) {
        val result =
            awaitShutdown(
                signalTimeoutSecs = signalTimeoutSecs,
                cleanupTimeoutSecs = cleanupTimeoutSecs,
                shutdownTimeoutSecs = shutdownTimeoutSecs,
            )
        if (result is ServiceManager.ShutdownResult.Report && result.errors.isNotEmpty()) {
            throw result.errors.first()
        }
    }

    private companion object {
        // The DB synchronizer allows 120 seconds for pending work; account saves allow a 30-second
        // drain plus a 30-second emergency callback phase. The watchdog must outlive both.
        private const val SIGNAL_TIMEOUT_SECS = 125
        private const val CLEANUP_TIMEOUT_SECS = 30
        private const val SHUTDOWN_TIMEOUT_SECS = 65
        private const val FORCE_EXIT_AFTER_MS = 240_000L
    }
}
