package org.rsmod.map.util

import kotlin.math.max
import kotlin.math.min
import org.rsmod.map.CoordGrid

/**
 * Distance checks include both footprints. Cross-plane pairs return false or Int.MAX_VALUE
 * instead of throwing; check levels before arithmetic.
 */
@JvmInline
public value class Bounds(public val packed: Long) {
    public val x: Int
        get() = ((packed shr X_BIT_OFFSET) and X_BIT_MASK).toInt()

    public val z: Int
        get() = ((packed shr Z_BIT_OFFSET) and Z_BIT_MASK).toInt()

    public val level: Int
        get() = ((packed shr LEVEL_BIT_OFFSET) and LEVEL_BIT_MASK).toInt()

    public val width: Int
        get() = ((packed shr WIDTH_BIT_OFFSET) and WIDTH_BIT_MASK).toInt()

    public val length: Int
        get() = ((packed shr LENGTH_BIT_OFFSET) and LENGTH_BIT_MASK).toInt()

    public val coords: CoordGrid
        get() = CoordGrid(x, z, level)

    public val fineCentreX: Double
        get() = x + (width / 2.0)

    public val fineCentreZ: Double
        get() = z + (length / 2.0)

    public constructor(
        x: Int,
        z: Int,
        level: Int,
        width: Int,
        length: Int,
    ) : this(pack(x = x, z = z, level = level, width = width, length = length))

    public constructor(
        x: Int,
        z: Int,
        level: Int,
        size: Int,
    ) : this(pack(x = x, z = z, level = level, width = size, length = size))

    public constructor(
        coords: CoordGrid,
        width: Int,
        length: Int,
    ) : this(x = coords.x, z = coords.z, level = coords.level, width = width, length = length)

    public constructor(
        coords: CoordGrid,
        size: Int = 1,
    ) : this(x = coords.x, z = coords.z, level = coords.level, width = size, length = size)

    /**
     * Cross-plane distance is Int.MAX_VALUE. Check levels before arithmetic; projectile timing
     * uses planar maxDistanceBetween instead.
     */
    public fun distanceTo(other: Bounds): Int {
        if (level != other.level) {
            return Int.MAX_VALUE
        }
        return maxDistanceBetween(this, other)
    }

    public fun isWithinDistance(other: Bounds, distance: Int): Boolean {
        if (level != other.level) {
            return false
        }
        return areWithinDistance(this, other, distance)
    }

    public operator fun iterator(): Iterator<CoordGrid> = BoundsIterator(this)

    public fun asSequence(): Sequence<CoordGrid> = iterator().asSequence()

    public fun shuffled(): Sequence<CoordGrid> = asSequence().shuffled()

    override fun toString(): String = "Bounds(base=$coords, width=$width, length=$length)"

    public companion object {
        public fun areWithinDistance(a: Bounds, b: Bounds, distance: Int): Boolean {
            if (distance < 0) {
                return false
            }
            val xDistance = xDistanceBetween(a, b)
            if (xDistance > distance) {
                return false
            }
            val zDistance = zDistanceBetween(a, b)
            return zDistance <= distance
        }

        public fun xDistanceBetween(a: Bounds, b: Bounds): Int {
            val minX = minX(a, b)
            val maxX = maxX(a, b)
            val expandedMinX = minX.x + minX.width - 1
            return max(0, maxX.x - expandedMinX)
        }

        public fun zDistanceBetween(a: Bounds, b: Bounds): Int {
            val minZ = minZ(a, b)
            val maxZ = maxZ(a, b)
            val expandedMinZ = minZ.z + minZ.length - 1
            return max(0, maxZ.z - expandedMinZ)
        }

        public fun minDistanceBetween(a: Bounds, b: Bounds): Int =
            min(xDistanceBetween(a, b), zDistanceBetween(a, b))

        public fun maxDistanceBetween(a: Bounds, b: Bounds): Int =
            max(xDistanceBetween(a, b), zDistanceBetween(a, b))

        public fun minX(a: Bounds, b: Bounds): Bounds = if (a.x < b.x) a else b

        public fun minZ(a: Bounds, b: Bounds): Bounds = if (a.z < b.z) a else b

        public fun maxX(a: Bounds, b: Bounds): Bounds = if (a.x > b.x) a else b

        public fun maxZ(a: Bounds, b: Bounds): Bounds = if (a.z > b.z) a else b

        private const val Z_BIT_COUNT: Int = 14
        private const val X_BIT_COUNT: Int = 14
        private const val LEVEL_BIT_COUNT: Int = 2
        private const val WIDTH_BIT_COUNT: Int = 16
        private const val LENGTH_BIT_COUNT: Int = 16

        private const val Z_BIT_OFFSET: Int = 0
        private const val X_BIT_OFFSET: Int = Z_BIT_OFFSET + Z_BIT_COUNT
        private const val LEVEL_BIT_OFFSET: Int = X_BIT_OFFSET + X_BIT_COUNT
        private const val WIDTH_BIT_OFFSET: Int = LEVEL_BIT_OFFSET + LEVEL_BIT_COUNT
        private const val LENGTH_BIT_OFFSET: Int = WIDTH_BIT_OFFSET + WIDTH_BIT_COUNT

        private const val Z_BIT_MASK: Long = (1L shl Z_BIT_COUNT) - 1
        private const val X_BIT_MASK: Long = (1L shl X_BIT_COUNT) - 1
        private const val LEVEL_BIT_MASK: Long = (1L shl LEVEL_BIT_COUNT) - 1
        private const val WIDTH_BIT_MASK: Long = (1L shl WIDTH_BIT_COUNT) - 1
        private const val LENGTH_BIT_MASK: Long = (1L shl LENGTH_BIT_COUNT) - 1

        private fun pack(x: Int, z: Int, level: Int, width: Int, length: Int): Long {
            require(x in 0..X_BIT_MASK) { "`x` value must be within range [0..$X_BIT_MASK]." }
            require(z in 0..Z_BIT_MASK) { "`z` value must be within range [0..$Z_BIT_MASK]." }
            require(level in 0..LEVEL_BIT_MASK) {
                "`level` value must be within range [0..$LEVEL_BIT_MASK]."
            }
            require(width in 0..WIDTH_BIT_MASK) {
                "`width` value must be within range [0..$WIDTH_BIT_MASK]."
            }
            require(length in 0..LENGTH_BIT_MASK) {
                "`length` value must be within range [0..$LENGTH_BIT_MASK]."
            }
            val packed =
                ((x.toLong() and X_BIT_MASK) shl X_BIT_OFFSET) or
                    ((z.toLong() and Z_BIT_MASK) shl Z_BIT_OFFSET) or
                    ((level.toLong() and LEVEL_BIT_MASK) shl LEVEL_BIT_OFFSET) or
                    ((width.toLong() and WIDTH_BIT_MASK) shl WIDTH_BIT_OFFSET) or
                    ((length.toLong() and LENGTH_BIT_MASK) shl LENGTH_BIT_OFFSET)
            return packed
        }
    }
}

private class BoundsIterator(private val bounds: Bounds) : Iterator<CoordGrid> {
    private var currentX = bounds.x
    private var currentZ = bounds.z

    override fun hasNext(): Boolean = currentZ < bounds.z + bounds.length

    override fun next(): CoordGrid {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        val coords = CoordGrid(currentX, currentZ, bounds.level)
        if (currentX + 1 < bounds.x + bounds.width) {
            currentX++
        } else {
            currentX = bounds.x
            currentZ++
        }
        return coords
    }
}
