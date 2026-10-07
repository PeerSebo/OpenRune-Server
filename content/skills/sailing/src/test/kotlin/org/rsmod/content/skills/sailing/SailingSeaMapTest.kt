package org.rsmod.content.skills.sailing

import dev.openrune.filesystem.Cache
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.BitSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SailingSeaMapTest {
    @Test
    fun `only complete ordinary water tiles without a bridge form the surface`() {
        val bytes = tileData(
            mapOf(
                Tile(1, 1) to Surface(overlay = 10, flags = 1),
                Tile(2, 1) to Surface(overlay = 11),
                Tile(3, 1) to Surface(overlay = 10, shape = 1),
                Tile(4, 1) to Surface(overlay = 10),
                Tile(4, 1, 1) to Surface(flags = 2),
                Tile(5, 1, 1) to Surface(overlay = 10),
            )
        )

        val water = SailingSeaMap.decodeWaterTiles(bytes, setOf(10))

        assertEquals(1, water.cardinality())
        assertTrue(water[1 * 64 + 1])
    }

    @Test
    fun `incomplete map data cannot silently create navigable tiles`() {
        assertThrows<IllegalStateException> {
            SailingSeaMap.decodeWaterTiles(byteArrayOf(0, 2, 0, 11, 0, 0), setOf(10))
        }
        val complete = tileData(mapOf(Tile(1, 1) to Surface(overlay = 10)))
        assertThrows<IllegalStateException> {
            SailingSeaMap.decodeWaterTiles(complete.copyOf(complete.size - 2), setOf(10))
        }
        assertThrows<IndexOutOfBoundsException> {
            SailingSeaMap.decodeWaterTiles(complete.copyOf(complete.size - 1), setOf(10))
        }
    }

    @Test
    fun `a native region trailer follows the complete terrain body`() {
        val body = tileData(mapOf(Tile(1, 1) to Surface(overlay = 10)))
        val water = SailingSeaMap.decodeWaterTiles(body + byteArrayOf(0), setOf(10))
        assertEquals(1, water.cardinality())
        assertTrue(water[1 * 64 + 1])
    }

    @Test
    fun `walking blocked water is usable while live loc and wall obstacles block`() {
        val fixture = Fixture()
        fixture.collision[104, 104, 0] = CollisionFlag.BLOCK_WALK or CollisionFlag.BLOCK_NPCS
        assertTrue(fixture.map.isNavigable(104, 104))

        for (obstacle in listOf(CollisionFlag.LOC, CollisionFlag.WALL_EAST, CollisionFlag.GROUND_DECOR,
            CollisionFlag.BLOCK_PLAYERS)) {
            fixture.collision[104, 104, 0] = CollisionFlag.BLOCK_WALK or obstacle
            assertFalse(fixture.map.isNavigable(104, 104))
        }
        fixture.collision[104, 104, 0] = CollisionFlag.BLOCK_WALK
        assertTrue(fixture.map.isNavigable(104, 104))
    }

    @Test
    fun `water initialization blocks walking while preserving sailing and existing obstacles`() {
        val fixture = Fixture()
        fixture.collision[104, 104, 0] = 0
        fixture.collision[105, 104, 0] = CollisionFlag.LOC
        fixture.collision[99, 104, 0] = 0

        fixture.map.blockWalkingOnWater()

        assertEquals(CollisionFlag.BLOCK_WALK, fixture.collision[104, 104, 0])
        assertTrue(fixture.map.isNavigable(104, 104))
        assertEquals(CollisionFlag.BLOCK_WALK or CollisionFlag.LOC, fixture.collision[105, 104, 0])
        assertFalse(fixture.map.isNavigable(105, 104))
        assertEquals(0, fixture.collision[99, 104, 0], "Non-water terrain stays untouched")

        fixture.collision.deallocateIfPresent(104, 104, 0)
        fixture.map.blockWalkingOnWater()
        assertFalse(fixture.collision.isZoneAllocated(104, 104, 0), "Missing zones stay unallocated")
    }

    @Test
    fun `missing surfaces zones and nonsea planes are closed`() {
        val fixture = Fixture()
        assertFalse(fixture.map.isNavigable(104, 104, 1))
        assertFalse(fixture.map.isNavigable(-1, 104))
        assertFalse(fixture.map.isNavigable(16384, 104))
        assertFalse(fixture.map.isNavigable(99, 104))
        fixture.collision.deallocateIfPresent(104, 104, 0)
        assertFalse(fixture.map.isNavigable(104, 104))
        assertFalse(SailingSeaMap(fixture.collision).isNavigable(104, 104))
    }

    @Test
    fun `a swept hull cannot skip a blocked tile between clear endpoints`() {
        val fixture = Fixture()
        assertTrue(fixture.move(101, 104, 107, 104))
        fixture.collision[104, 104, 0] = CollisionFlag.LOC

        assertTrue(fixture.move(101, 104, 101, 104))
        assertTrue(fixture.move(107, 104, 107, 104))
        assertFalse(fixture.move(101, 104, 107, 104))
    }

    @Test
    fun `boat sides must clear obstacles even when the root tile is clear`() {
        val fixture = Fixture()
        fixture.collision[105, 104, 0] = CollisionFlag.LOC
        assertTrue(fixture.move(104, 104, 104, 104))
        assertFalse(fixture.move(104, 104, 104, 104, width = 3))
        assertFalse(fixture.move(104, 104, 104, 104, angle = 512, length = 3))
    }

    @Test
    fun `turning sweeps the hull around the pivot`() {
        val fixture = Fixture()
        fixture.collision[106, 104, 0] = CollisionFlag.LOC
        assertTrue(fixture.move(104, 104, 104, 104, length = 3))
        assertFalse(fixture.map.canMove(104 * 128 + 64, 104 * 128 + 64,
            104 * 128 + 64, 104 * 128 + 64, angle = 128, width = 1, length = 3,
            startAngle = 0))
    }

    @Test
    fun `native bounds offsets shift the checked footprint`() {
        val fixture = Fixture()
        fixture.collision[104, 102, 0] = CollisionFlag.LOC
        assertTrue(fixture.move(104, 104, 104, 104))
        assertFalse(fixture.map.canMove(104 * 128 + 64, 104 * 128 + 64,
            104 * 128 + 64, 104 * 128 + 64, angle = 0, width = 1, length = 1,
            boundsOffsetZ = -256))
    }

    @Test
    fun `invalid movement dimensions and root coordinates are rejected`() {
        val fixture = Fixture()
        assertFalse(fixture.move(104, 104, 104, 104, width = 0))
        assertFalse(fixture.move(104, 104, 104, 104, angle = 2048))
        assertFalse(fixture.map.canMove(-1, 104, 0, 104, 0, 1, 1))
    }

    @Test
    fun `native starter berths are ordinary water and the guessed Sarim pier is excluded`() {
        val live = Cache.load(Paths.get(".data", "cache", "LIVE"))
        val overlays = try {
            SailingSeaMap.ordinaryWaterOverlays(live, 241)
        } finally {
            live.close()
        }
        assertTrue(overlays.containsAll(listOf(441, 444, 447, 450)))
        val server = Cache.load(Paths.get(".data", "cache", "SERVER"))
        try {
            fun waterAt(x: Int, z: Int): Boolean {
                val region = ((x shr 6) shl 8) or (z shr 6)
                val bytes = checkNotNull(server.data(5, region, 0))
                val water = SailingSeaMap.decodeWaterTiles(bytes, overlays)
                return water[(x and 63) * 64 + (z and 63)]
            }
            assertTrue(waterAt(2690, 3162), "Port Khazard captured berth")
            assertTrue(waterAt(2796, 3408), "Catherby captured berth")
            assertFalse(waterAt(3031, 3203), "Old Port Sarim estimate is beneath a pier")
            assertFalse(waterAt(3025, 3204), "Old Port Sarim land estimate")
            for (dock in SailingDocks.AUTHORED) {
                val land = checkNotNull(dock.landCoords)
                assertFalse(waterAt(land.x, land.z), "${dock.dbrow} lands on its pier")
                for (type in 0..2) {
                    val sea = checkNotNull(dock.seaCoordsFor(type))
                    assertTrue(waterAt(sea.x, sea.z), "${dock.dbrow} type $type starts on water")
                }
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `authored berths retain all unique dock entries and reject unknown hull types`() {
        assertEquals(61, SailingDocks.ALL.size)
        assertEquals(61, SailingDocks.ALL.map { it.dbrow }.distinct().size)
        for (dock in SailingDocks.AUTHORED) {
            assertTrue(dock.canSpawnBoat)
            assertEquals(null, dock.seaCoordsFor(-1))
            assertEquals(null, dock.seaCoordsFor(3))
        }
        assertTrue(SailingDocks.TODO_GANGPLANK_DOCKS.all { it.seaCoordsFor(0) == null })
    }

    private class Fixture {
        val collision = CollisionFlagMap(arrayOfNulls(40000))
        val water = BitSet(4096)
        val map = SailingSeaMap(collision, mutableMapOf(257 to water))

        init {
            for (x in 100..110) {
                for (z in 100..110) {
                    water.set((x and 63) * 64 + (z and 63))
                    collision[x, z, 0] = CollisionFlag.BLOCK_WALK
                }
            }
        }

        fun move(x: Int, z: Int, destX: Int, destZ: Int, angle: Int = 0, width: Int = 1,
            length: Int = 1): Boolean = map.canMove(x * 128 + 64, z * 128 + 64,
            destX * 128 + 64, destZ * 128 + 64, angle, width, length)
    }

    private data class Tile(val x: Int, val z: Int, val level: Int = 0)
    private data class Surface(val overlay: Int? = null, val shape: Int = 0, val flags: Int = 0)

    private fun tileData(surfaces: Map<Tile, Surface>): ByteArray {
        val bytes = ByteArrayOutputStream()
        fun short(value: Int) {
            bytes.write(value ushr 8)
            bytes.write(value)
        }
        for (level in 0..3) for (x in 0..63) for (z in 0..63) {
            val surface = surfaces[Tile(x, z, level)]
            if (surface?.overlay != null) {
                short(2 + surface.shape * 4)
                short(surface.overlay + 1)
            }
            if (surface != null && surface.flags != 0) short(49 + surface.flags)
            short(0)
        }
        return bytes.toByteArray()
    }
}
