package org.rsmod.api.bosses.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList

class BossRepeatTickTest {
    @Test
    fun `one effect ticks once per cycle and stops after its duration`() {
        val fixture = Fixture()
        fixture.start("web", ticks = 3)

        fixture.advance(5)

        assertEquals(listOf("web:1:3", "web:2:2", "web:3:1"), fixture.hits)
        assertEquals(listOf("web:4"), fixture.stops)
        assertTrue(fixture.queues.isEmpty)
    }

    @Test
    fun `unrelated trailing queue cannot accelerate the repeating effect`() {
        val fixture = Fixture()
        fixture.start("web", ticks = 3)
        fixture.queues.add(100) {}

        fixture.advance(5)

        assertEquals(listOf("web:1:3", "web:2:2", "web:3:1"), fixture.hits)
        assertEquals(listOf("web:4"), fixture.stops)
    }

    @Test
    fun `overlapping effects keep independent one tick intervals`() {
        val fixture = Fixture()
        fixture.start("web", ticks = 3)
        fixture.start("healing", ticks = 3)

        fixture.advance(5)

        assertEquals(
            listOf(
                "web:1:3", "healing:1:3",
                "web:2:2", "healing:2:2",
                "web:3:1", "healing:3:1",
            ),
            fixture.hits,
        )
        assertEquals(listOf("web:4", "healing:4"), fixture.stops)
        assertTrue(fixture.queues.isEmpty)
    }

    @Test
    fun `cancellation stops once and skips all remaining ticks`() {
        val fixture = Fixture()
        fixture.start("web", ticks = 5, stopAt = 4)
        fixture.queues.add(100) {}

        fixture.advance(8)

        assertEquals(listOf("web:1:5", "web:2:4"), fixture.hits)
        assertEquals(listOf("web:2"), fixture.stops)
    }

    @Test
    fun `effect created during queue processing still spaces its callbacks one tick apart`() {
        val fixture = Fixture()
        fixture.queues.add(1) { fixture.start("web", ticks = 3) }
        fixture.queues.add(100) {}

        fixture.advance(5)

        assertEquals(listOf("web:2:3", "web:3:2", "web:4:1"), fixture.hits)
        assertEquals(listOf("web:5"), fixture.stops)
    }

    @Test
    fun `zero duration stops on next queue cycle without ticking`() {
        val fixture = Fixture()
        fixture.start("web", ticks = 0)

        fixture.advance(2)

        assertTrue(fixture.hits.isEmpty())
        assertEquals(listOf("web:1"), fixture.stops)
        assertTrue(fixture.queues.isEmpty)
    }

    @Test
    fun `indefinite watcher allocates one future callback and stops once`() {
        val fixture = Fixture()
        var calls = 0
        fixture.queues.repeatTick(
            Int.MAX_VALUE,
            onTick = { calls++; calls < 3 },
            onStop = { fixture.stops += "stopped" },
        )
        fixture.advance(5)

        assertEquals(3, calls)
        assertEquals(listOf("stopped"), fixture.stops)
        assertTrue(fixture.queues.isEmpty)
    }

    @Test
    fun `indefinite watcher created in a queue cannot repeat within that queue pass`() {
        val fixture = Fixture()
        var calls = 0
        fixture.queues.add(1) {
            fixture.queues.repeatTick(Int.MAX_VALUE, onTick = { calls++; calls < 3 })
        }
        fixture.queues.add(100) {}

        fixture.advance(1)
        assertEquals(0, calls)
        fixture.advance(1)
        assertEquals(1, calls)
        fixture.advance(2)
        assertEquals(3, calls)
    }

    private class Fixture {
        val queues = WorldQueueList()
        val hits = mutableListOf<String>()
        val stops = mutableListOf<String>()
        private val process = WorldQueueListProcess(queues)
        private var cycle = 0

        fun start(name: String, ticks: Int, stopAt: Int = -1) {
            queues.repeatTick(
                ticks,
                onTick = { remaining ->
                    hits += "$name:$cycle:$remaining"
                    remaining != stopAt
                },
                onStop = { stops += "$name:$cycle" },
            )
        }

        fun advance(ticks: Int) {
            repeat(ticks) {
                cycle++
                process.process()
            }
        }
    }
}
