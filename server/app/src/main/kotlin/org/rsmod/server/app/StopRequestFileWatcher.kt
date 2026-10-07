package org.rsmod.server.app

import com.github.michaelbull.logging.InlineLogger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class StopRequestFileWatcher private constructor(
    private val file: Path,
    private val requestShutdown: () -> Unit,
) : AutoCloseable {
    private val logger = InlineLogger()
    private val stateLock = Any()
    private val closed = CountDownLatch(1)
    private var worker: Thread? = null

    init {
        logger.debug { "Local cooperative shutdown is configured through $file." }
    }

    fun start() {
        synchronized(stateLock) {
            if (closed.count == 0L) return
            if (worker != null) return
            worker = Thread(::watch, "local-stop-request").apply { isDaemon = true; start() }
        }
    }

    private fun watch() {
        try {
            while (closed.count != 0L) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    synchronized(stateLock) {
                        if (closed.count != 0L) {
                            logger.info { "Received local stop request; beginning normal server shutdown." }
                            requestShutdown()
                        }
                    }
                    return
                }
                if (closed.await(500, TimeUnit.MILLISECONDS)) return
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (cause: Throwable) {
            logger.error(cause) { "Could not read the local server stop request at $file." }
        }
    }

    override fun close() {
        val running = synchronized(stateLock) {
            closed.countDown()
            worker
        }
        if (running != null && running !== Thread.currentThread()) running.join()
    }

    companion object {
        const val PROPERTY = "openrune.stopRequestFile"

        fun create(configuredPath: String?, requestShutdown: () -> Unit): StopRequestFileWatcher? {
            if (configuredPath.isNullOrBlank()) return null
            val file = Path.of(configuredPath)
            require(file.isAbsolute) { "$PROPERTY must name the launcher's absolute per-run stop request file." }
            return StopRequestFileWatcher(file.normalize(), requestShutdown)
        }
    }
}
