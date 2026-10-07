package org.rsmod.content.areas.city.varrock.pack

import dev.openrune.cache.filestore.definition.ModelDecoder
import dev.openrune.definition.type.model.MeshType
import dev.openrune.definition.type.model.ModelType
import dev.openrune.filesystem.Cache
import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.city.varrock.pack.GardenCornerMesh.Point

class VarrockGardenModelsTest {
    private val quarter = listOf(
        Point(-64.0, 0.0, 64.0),
        Point(-55.0, 0.0, 30.0),
        Point(-54.0, 0.0, 28.0),
        Point(-37.0, 0.0, -2.0),
        Point(-2.0, 0.0, -37.0),
        Point(28.0, 0.0, -55.0),
        Point(30.0, 0.0, -56.0),
        Point(64.0, 0.0, -64.0),
        Point(64.0, 0.0, 64.0),
    )

    @Test
    fun `soil triangles are clipped to the native curve with no vertices outside`() {
        val soil = model(
            listOf(
                Point(-64.0, 0.0, -64.0), Point(64.0, 0.0, -64.0),
                Point(64.0, 0.0, 64.0), Point(-64.0, 0.0, 64.0),
            ),
            listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3)),
        )
        val clipped = GardenCornerMesh.clip(1, listOf(soil), quarter)

        assertTrue(clipped.triangleCount > soil.triangleCount)
        assertTrue(vertices(clipped).all { GardenCornerMesh.contains(quarter, it) })
        assertFalse(vertices(clipped).contains(Point(-64.0, 0.0, -64.0)))
        assertTrue(vertices(clipped).contains(Point(64.0, 0.0, 64.0)))
        assertEquals(11732.5, projectedArea(clipped), 100.0)
        assertEquals(4, soil.vertexCount)
        assertEquals(2, soil.triangleCount)
    }

    @Test
    fun `vertical foliage remains visible while crossing leaves are trimmed`() {
        val leaf = model(
            listOf(
                Point(-64.0, 0.0, -32.0), Point(64.0, 0.0, -32.0),
                Point(64.0, -110.0, -32.0),
            ),
            listOf(intArrayOf(0, 1, 2)),
        )
        val clipped = GardenCornerMesh.clip(1, listOf(leaf), quarter)

        assertTrue(clipped.triangleCount > 0)
        assertTrue(vertices(clipped).all { GardenCornerMesh.contains(quarter, it) })
        assertEquals(-110, checkNotNull(clipped.vertexPositionsY).min())
        assertEquals(0, checkNotNull(clipped.vertexPositionsY).max())
        assertTrue(checkNotNull(clipped.vertexPositionsY).any { it in -109..-1 })
    }

    @Test
    fun `each split face retains its color transparency and priority`() {
        val leaf = model(
            listOf(
                Point(-64.0, 0.0, -32.0), Point(64.0, 0.0, -32.0),
                Point(64.0, -110.0, -32.0),
            ),
            listOf(intArrayOf(0, 1, 2)),
        ).apply {
            triangleColors = shortArrayOf(8123)
            triangleAlphas = intArrayOf(64)
            triangleRenderTypes = intArrayOf(1)
            triangleRenderPriorities = intArrayOf(3)
        }
        val clipped = GardenCornerMesh.clip(1, listOf(leaf), quarter)

        assertTrue(checkNotNull(clipped.triangleColors).all { it == 8123.toShort() })
        assertTrue(clipped.triangleAlphas!!.all { it == 64 })
        assertTrue(clipped.triangleRenderTypes!!.all { it == 1 })
        assertTrue(clipped.triangleRenderPriorities!!.all { it == 3 })
    }

    @Test
    fun `paving boundary ignores internal and duplicate vertices`() {
        val paving = model(
            quarter + Point(0.0, -12.0, 0.0) + quarter.first().copy(y = -12.0),
            emptyList(),
        )
        val hull = GardenCornerMesh.boundary(paving)

        assertEquals(quarter, hull)
    }

    @Test
    fun `fully exterior foliage is omitted without corrupting remaining faces`() {
        val foliage = model(
            listOf(
                Point(-64.0, 0.0, -64.0), Point(-50.0, -30.0, -64.0),
                Point(-64.0, -30.0, -50.0), Point(30.0, 0.0, 30.0),
                Point(50.0, 0.0, 30.0), Point(30.0, -30.0, 50.0),
            ),
            listOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5)),
        ).apply { triangleColors = shortArrayOf(10, 20) }
        val clipped = GardenCornerMesh.clip(1, listOf(foliage), quarter)

        assertEquals(1, clipped.triangleCount)
        assertEquals(3, clipped.vertexCount)
        assertArrayEquals(shortArrayOf(20), clipped.triangleColors)
    }

    @Test
    fun `combining soil and weeds is deterministic and preserves both face groups`() {
        val soil = model(
            listOf(Point(0.0, 0.0, 0.0), Point(60.0, 0.0, 0.0), Point(0.0, 0.0, 60.0)),
            listOf(intArrayOf(0, 1, 2)),
        ).apply { triangleColors = shortArrayOf(10) }
        val weeds = model(
            listOf(Point(0.0, 0.0, 0.0), Point(20.0, -80.0, 0.0), Point(20.0, 0.0, 0.0)),
            listOf(intArrayOf(0, 1, 2)),
        ).apply { triangleColors = shortArrayOf(20) }
        val first = GardenCornerMesh.clip(1, listOf(soil, weeds), quarter)
        val second = GardenCornerMesh.clip(1, listOf(soil, weeds), quarter)

        assertArrayEquals(shortArrayOf(10, 20), first.triangleColors)
        assertEquals(2, first.triangleCount)
        assertEquals(5, first.vertexCount)
        assertArrayEquals(first.vertexPositionsX, second.vertexPositionsX)
        assertArrayEquals(first.vertexPositionsY, second.vertexPositionsY)
        assertArrayEquals(first.vertexPositionsZ, second.vertexPositionsZ)
        assertArrayEquals(first.triangleVertex1, second.triangleVertex1)
        assertArrayEquals(first.triangleVertex2, second.triangleVertex2)
        assertArrayEquals(first.triangleVertex3, second.triangleVertex3)
    }

    @Test
    fun `encoded clipped mesh preserves native soil transparency and foliage priority`() {
        val soil = model(
            listOf(Point(0.0, 0.0, 0.0), Point(60.0, 0.0, 0.0), Point(0.0, 0.0, 60.0)),
            listOf(intArrayOf(0, 1, 2)),
        ).apply {
            type = MeshType.VersionedSkeletal
            version = 12
            triangleColors = shortArrayOf(10)
            triangleAlphas = intArrayOf(-2)
            renderPriority = 4
        }
        val weeds = model(
            listOf(Point(0.0, 0.0, 0.0), Point(20.0, -80.0, 0.0), Point(20.0, 0.0, 0.0)),
            listOf(intArrayOf(0, 1, 2)),
        ).apply {
            type = MeshType.UnversionedSkeletal
            version = -1
            triangleColors = shortArrayOf(20)
            renderPriority = 1
        }
        val clipped = GardenCornerMesh.clip(1, listOf(weeds, soil), quarter)
        val unusedCache = Proxy.newProxyInstance(
            Cache::class.java.classLoader,
            arrayOf(Cache::class.java),
        ) { _, _, _ -> error("Decoding a supplied model must not access the cache") } as Cache
        val decoded = checkNotNull(ModelDecoder(unusedCache).getModel(clipped.saveAsDat(), 1))

        assertArrayEquals(shortArrayOf(20, 10), decoded.triangleColors)
        assertArrayEquals(intArrayOf(0, -2), decoded.triangleAlphas)
        assertArrayEquals(intArrayOf(1, 4), decoded.triangleRenderPriorities)
        assertArrayEquals(clipped.vertexPositionsX, decoded.vertexPositionsX)
        assertArrayEquals(clipped.vertexPositionsY, decoded.vertexPositionsY)
        assertArrayEquals(clipped.vertexPositionsZ, decoded.vertexPositionsZ)
    }

    private fun model(points: List<Point>, triangles: List<IntArray>): ModelType =
        ModelType().apply {
            vertexCount = points.size
            vertexPositionsX = points.map { it.x.toInt() }.toIntArray()
            vertexPositionsY = points.map { it.y.toInt() }.toIntArray()
            vertexPositionsZ = points.map { it.z.toInt() }.toIntArray()
            triangleCount = triangles.size
            triangleVertex1 = triangles.map { it[0] }.toIntArray()
            triangleVertex2 = triangles.map { it[1] }.toIntArray()
            triangleVertex3 = triangles.map { it[2] }.toIntArray()
            triangleColors = ShortArray(triangles.size) { 100 }
        }

    private fun vertices(model: ModelType): List<Point> {
        val x = checkNotNull(model.vertexPositionsX)
        val y = checkNotNull(model.vertexPositionsY)
        val z = checkNotNull(model.vertexPositionsZ)
        return (0 until model.vertexCount).map {
            Point(
                x[it].toDouble(),
                y[it].toDouble(),
                z[it].toDouble(),
            )
        }
    }

    private fun projectedArea(model: ModelType): Double {
        val x = checkNotNull(model.vertexPositionsX)
        val z = checkNotNull(model.vertexPositionsZ)
        val trianglesA = checkNotNull(model.triangleVertex1)
        val trianglesB = checkNotNull(model.triangleVertex2)
        val trianglesC = checkNotNull(model.triangleVertex3)
        return (0 until model.triangleCount).sumOf {
            val a = trianglesA[it]
            val b = trianglesB[it]
            val c = trianglesC[it]
            kotlin.math.abs(
                (x[b] - x[a]).toDouble() * (z[c] - z[a]) -
                    (z[b] - z[a]).toDouble() * (x[c] - x[a]),
            ) / 2
        }
    }
}
