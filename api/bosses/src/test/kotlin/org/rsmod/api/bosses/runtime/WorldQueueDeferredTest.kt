package org.rsmod.api.bosses.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

class WorldQueueDeferredTest {
    @Test
    fun `deferred work alone keeps the queue nonempty and starts on the next pass`() {
        val f = Fixture()
        f.queues.addDeferred(2) { f.record() }
        assertEquals(1, f.queues.size)
        assertFalse(f.queues.isEmpty)
        assertTrue(f.queues.isNotEmpty)

        f.advance()
        assertTrue(f.events.isEmpty())
        assertEquals(1, f.queues.size)
        f.advance()

        assertEquals(listOf(2), f.events)
        assertEquals(0, f.queues.size)
        assertTrue(f.queues.isEmpty)
    }

    @Test
    fun `work deferred by a running queue waits a full tick with or without trailing work`() {
        for (trailing in listOf(false, true)) {
            val f = Fixture()
            f.queues.add(1) { f.queues.addDeferred(1) { f.record() } }
            if (trailing) f.queues.add(100) {}

            f.advance()
            assertTrue(f.events.isEmpty())
            assertEquals(if (trailing) 2 else 1, f.queues.size)
            f.advance()

            assertEquals(listOf(2), f.events)
            assertEquals(if (trailing) 1 else 0, f.queues.size)
        }
    }

    @Test
    fun `deferred callbacks can chain without sharing a queue pass`() {
        val f = Fixture()
        f.queues.addDeferred(1) {
            f.record()
            f.queues.addDeferred(1) { f.record() }
        }
        f.queues.add(100) {}

        f.advance()
        assertEquals(listOf(1), f.events)
        f.advance()

        assertEquals(listOf(1, 2), f.events)
        assertEquals(1, f.queues.size)
    }

    @Test
    fun `ordinary callbacks created during processing also wait for the next tick`() {
        val f = Fixture()
        f.queues.add(1) { f.queues.add(1) { f.record() } }
        f.queues.add(100) {}

        f.advance()

        assertTrue(f.events.isEmpty())
        assertEquals(2, f.queues.size)
        f.advance()
        assertEquals(listOf(2), f.events)
        assertEquals(1, f.queues.size)
    }

    @Test
    fun `deferred durations must be positive`() {
        val queues = WorldQueueList()
        assertThrows(IllegalArgumentException::class.java) { queues.addDeferred(0) {} }
        assertThrows(IllegalArgumentException::class.java) { queues.addDeferred(-1) {} }
        assertTrue(queues.isEmpty)
    }

    private class Fixture {
        val queues = WorldQueueList()
        private val clock = MapClock(0)
        private val process = WorldQueueListProcess(queues)
        val events = mutableListOf<Int>()
        fun record() { events += clock.cycle }
        fun advance() {
            clock.cycle++
            process.process()
        }
    }
}
