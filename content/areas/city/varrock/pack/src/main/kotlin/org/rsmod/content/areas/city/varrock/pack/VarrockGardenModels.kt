package org.rsmod.content.areas.city.varrock.pack

import dev.openrune.OsrsCacheProvider
import dev.openrune.cache.filestore.definition.ModelDecoder
import dev.openrune.definition.type.model.ModelType
import dev.openrune.filesystem.Cache
import dev.openrune.pack.ClientCacheTask
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.math.roundToInt

internal class VarrockGardenModels : ClientCacheTask() {
    override fun init(cache: Cache) {
        val provider = OsrsCacheProvider(cache, revision).apply { init() }
        val decoder = ModelDecoder(cache)
        fun models(loc: String): List<ModelType> =
            checkNotNull(provider.objects[loc.asRSCM()]).objectModels.orEmpty().map {
                checkNotNull(decoder.getModel(it))
            }

        val boundary = GardenCornerMesh.boundary(models("loc.garden_paving_curved").single())
        val stages = listOf(
            "cleared" to "loc.garden_delphiniums_weeded",
            "weeds_1" to "loc.garden_delphiniums_weeds_1",
            "weeds_2" to "loc.garden_delphiniums_weeds_2",
            "weeds_3" to "loc.garden_delphiniums_weeds_3",
        )
        for ((stage, loc) in stages) {
            val id = "models.varrock_garden_corner_$stage".asRSCM()
            val mesh = GardenCornerMesh.clip(id, models(loc), boundary)
            cache.write(MODEL_INDEX, id, 0, mesh.saveAsDat(), null)
        }
    }

    private companion object {
        const val MODEL_INDEX = 7
    }
}

/** Clips static garden foliage to the native curved soil outline, preserving face appearance. */
internal object GardenCornerMesh {
    data class Point(val x: Double, val y: Double, val z: Double)

    fun boundary(model: ModelType): List<Point> {
        val x = checkNotNull(model.vertexPositionsX)
        val z = checkNotNull(model.vertexPositionsZ)
        val points = (0 until model.vertexCount).map {
            Point(x[it].toDouble(), 0.0, z[it].toDouble())
        }.distinct().sortedWith(compareBy(Point::x, Point::z))
        fun half(points: List<Point>): List<Point> {
            val hull = mutableListOf<Point>()
            for (point in points) {
                while (hull.size >= 2 && side(hull[hull.lastIndex - 1], hull.last(), point) <= 0) {
                    hull.removeAt(hull.lastIndex)
                }
                hull += point
            }
            return hull.dropLast(1)
        }
        return (half(points) + half(points.asReversed())).also { require(it.size >= 3) }
    }

    fun clip(id: Int, sources: List<ModelType>, boundary: List<Point>): ModelType {
        require(sources.isNotEmpty())
        require(boundary.size >= 3)
        val vertices = mutableListOf<Point>()
        val indices = LinkedHashMap<Point, Int>()
        val triangles = mutableListOf<IntArray>()
        val colors = mutableListOf<Short>()
        val alphas = mutableListOf<Int>()
        val types = mutableListOf<Int>()
        val priorities = mutableListOf<Int>()
        for (source in sources) {
            require(source.textureTriangleCount == 0 && source.triangleTextures == null)
            require(source.vertexSkins == null && source.skeletalBones == null)
            val x = checkNotNull(source.vertexPositionsX)
            val y = checkNotNull(source.vertexPositionsY)
            val z = checkNotNull(source.vertexPositionsZ)
            val a = checkNotNull(source.triangleVertex1)
            val b = checkNotNull(source.triangleVertex2)
            val c = checkNotNull(source.triangleVertex3)
            val faceColors = checkNotNull(source.triangleColors)
            for (face in 0 until source.triangleCount) {
                val polygon = listOf(
                    a[face],
                    b[face],
                    c[face],
                ).map { index ->
                    Point(
                        x[index].toDouble(),
                        y[index].toDouble(),
                        z[index].toDouble(),
                    )
                }
                val clipped = clipPolygon(polygon, boundary).map { quantize(it, boundary) }
                for (i in 1 until clipped.lastIndex) {
                    val points = listOf(clipped[0], clipped[i], clipped[i + 1])
                    if (degenerate(points)) continue
                    triangles += points.map { point ->
                        indices.getOrPut(point) { vertices.add(point); vertices.lastIndex }
                    }.toIntArray()
                    colors += faceColors[face]
                    alphas += source.triangleAlphas?.get(face) ?: 0
                    types += source.triangleRenderTypes?.get(face) ?: 0
                    priorities += source.triangleRenderPriorities?.get(face) ?: source.renderPriority
                }
            }
        }
        require(triangles.isNotEmpty())
        return ModelType().apply {
            this.id = id
            type = sources.first().type
            version = sources.first().version
            vertexCount = vertices.size
            triangleCount = triangles.size
            vertexPositionsX = vertices.map { it.x.toInt() }.toIntArray()
            vertexPositionsY = vertices.map { it.y.toInt() }.toIntArray()
            vertexPositionsZ = vertices.map { it.z.toInt() }.toIntArray()
            triangleVertex1 = triangles.map { it[0] }.toIntArray()
            triangleVertex2 = triangles.map { it[1] }.toIntArray()
            triangleVertex3 = triangles.map { it[2] }.toIntArray()
            triangleColors = colors.toShortArray()
            triangleAlphas = alphas.toIntArray().takeIf { it.any { alpha -> alpha != 0 } }
            triangleRenderTypes = types.toIntArray().takeIf { it.any { type -> type != 0 } }
            triangleRenderPriorities = priorities.toIntArray()
        }
    }

    private fun clipPolygon(points: List<Point>, boundary: List<Point>): List<Point> {
        var polygon = points
        for (index in boundary.indices) {
            if (polygon.isEmpty()) break
            val a = boundary[index]
            val b = boundary[(index + 1) % boundary.size]
            val output = mutableListOf<Point>()
            var previous = polygon.last()
            var previousSide = side(a, b, previous)
            for (current in polygon) {
                val currentSide = side(a, b, current)
                if ((currentSide >= 0) != (previousSide >= 0)) {
                    val fraction = previousSide / (previousSide - currentSide)
                    output += Point(
                        previous.x + (current.x - previous.x) * fraction,
                        previous.y + (current.y - previous.y) * fraction,
                        previous.z + (current.z - previous.z) * fraction,
                    )
                }
                if (currentSide >= 0) output += current
                previous = current
                previousSide = currentSide
            }
            polygon = output
        }
        return polygon
    }

    internal fun contains(boundary: List<Point>, point: Point): Boolean =
        boundary.indices.all { side(boundary[it], boundary[(it + 1) % boundary.size], point) >= 0 }

    private fun quantize(point: Point, boundary: List<Point>): Point {
        val rounded = Point(
            point.x.roundToInt().toDouble(),
            point.y.roundToInt().toDouble(),
            point.z.roundToInt().toDouble(),
        )
        if (contains(boundary, rounded)) return rounded
        return (-2..2).flatMap { dx ->
            (-2..2).map { dz -> rounded.copy(x = rounded.x + dx, z = rounded.z + dz) }
        }.filter { contains(boundary, it) }.minByOrNull {
            (it.x - point.x) * (it.x - point.x) + (it.z - point.z) * (it.z - point.z)
        } ?: error("Unable to round clipped garden vertex inside its boundary: $point")
    }

    private fun degenerate(points: List<Point>): Boolean {
        val a = points[0]
        val b = points[1]
        val c = points[2]
        return (b.y - a.y) * (c.z - a.z) == (b.z - a.z) * (c.y - a.y) &&
            (b.z - a.z) * (c.x - a.x) == (b.x - a.x) * (c.z - a.z) &&
            (b.x - a.x) * (c.y - a.y) == (b.y - a.y) * (c.x - a.x)
    }

    private fun side(a: Point, b: Point, point: Point): Double =
        (b.x - a.x) * (point.z - a.z) - (b.z - a.z) * (point.x - a.x)
}
