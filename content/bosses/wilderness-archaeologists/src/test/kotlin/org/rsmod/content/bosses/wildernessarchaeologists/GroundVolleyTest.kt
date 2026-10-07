package org.rsmod.content.bosses.wildernessarchaeologists

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

class GroundVolleyTest {
    @Test fun `special lands after its warning rather than applying immediate damage`() {
        val f = Fixture()
        f.launch(listOf(f.position!!))
        f.advance(3)
        assertEquals(0, f.hits)
        assertTrue(f.impacts.isEmpty())
        f.advance(1)
        assertEquals(1, f.hits)
        assertEquals(listOf(CoordGrid(2977, 3702)), f.impacts)
    }

    @Test fun `running out of a targeted blast before landing avoids its hit`() {
        val f = Fixture()
        val thrownAt = f.position!!
        f.launch(listOf(thrownAt))
        f.position = thrownAt.translate(2, 0)
        f.advance(4)
        assertEquals(0, f.hits)
        assertEquals(listOf(thrownAt), f.impacts)
    }

    @Test fun `one square away is still inside each three by three blast`() {
        val f = Fixture()
        val thrownAt = f.position!!
        f.launch(listOf(thrownAt, thrownAt, thrownAt))
        f.position = thrownAt.translate(1, 1)
        f.advance(4)
        assertEquals(3, f.hits)
        assertEquals(3, f.impacts.size)
    }

    @Test fun `stairs and logout prevent landing damage`() {
        val upstairs = Fixture()
        upstairs.launch(listOf(upstairs.position!!))
        upstairs.position = CoordGrid(2977, 3702, 1)
        upstairs.advance(4)
        assertEquals(0, upstairs.hits)
        val logout = Fixture()
        logout.launch(listOf(logout.position!!))
        logout.position = null
        logout.advance(4)
        assertEquals(0, logout.hits)
    }

    @Test fun `boss death and respawn invalidate all old flight callbacks`() {
        val f = Fixture()
        f.launch(listOf(f.position!!))
        f.advance(2)
        f.life = Any()
        f.advance(2)
        assertEquals(0, f.hits)
        assertTrue(f.impacts.isEmpty())
        f.launch(listOf(f.position!!))
        f.advance(4)
        assertEquals(1, f.hits)
    }

    @Test fun `volley copies target collection at throw time`() {
        val f = Fixture()
        val centres = mutableListOf(f.position!!)
        f.launch(centres)
        centres.clear()
        centres += CoordGrid(3200, 3200)
        f.advance(4)
        assertEquals(1, f.hits)
    }

    @Test fun `independent volleys keep separate impact times`() {
        val f = Fixture()
        f.launch(listOf(f.position!!))
        f.advance(2)
        f.launch(listOf(f.position!!))
        f.advance(2)
        assertEquals(1, f.hits)
        f.advance(2)
        assertEquals(2, f.hits)
        assertTrue(f.queues.isEmpty)
    }

    private class Fixture {
        val queues = WorldQueueList()
        private val process = WorldQueueListProcess(queues)
        private val volley = GroundVolley(queues)
        var position: CoordGrid? = CoordGrid(2977, 3702)
        var life = Any()
        var hits = 0
        val impacts = mutableListOf<CoordGrid>()
        fun launch(centres: List<CoordGrid>) {
            val currentLife = life
            volley.launch(centres, 4, 1, { life === currentLife }, { position }, { impacts += it }, { hits++ })
        }
        fun advance(ticks: Int) { repeat(ticks) { process.process() } }
    }
}
