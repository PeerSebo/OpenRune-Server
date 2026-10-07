package org.rsmod.api.account.saver

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.game.entity.Player

class AccountSaveQueueTest {
    @Test fun `concurrent preparation reservations cannot exceed capacity`() {
        val queue = AccountSaveQueue(32)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (1..500).map { executor.submit<Boolean> { queue.reserve() } }
            assertEquals(32, results.count { it.get(5, TimeUnit.SECONDS) })
            assertEquals(32, queue.outstanding())
            repeat(32) { queue.cancelReservation() }
            assertTrue(queue.reserve())
        } finally { executor.shutdownNow() }
    }

    @Test fun `in flight requests and retries retain their admission slot`() {
        val queue = AccountSaveQueue(1)
        val request = request(1, 1)
        assertTrue(queue.reserve())
        queue.addReserved(request)
        assertSame(request, queue.poll())
        assertFalse(queue.reserve())
        request.attempts++
        queue.retry(request)
        assertSame(request, queue.poll())
        assertFalse(queue.reserve())
        queue.complete(request)
        assertTrue(queue.reserve())
    }

    @Test fun `newer commits protect older retries until both callbacks finish`() {
        val queue = AccountSaveQueue(2)
        val old = request(1, 1)
        val latest = request(1, 2)
        for (request in listOf(old, latest)) {
            assertTrue(queue.reserve())
            queue.addReserved(request)
            queue.poll()
        }
        queue.markCommitted(latest)
        queue.complete(latest)
        assertTrue(queue.isCommitted(old))
        assertEquals(1, queue.trackedSessions())
        queue.complete(old)
        assertEquals(0, queue.trackedSessions())
        assertEquals(0, queue.outstanding())
        assertFalse(queue.isCommitted(request(1, 1, "new-login")))
    }

    @Test fun `completed sessions do not accumulate permanent tracking entries`() {
        val queue = AccountSaveQueue(1)
        repeat(10000) { id ->
            val request = request(id, id.toLong() + 1)
            assertTrue(queue.reserve())
            queue.addReserved(request)
            queue.poll()
            queue.markCommitted(request)
            queue.complete(request)
        }
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.trackedSessions())
        assertEquals(0, queue.outstanding())
    }

    private fun request(id: Int, sequence: Long, generation: String = "session") =
        AccountSaveRequest(1, id, Player(), {}).apply {
            this.sequence = sequence
            saveGeneration = generation
        }
}
