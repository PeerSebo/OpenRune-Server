package org.rsmod.api.registry.account

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.account.saver.request.AccountSaveCallback
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.game.entity.Player

class LogoutSaveQueueTest {
    @Test
    fun `shutdown diagnostics include failed preparations before intake and after the final retry`() {
        val fixture = Fixture()
        val player = fixture.player().apply { loggingOut = true }
        fixture.failPreparation = true
        fixture.queue.save(player)
        assertEquals(listOf(player), fixture.queue.pendingPreparations())

        fixture.advance(1024)

        assertEquals(listOf(player), fixture.queue.pendingPreparations())
        assertEquals(21, fixture.attemptCycles.size)
        assertTrue(player in fixture.registered)

        fixture.advance(26)
        assertEquals(listOf(player), fixture.queue.pendingPreparations())
        fixture.advance()
        assertEquals(listOf(player), fixture.queue.pendingPreparations())

        fixture.failPreparation = false
        fixture.advance(49)
        assertTrue(fixture.queue.pendingPreparations().isEmpty())
        assertTrue(player in fixture.registered)
        fixture.complete(AccountSaveResponse.Success(player))
        fixture.advance()
        assertFalse(player in fixture.registered)
        assertTrue(fixture.queue.pendingPreparations().isEmpty())
    }

    @Test
    fun `preparation failure retains player until a successful retry completes`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.failPreparation = true
        fixture.queue.save(player)

        fixture.advance(49)

        assertTrue(player in fixture.registered)
        assertEquals(listOf(0), fixture.attemptCycles)

        fixture.failPreparation = false
        fixture.advance()
        assertEquals(listOf(0, 50), fixture.attemptCycles)
        assertTrue(player in fixture.registered)
        fixture.complete(AccountSaveResponse.Success(player))
        assertTrue(player in fixture.registered)
        fixture.advance()

        assertFalse(player in fixture.registered)
        assertEquals(listOf(player), fixture.removed)
        fixture.advance(100)
        assertEquals(listOf(0, 50), fixture.attemptCycles)
    }

    @Test
    fun `repeated preparation failures each wait fifty cycles`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.failPreparation = true
        fixture.queue.save(player)

        fixture.advance(149)

        assertEquals(listOf(0, 50, 100), fixture.attemptCycles)
        assertTrue(player in fixture.registered)
        assertTrue(fixture.removed.isEmpty())
        fixture.advance()
        assertEquals(listOf(0, 50, 100, 150), fixture.attemptCycles)
    }

    @Test
    fun `worker responses only trigger retry and removal on the processing thread`() {
        val fixture = Fixture()
        val player = fixture.player()
        val gameThread = Thread.currentThread()
        val worker = Executors.newSingleThreadExecutor()
        try {
            fixture.queue.save(player)
            worker.submit { fixture.complete(AccountSaveResponse.PreparationFailure(player)) }
                .get(5, TimeUnit.SECONDS)

            assertEquals(listOf(gameThread), fixture.attemptThreads)
            assertTrue(fixture.removed.isEmpty())
            fixture.advance(49)
            assertEquals(1, fixture.attemptThreads.size)
            fixture.advance()
            assertEquals(listOf(gameThread, gameThread), fixture.attemptThreads)

            worker.submit { fixture.complete(AccountSaveResponse.Success(player)) }
                .get(5, TimeUnit.SECONDS)
            assertTrue(player in fixture.registered)
            assertTrue(fixture.removalThreads.isEmpty())
            fixture.advance()

            assertEquals(listOf(gameThread), fixture.removalThreads)
            assertFalse(player in fixture.registered)
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun `retry processing respects the per-cycle budget and does not resubmit pending saves`() {
        val fixture = Fixture()
        val players = List(3) { fixture.player() }
        fixture.failPreparation = true
        players.forEach(fixture.queue::save)
        fixture.advance(49)
        fixture.failPreparation = false

        fixture.advance(count = 1)
        assertEquals(listOf(0, 0, 0, 50), fixture.attemptCycles)
        fixture.advance(count = 1)
        assertEquals(listOf(0, 0, 0, 50, 51), fixture.attemptCycles)
        fixture.advance(count = 1)
        assertEquals(listOf(0, 0, 0, 50, 51, 52), fixture.attemptCycles)
        fixture.advance(100)

        assertEquals(6, fixture.attemptCycles.size)
        assertTrue(players.all { it in fixture.registered })
        assertTrue(fixture.removed.isEmpty())
    }

    @Test
    fun `every failed logout save retains the player until a database commit succeeds`() {
        val failures: List<(Player) -> AccountSaveResponse.Failure> = listOf(
            { AccountSaveResponse.ExcessiveRetries(it) },
            { AccountSaveResponse.InternalShutdownError(it) },
            { AccountSaveResponse.RecoveryFailure(it) },
            { AccountSaveResponse.PreparationFailure(it) },
            { AccountSaveResponse.QueueFull(it) },
        )
        for (failure in failures) {
            val fixture = Fixture()
            val player = fixture.player().apply { loggingOut = true }
            fixture.queue.save(player)
            fixture.complete(failure(player))
            assertEquals(listOf(player), fixture.queue.pendingPreparations())

            fixture.advance(49)
            assertTrue(player in fixture.registered)
            assertTrue(fixture.removed.isEmpty())
            assertEquals(listOf(0), fixture.attemptCycles)
            fixture.advance()
            assertEquals(listOf(0, 50), fixture.attemptCycles)
            fixture.advance(100)
            assertEquals(listOf(0, 50), fixture.attemptCycles)
            assertTrue(player in fixture.registered)

            fixture.complete(AccountSaveResponse.Success(player))
            fixture.advance()
            assertFalse(player in fixture.registered)
            assertEquals(listOf(player), fixture.removed)
            assertTrue(fixture.queue.pendingPreparations().isEmpty())
            fixture.advance(100)
            assertEquals(listOf(0, 50), fixture.attemptCycles)
            assertEquals(listOf(player), fixture.removed)
        }
    }

    @Test
    fun `repeated terminal failures retry without removing the player or overlapping saves`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.queue.save(player)
        fixture.complete(AccountSaveResponse.ExcessiveRetries(player))
        fixture.advance(50)
        fixture.complete(AccountSaveResponse.InternalShutdownError(player))
        fixture.advance(50)

        assertEquals(listOf(0, 50, 100), fixture.attemptCycles)
        assertTrue(fixture.removed.isEmpty())
        fixture.advance(100)
        assertEquals(3, fixture.attemptCycles.size)
        fixture.complete(AccountSaveResponse.Success(player))
        fixture.advance(100)
        assertEquals(listOf(player), fixture.removed)
        assertEquals(3, fixture.attemptCycles.size)
    }

    @Test
    fun `failed database and recovery writes retain logout player for a delayed retry`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.queue.save(player)
        fixture.complete(AccountSaveResponse.RecoveryFailure(player))
        assertEquals(listOf(player), fixture.queue.pendingPreparations())

        fixture.advance(49)
        assertTrue(player in fixture.registered)
        assertEquals(listOf(0), fixture.attemptCycles)
        fixture.advance()
        assertEquals(listOf(0, 50), fixture.attemptCycles)
        fixture.complete(AccountSaveResponse.Success(player))
        fixture.advance()
        assertFalse(player in fixture.registered)
    }

    private class Fixture {
        val registered = mutableSetOf<Player>()
        val removed = mutableListOf<Player>()
        val attemptCycles = mutableListOf<Int>()
        val attemptThreads = mutableListOf<Thread>()
        val removalThreads = mutableListOf<Thread>()
        private val callbacks = mutableMapOf<Player, AccountSaveCallback>()
        private var cycle = 0
        var failPreparation = false

        val queue =
            LogoutSaveQueue(
                requestSave = { player, callback ->
                    attemptCycles += cycle
                    attemptThreads += Thread.currentThread()
                    if (failPreparation) {
                        callback(AccountSaveResponse.PreparationFailure(player))
                    } else {
                        callbacks[player] = callback
                    }
                },
                removePlayer = { player ->
                    removalThreads += Thread.currentThread()
                    removed += player
                    registered.remove(player)
                },
            )

        fun player(): Player = Player().also(registered::add)

        fun complete(response: AccountSaveResponse) {
            checkNotNull(callbacks.remove(response.player)).invoke(response)
        }

        fun advance(cycles: Int = 1, count: Int = 200) {
            repeat(cycles) {
                cycle++
                queue.process(count)
            }
        }
    }
}
