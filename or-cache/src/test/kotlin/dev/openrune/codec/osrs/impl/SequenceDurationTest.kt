package dev.openrune.codec.osrs.impl

import dev.openrune.definition.type.SequenceType
import dev.openrune.types.SequenceServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SequenceDurationTest {
    @Test
    fun `frame durations round partial 600ms ticks up`() {
        val cases =
            listOf(
                emptyList<Int>() to 0,
                listOf(0) to 0,
                listOf(1) to 1,
                listOf(29) to 1,
                listOf(30) to 1,
                listOf(30, 1) to 2,
                listOf(30, 30) to 2,
                listOf(30, 30, 1) to 3,
            )
        for ((delays, expected) in cases) {
            assertEquals(expected, SequenceDuration.frameTicks(delays), delays.toString())
        }
    }

    @Test
    fun `only the trailing long-delay run is replaced by a single buffer`() {
        val cases =
            listOf(
                Triple(listOf(30), 30, 1),
                Triple(listOf(31), 5, 1),
                Triple(listOf(31, 800), 5, 1),
                Triple(listOf(26, 31, 800), 31, 2),
                Triple(listOf(31, 1), 32, 2),
                Triple(listOf(31, 1, 800), 37, 2),
            )
        for ((delays, cycles, ticks) in cases) {
            assertEquals(cycles, SequenceDuration.frameCycles(delays), delays.toString())
            assertEquals(ticks, SequenceDuration.frameTicks(delays), delays.toString())
        }
    }

    @Test
    fun `skeletal timing subtracts the range start and retains downward rounding`() {
        val cases =
            listOf(
                Triple(0, 0, 0),
                Triple(10, 39, 0),
                Triple(10, 40, 1),
                Triple(10, 41, 1),
                Triple(17, 257, 8),
            )
        for ((begin, end, ticks) in cases) {
            assertEquals(ticks, SequenceDuration.skeletalTicks(begin, end), "$begin..$end")
        }
    }

    @Test
    fun `codec preserves packed timing and raw delay metadata`() {
        val cases =
            listOf(
                Triple(SequenceType(frameDelays = mutableListOf(30, 1)), 2, 31),
                Triple(SequenceType(frameDelays = mutableListOf(26, 31, 800)), 2, 857),
                Triple(SequenceType(frameDelays = mutableListOf()), 0, 0),
                Triple(SequenceType(skeletalId = 4, rangeBegin = 17, rangeEnd = 257), 8, 240),
                Triple(SequenceType(skeletalId = 4, rangeBegin = 10, rangeEnd = 39), 0, 29),
                Triple(
                    SequenceType(skeletalId = 4, rangeBegin = 10, rangeEnd = 41, frameDelays = mutableListOf(1)),
                    1,
                    31,
                ),
            )
        for ((source, ticks, rawDelay) in cases) {
            val native = source.copy(id = 123, maxLoops = 7, forcedPriority = 3)
            val packed = SequenceServerType(id = native.id)
            SequenceServerCodec(mapOf(native.id to native)).run { packed.createData() }
            assertEquals(ticks, packed.tickDuration, source.toString())
            assertEquals(rawDelay, packed.totalDelay, source.toString())
            assertEquals(7, packed.maxLoops)
            assertEquals(3, packed.priority)
        }
    }

    @Test
    fun `codec without a source preserves an existing packed definition`() {
        for (sources in listOf(null, emptyMap<Int, SequenceType>())) {
            val packed = SequenceServerType(id = 123, tickDuration = 7, totalDelay = 200, maxLoops = 5)
            val expected = packed.copy()
            SequenceServerCodec(sources).run { packed.createData() }
            assertEquals(expected, packed)
        }
    }

    @Test
    fun `codec still rejects a non-skeletal source without frame delays`() {
        val source = SequenceType(id = 123)
        val packed = SequenceServerType(id = source.id)
        assertThrows(NullPointerException::class.java) {
            SequenceServerCodec(mapOf(source.id to source)).run { packed.createData() }
        }
    }
}
