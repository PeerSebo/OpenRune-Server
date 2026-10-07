package org.rsmod.api.db.jdbc

import com.github.michaelbull.logging.InlineLogger
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class EmbeddedPostgresOwner {
    private val logger = InlineLogger()
    private val startLock = Any()

    @Volatile private var current: Ownership? = null
    private var failedLaunch: Pair<Lease, EmbeddedPostgres?>? = null

    fun start(requestedDirectory: Path) {
        synchronized(startLock) {
            check(failedLaunch == null) {
                "A previous PostgreSQL launch could not be verified; its lease is retained until this game process exits."
            }
            current?.let {
                check(!it.stopping.get() && it.identity.matches(it.directory)) {
                    "Embedded PostgreSQL is stopping or its ownership changed; restart this game process."
                }
                return
            }
            Files.createDirectories(requestedDirectory)
            val directory = requestedDirectory.toRealPath()
            val lease = Lease.acquire(directory)
            logger.debug { "Claimed PostgreSQL ownership lease at $directory (game PID ${ProcessHandle.current().pid()})." }
            var launchAttempted = false
            var launched: EmbeddedPostgres? = null
            try {
                preparePidFile(directory)
                val launchStartedAt = Instant.now()
                launchAttempted = true
                val started = EmbeddedPostgres.builder()
                    .setDataDirectory(directory.toFile())
                    .setCleanDataDirectory(false)
                    .setRegisterShutdownHook(false)
                    // Zonky defaults are intended for disposable tests. This PGDATA stores player
                    // accounts, so committed saves must survive an unexpected process termination.
                    .setServerConfig("fsync", "on")
                    .setServerConfig("synchronous_commit", "on")
                    .setPGStartupWait(Duration.ofSeconds(30))
                    .start()
                launched = started
                val metadata = checkNotNull(Postmaster.read(directory)) {
                    "Embedded PostgreSQL started without verifiable PID metadata; refusing to claim or stop it."
                }
                val process = ProcessHandle.of(metadata.pid).orElseThrow {
                    IllegalStateException("Embedded PostgreSQL exited before ownership could be verified.")
                }
                val startedAt = process.info().startInstant().orElseThrow {
                    IllegalStateException("Cannot verify the embedded PostgreSQL process start time.")
                }
                check(metadata.directory == directory && metadata.port == started.port && process.isAlive) {
                    "PostgreSQL ownership could not be verified."
                }
                check(!startedAt.isBefore(launchStartedAt.minusSeconds(5))) {
                    "PostgreSQL PID metadata refers to a process that predates this launch."
                }
                check(kotlin.math.abs(startedAt.epochSecond - metadata.startedSeconds) <= 5) {
                    "PostgreSQL PID metadata refers to another process incarnation."
                }
                current = Ownership(directory, lease, started, Identity(metadata, process, startedAt))
                logger.debug { "Own PostgreSQL PID ${metadata.pid}, started $startedAt, at $directory." }
            } catch (cause: Throwable) {
                // A failed contender has no stop authority. Once native startup began, however,
                // it might have launched a process even if start() threw before returning a handle.
                // Keep that uncertain lease quarantined until JVM exit; never use path-based cleanup.
                if (launchAttempted) {
                    failedLaunch = lease to launched
                    logger.error(cause) { "PostgreSQL launch could not be verified at $directory; the lease remains held until this game process exits." }
                } else {
                    runCatching { lease.close() }.onFailure(cause::addSuppressed)
                }
                throw cause
            }
        }
    }

    fun jdbcTriple(): Triple<String, String, String>? = current?.let {
        Triple(it.postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")
    }

    fun stop(timeoutMs: Long) {
        require(timeoutMs > 0)
        val ownership = synchronized(startLock) {
            val owned = current ?: return
            if (!owned.stopping.get()) {
                check(owned.identity.matches(owned.directory)) {
                    "Embedded PostgreSQL identity changed at ${owned.directory}; refusing to stop another process. " +
                        "The ownership lease is retained until this game process exits."
                }
                owned.stopping.set(true)
                Thread({ closeOwned(owned) }, "embedded-pg-stop").apply { isDaemon = true; start() }
            }
            owned
        }
        if (!ownership.done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            forceOwned(ownership)
            check(ownership.done.await(10, TimeUnit.SECONDS)) {
                "Embedded PostgreSQL shutdown has not finished; its ownership lease remains held."
            }
        }
        ownership.failure?.let { throw IllegalStateException("Embedded PostgreSQL could not stop safely", it) }
    }

    fun forceStopNow() {
        current?.let(::forceOwned)
    }

    private fun closeOwned(ownership: Ownership) {
        try {
            check(current === ownership && ownership.identity.matches(ownership.directory)) {
                "PostgreSQL identity changed before close; refusing directory-based pg_ctl shutdown."
            }
            ownership.postgres.close()
            if (ownership.identity.process.isAlive) {
                forceOwned(ownership)
                ownership.identity.process.onExit().get(10, TimeUnit.SECONDS)
            }
            check(!ownership.identity.process.isAlive) { "Owned PostgreSQL is still running." }
            synchronized(startLock) {
                check(current === ownership)
                ownership.lease.close()
                current = null
            }
            logger.info { "Stopped owned PostgreSQL PID ${ownership.identity.metadata.pid} and released its lease at ${ownership.directory}." }
        } catch (cause: Throwable) {
            ownership.failure = cause
            // Do not release the lease after an unverified/unfinished close. Operator intervention
            // or this JVM exiting must resolve the uncertain ownership first.
        } finally {
            ownership.done.countDown()
        }
    }

    private fun forceOwned(ownership: Ownership) {
        runCatching {
            if (current !== ownership || !ownership.identity.matches(ownership.directory)) return
            val process = ownership.identity.process
            val descendants = process.descendants().use { it.toList() }
            for (child in descendants) {
                if (child.isAlive) child.destroyForcibly()
            }
            if (process.isAlive) process.destroyForcibly()
        }.onFailure { logger.error(it) { "Could not stop the owned PostgreSQL process safely." } }
    }

    private fun preparePidFile(directory: Path) {
        val metadata = Postmaster.read(directory) ?: return
        check(metadata.directory == directory) {
            "PostgreSQL PID metadata names another data directory; no files or processes were changed."
        }
        val alive = ProcessHandle.of(metadata.pid).map { it.isAlive }.orElse(false)
        if (alive) {
            val message = "PostgreSQL PID ${metadata.pid} is already running at $directory. " +
                "Stop the previous local server cleanly before restarting; OpenRune will not borrow or kill it."
            logger.error { message }
            error(message)
        }
        // A stale PID is recoverable only after acquiring the exclusive lease and proving that its
        // complete metadata and dead-process status are still unchanged immediately before deletion.
        val latest = checkNotNull(Postmaster.read(directory)) { "PostgreSQL PID metadata changed during startup." }
        check(latest.bytes.contentEquals(metadata.bytes) &&
            !ProcessHandle.of(metadata.pid).map { it.isAlive }.orElse(false)) {
            "PostgreSQL PID metadata changed during startup; refusing automatic cleanup."
        }
        Files.delete(directory.resolve("postmaster.pid"))
        logger.info { "Removed verified stale PostgreSQL PID ${metadata.pid} metadata at $directory; existing database files are preserved." }
    }

    private class Ownership(
        val directory: Path,
        val lease: Lease,
        val postgres: EmbeddedPostgres,
        val identity: Identity,
        val stopping: AtomicBoolean = AtomicBoolean(false),
        val done: CountDownLatch = CountDownLatch(1),
        @Volatile var failure: Throwable? = null,
    )

    private class Identity(val metadata: Postmaster, val process: ProcessHandle, val startedAt: Instant) {
        fun matches(directory: Path): Boolean = runCatching {
            val current = Postmaster.read(directory) ?: return false
            current.pid == metadata.pid && current.startedSeconds == metadata.startedSeconds &&
                current.port == metadata.port && current.directory == directory && process.isAlive &&
                process.info().startInstant().orElse(null) == startedAt
        }.getOrDefault(false)
    }

    private class Postmaster(
        val pid: Long,
        val directory: Path,
        val startedSeconds: Long,
        val port: Int,
        val bytes: ByteArray,
    ) {
        companion object {
            fun read(directory: Path): Postmaster? {
                val file = directory.resolve("postmaster.pid")
                if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
                check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 8192) {
                    "Unrecognized PostgreSQL PID metadata at $file; refusing automatic cleanup."
                }
                val bytes = Files.readAllBytes(file)
                val lines = bytes.toString(Charsets.UTF_8).lines()
                val pid = lines.getOrNull(0)?.trim()?.toLongOrNull()
                val started = lines.getOrNull(2)?.trim()?.toLongOrNull()
                val port = lines.getOrNull(3)?.trim()?.toIntOrNull()
                check(pid != null && pid > 0 && started != null && started > 0 && port != null && port in 1..65535) {
                    "Malformed PostgreSQL PID metadata at $file; refusing automatic cleanup."
                }
                val dataDirectory = Path.of(checkNotNull(lines.getOrNull(1)).trim()).toRealPath()
                return Postmaster(pid, dataDirectory, started, port, bytes)
            }
        }
    }

    private class Lease private constructor(private val channel: FileChannel, private val lock: FileLock) {
        fun close() {
            try { lock.release() } finally { channel.close() }
        }

        companion object {
            fun acquire(directory: Path): Lease {
                val file = directory.resolveSibling("${directory.fileName}.openrune-owner.lock")
                val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
                try {
                    val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    checkNotNull(lock) {
                        "Another OpenRune process owns PostgreSQL at $directory. Stop that server before starting another."
                    }
                    val owner = "pid=${ProcessHandle.current().pid()}\nstarted=${Instant.now()}\ndata=$directory\n"
                    channel.truncate(0)
                    channel.write(ByteBuffer.wrap(owner.toByteArray(Charsets.UTF_8)))
                    channel.force(true)
                    return Lease(channel, lock)
                } catch (cause: Throwable) {
                    channel.close()
                    throw cause
                }
            }
        }
    }
}
