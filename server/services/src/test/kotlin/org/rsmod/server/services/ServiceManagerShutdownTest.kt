package org.rsmod.server.services

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.server.services.concurrent.ScheduledService

class ServiceManagerShutdownTest {
    @Test
    fun `interrupt resistant worker is never drained or disconnected while still running`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val drained = AtomicBoolean()
        val closed = AtomicBoolean()
        val worker = object : ScheduledService {
            override suspend fun startup() {}
            override suspend fun setup() {}
            override fun createExecutor() = Executors.newSingleThreadExecutor()
            override suspend fun run() {
                entered.countDown()
                while (release.count > 0) {
                    try { release.await() } catch (_: InterruptedException) { /* Blocking driver. */ }
                }
            }
            override suspend fun shutdown() { drained.set(true) }
        }
        val database = service(Service.ShutdownPhase.RESOURCES) { closed.set(true) }
        val manager = started(database, worker)
        val executor = Executors.newSingleThreadExecutor()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            manager.shutdown()
            val stopping = executor.submit<ServiceManager.ShutdownResult> {
                manager.awaitShutdown(cleanupTimeoutSecs = 1)
            }
            assertThrows(TimeoutException::class.java) { stopping.get(1500, TimeUnit.MILLISECONDS) }
            assertFalse(drained.get())
            assertFalse(closed.get())
            release.countDown()
            assertTrue(stopping.get(5, TimeUnit.SECONDS) is ServiceManager.ShutdownResult.Report)
            assertTrue(drained.get())
            assertTrue(closed.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent awaiter cannot return until the worker finishes saving`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stops = AtomicInteger()
        val worker = service {
            stops.incrementAndGet()
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        val manager = started(worker)
        val executor = Executors.newFixedThreadPool(2)
        try {
            manager.shutdown()
            val first = executor.submit<ServiceManager.ShutdownResult> { manager.awaitShutdown() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<ServiceManager.ShutdownResult> { manager.awaitShutdown() }
            assertThrows(TimeoutException::class.java) { second.get(150, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertEquals(ServiceManager.ShutdownResult.Clean, first.get(5, TimeUnit.SECONDS))
            assertEquals(ServiceManager.ShutdownResult.AlreadyShutDown, second.get(5, TimeUnit.SECONDS))
            assertEquals(1, stops.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `shared database closes after every worker including suspended saves`() {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val database = service(Service.ShutdownPhase.RESOURCES) { calls += "database" }
        val saver = service { delay(30); calls += "save" }
        val logout = service { delay(10); calls += "logout" }
        val manager = started(database, saver, logout)
        manager.shutdown()
        assertEquals(ServiceManager.ShutdownResult.Clean, manager.awaitShutdown())
        assertEquals(setOf("save", "logout"), calls.take(2).toSet())
        assertEquals("database", calls.last())
    }

    @Test
    fun `failed worker cannot skip other workers resource close or later error reporting`() {
        val failure = IllegalStateException("save failed")
        val calls = mutableListOf<String>()
        val database = service(Service.ShutdownPhase.RESOURCES) { calls += "database" }
        val failing = service { throw failure }
        val other = service { delay(10); calls += "other" }
        val manager = started(database, failing, other)
        manager.shutdown()
        val first = manager.awaitShutdown() as ServiceManager.ShutdownResult.Report
        assertTrue(first.errors.contains(failure))
        assertEquals(listOf("other", "database"), calls)
        val later = manager.awaitShutdown() as ServiceManager.ShutdownResult.Report
        assertEquals(first.errors, later.errors)
    }

    @Test
    fun `startup failure also drains workers before closing shared resources`() = runBlocking {
        val calls = mutableListOf<String>()
        val database = service(Service.ShutdownPhase.RESOURCES) { calls += "database" }
        val failed = object : Service {
            override suspend fun startup() { throw IllegalStateException("startup failed") }
            override suspend fun shutdown() { delay(10); calls += "worker" }
        }
        val manager = ServiceManager.create(linkedSetOf(database, failed))
        assertTrue(manager.awaitStartup() is ServiceManager.StartResult.Error.Clean)
        assertEquals(listOf("worker", "database"), calls)
    }

    private fun started(vararg services: Service): ServiceManager =
        ServiceManager.create(services.toSet()).also { manager ->
            assertEquals(ServiceManager.StartResult.Healthy, runBlocking { manager.awaitStartup() })
        }

    private fun service(
        phase: Service.ShutdownPhase = Service.ShutdownPhase.WORKERS,
        stop: suspend () -> Unit,
    ): Service = object : Service {
        override val shutdownPhase = phase
        override suspend fun startup() {}
        override suspend fun shutdown() = stop()
    }
}
