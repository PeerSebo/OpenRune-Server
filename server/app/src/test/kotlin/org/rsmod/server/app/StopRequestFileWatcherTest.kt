package org.rsmod.server.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.server.services.Service
import org.rsmod.server.services.ServiceManager

class StopRequestFileWatcherTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `only the exact configured file requests shutdown and it requests once`() {
        val requested = CountDownLatch(1)
        val count = AtomicInteger()
        val file = directory.resolve("this-run.stop-request")
        val watcher = checkNotNull(StopRequestFileWatcher.create(file.toString()) {
            count.incrementAndGet()
            requested.countDown()
        })
        watcher.use {
            it.start()
            it.start()
            Files.writeString(directory.resolve("another-run.stop-request"), "stop")
            assertFalse(requested.await(750, TimeUnit.MILLISECONDS))
            Files.writeString(file, "stop")
            assertTrue(requested.await(5, TimeUnit.SECONDS))
        }
        assertEquals(1, count.get())
        assertTrue(Files.isRegularFile(file), "The per-run request remains available for launcher diagnostics")
    }

    @Test
    fun `closing the watcher terminates polling and prevents a later request`() {
        val requested = CountDownLatch(1)
        val file = directory.resolve("closed-run.stop-request")
        val watcher = checkNotNull(StopRequestFileWatcher.create(file.toString(), requested::countDown))
        watcher.start()
        watcher.close()
        watcher.close()
        watcher.start()
        Files.writeString(file, "stop")
        assertFalse(requested.await(750, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a request made during startup is retained until watching begins`() {
        val requested = CountDownLatch(1)
        val file = directory.resolve("startup-run.stop-request")
        Files.writeString(file, "stop")
        checkNotNull(StopRequestFileWatcher.create(file.toString(), requested::countDown)).use {
            assertEquals(1L, requested.count)
            it.start()
            assertTrue(requested.await(5, TimeUnit.SECONDS))
        }
        assertNull(StopRequestFileWatcher.create(null) { fail("No property must create no watcher") })
        assertNull(StopRequestFileWatcher.create(" ") { fail("An empty property must create no watcher") })
        assertThrows(IllegalArgumentException::class.java) {
            StopRequestFileWatcher.create("relative.stop-request") { fail("Relative request paths are unsupported") }
        }
    }

    @Test
    fun `local stop goes through normal service saving and resource shutdown`() {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val saver = object : Service {
            override suspend fun startup() {}
            override suspend fun shutdown() { calls += "saved" }
        }
        val database = object : Service {
            override val shutdownPhase = Service.ShutdownPhase.RESOURCES
            override suspend fun startup() {}
            override suspend fun shutdown() { calls += "database-closed" }
        }
        val manager = ServiceManager.create(setOf(database, saver))
        assertFalse(runBlocking { manager.awaitStartup() } is ServiceManager.StartResult.Error)
        val waiting = Executors.newSingleThreadExecutor()
        val result = waiting.submit<ServiceManager.ShutdownResult> { manager.awaitShutdown() }
        val file = directory.resolve("service-run.stop-request")
        val watcher = checkNotNull(StopRequestFileWatcher.create(file.toString(), manager::shutdown))
        try {
            watcher.start()
            Files.writeString(file, "stop")
            assertEquals(ServiceManager.ShutdownResult.Clean, result.get(5, TimeUnit.SECONDS))
            assertEquals(listOf("saved", "database-closed"), calls)
        } finally {
            watcher.close()
            manager.shutdown()
            result.get(5, TimeUnit.SECONDS)
            waiting.shutdownNow()
            assertTrue(waiting.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
