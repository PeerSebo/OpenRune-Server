package org.rsmod.content.bosses.derangedarchaeologist

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.random.GameRandom
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

class ArchaeologistBooksTest {
    @Test fun `initial books impact after four ticks and central book makes two later impacts`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val launches = mutableListOf<Pair<CoordGrid, CoordGrid>>()
        val impacts = mutableListOf<CoordGrid>()
        val origin = CoordGrid(3683, 3706)
        val target = CoordGrid(3685, 3700)
        val tiles = ArchaeologistBooks(FixedRandom(), queues).throwBooks(
            origin, target, { from, to, _ -> launches += from to to }, impacts::add,
        )
        assertEquals(5, tiles.toSet().size)
        assertEquals(target, tiles.first())
        assertEquals(3, launches.size)
        repeat(3) { process.process() }
        assertTrue(impacts.isEmpty())
        process.process()
        assertEquals(tiles.take(3), impacts)
        assertEquals(listOf(target, target), launches.drop(3).map { it.first })
        process.process()
        assertEquals(3, impacts.size)
        process.process()
        assertEquals(tiles, impacts)
        assertTrue(queues.isEmpty)
    }

    @Test fun `dodging after launch uses position at impact and other floors are safe`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val target = CoordGrid(3685, 3700)
        var currentPosition = target
        var damage = 0
        ArchaeologistBooks(FixedRandom(), queues).throwBooks(target, target, { _, _, _ -> }, {
            damage += ArchaeologistBooks.maxDamage(currentPosition, it)
        })
        repeat(3) { process.process() }
        currentPosition = target.translate(9, 9)
        repeat(3) { process.process() }
        assertEquals(0, damage)
        assertEquals(0, ArchaeologistBooks.maxDamage(CoordGrid(3685, 3700, 1), target))
    }

    @Test fun `direct and adjacent blast caps apply independently`() {
        val tile = CoordGrid(3685, 3700)
        assertEquals(56, ArchaeologistBooks.maxDamage(tile, tile))
        for (dx in -1..1) for (dz in -1..1) {
            if (dx != 0 || dz != 0) assertEquals(18, ArchaeologistBooks.maxDamage(tile.translate(dx, dz), tile))
        }
        assertEquals(0, ArchaeologistBooks.maxDamage(tile.translate(2, 0), tile))
    }

    @Test fun `repeating identical random draws never overlap book destinations`() {
        for (value in 0..10) {
            val target = CoordGrid(3685, 3700)
            val tiles = ArchaeologistBooks(FixedRandom(value), WorldQueueList()).throwBooks(
                target, target, { _, _, _ -> }, {},
            )
            assertEquals(5, tiles.toSet().size)
            assertTrue(tiles.all { it.chebyshevDistance(target) <= 4 })
        }
    }

    @Test fun `trunk crosses to both sides of its actual blocking row`() {
        assertEquals(CoordGrid(3682, 3715), DerangedAccess.crossingDestination(CoordGrid(3682, 3717)))
        assertEquals(CoordGrid(3682, 3717), DerangedAccess.crossingDestination(CoordGrid(3682, 3715)))
    }

    private class FixedRandom(private val value: Int = 0) : GameRandom {
        override fun of(maxExclusive: Int): Int = value % maxExclusive
        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive + of(maxInclusive - minInclusive + 1)
        override fun randomDouble(): Double = 0.0
    }
}
