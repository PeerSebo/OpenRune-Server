package org.rsmod.server.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.PluginScriptBootGate
import org.rsmod.game.GameProcess
import org.rsmod.server.services.ServiceManager

class GameServiceStartupTest {
    @Test
    fun `readiness waits for game startup validation on the game thread`() {
        val validationEntered = CountDownLatch(1)
        val finishValidation = CountDownLatch(1)
        val firstCycle = CountDownLatch(1)
        val startupCalls = AtomicInteger()
        val startupThread = AtomicReference<Thread>()
        val cycleThread = AtomicReference<Thread>()
        val process = object : GameProcess {
            override fun startup() {
                startupCalls.incrementAndGet()
                startupThread.set(Thread.currentThread())
                validationEntered.countDown()
                check(finishValidation.await(5, TimeUnit.SECONDS))
            }

            override fun cycle() {
                cycleThread.set(Thread.currentThread())
                firstCycle.countDown()
            }
            override fun preShutdown() {}
            override fun shutdown() {}
        }
        val gate = PluginScriptBootGate().apply { markReady() }
        val manager = ServiceManager.create(setOf(GameService(process, gate)))
        val caller = Executors.newSingleThreadExecutor()
        val starting = caller.submit<ServiceManager.StartResult> {
            runBlocking { manager.awaitStartup() }
        }
        try {
            assertTrue(validationEntered.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) {
                starting.get(150, TimeUnit.MILLISECONDS)
            }
            assertEquals(1L, firstCycle.count, "Ticks must not run during startup validation")
            finishValidation.countDown()
            assertEquals(ServiceManager.StartResult.Healthy, starting.get(5, TimeUnit.SECONDS))
            assertTrue(firstCycle.await(5, TimeUnit.SECONDS))
            assertEquals("game", startupThread.get().name.substringBefore(" @coroutine#"))
            assertSame(startupThread.get(), cycleThread.get())
            assertEquals(1, startupCalls.get())
        } finally {
            finishValidation.countDown()
            val result = starting.get(5, TimeUnit.SECONDS)
            if (result == ServiceManager.StartResult.Healthy) {
                manager.shutdown()
                assertEquals(ServiceManager.ShutdownResult.Clean, manager.awaitShutdown())
            }
            caller.shutdownNow()
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `startup validation failure prevents readiness and game ticks`() = runBlocking {
        val failure = IllegalStateException("Invalid farming crop transforms")
        val cycles = AtomicInteger()
        val shutdowns = AtomicInteger()
        val process = object : GameProcess {
            override fun startup() { throw failure }
            override fun cycle() { cycles.incrementAndGet() }
            override fun preShutdown() {}
            override fun shutdown() { shutdowns.incrementAndGet() }
        }
        val gate = PluginScriptBootGate().apply { markReady() }
        val manager = ServiceManager.create(setOf(GameService(process, gate)))
        val result = manager.awaitStartup()

        assertTrue(result is ServiceManager.StartResult.Error.Clean)
        val reported = (result as ServiceManager.StartResult.Error).throwable
        assertEquals(failure.javaClass, reported.javaClass)
        assertEquals(failure.message, reported.message)
        assertTrue(generateSequence(reported) { it.cause }.any { it === failure })
        assertEquals(0, cycles.get())
        assertEquals(1, shutdowns.get())
    }
}
