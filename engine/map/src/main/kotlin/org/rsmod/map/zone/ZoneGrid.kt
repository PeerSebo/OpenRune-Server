package org.rsmod.map.zone

import org.rsmod.map.CoordGrid

/**
 * Zone-local coordinates: x/z each occupy three bits (0..7); convert from absolute coordinates
 * before packing.
 */
@JvmInline
public value class ZoneGrid(public val packed: Int) {
    public val x: Int
        get() = (packed shr X_BIT_OFFSET) and X_BIT_MASK

    public val z: Int
        get() = (packed shr Z_BIT_OFFSET) and Z_BIT_MASK

    public val level: Int
        get() = (packed shr LEVEL_BIT_OFFSET) and LEVEL_BIT_MASK

    public constructor(x: Int, z: Int, level: Int = 0) : this(pack(x, z, level))

    public fun translate(xOffset: Int, zOffset: Int, levelOffset: Int = 0): ZoneGrid =
        ZoneGrid(x = x + xOffset, z = z + zOffset, level = level + levelOffset)

    public fun translateX(offset: Int): ZoneGrid = translate(offset, 0, 0)

    public fun translateZ(offset: Int): ZoneGrid = translate(0, offset, 0)

    public fun translateLevel(offset: Int): ZoneGrid = translate(0, 0, offset)

    public operator fun minus(other: ZoneGrid): ZoneGrid {
        return translate(-other.x, -other.z, -other.level)
    }

    public operator fun plus(other: ZoneGrid): ZoneGrid {
        return translate(other.x, other.z, other.level)
    }

    public operator fun component1(): Int = x

    public operator fun component2(): Int = z

    public operator fun component3(): Int = level

    public override fun toString(): String = "ZoneGrid(x=$x, y=$z, level=$level)"

    public companion object {
        public val ZERO: ZoneGrid = ZoneGrid(0)

        public const val LENGTH: Int = 8

        public const val Z_BIT_COUNT: Int = 3
        public const val X_BIT_COUNT: Int = 3
        public const val LEVEL_BIT_COUNT: Int = 2

        public const val Z_BIT_OFFSET: Int = 0
        public const val X_BIT_OFFSET: Int = Z_BIT_OFFSET + Z_BIT_COUNT
        public const val LEVEL_BIT_OFFSET: Int = X_BIT_OFFSET + X_BIT_COUNT

        public const val Z_BIT_MASK: Int = (1 shl Z_BIT_COUNT) - 1
        public const val X_BIT_MASK: Int = (1 shl X_BIT_COUNT) - 1
        public const val LEVEL_BIT_MASK: Int = (1 shl LEVEL_BIT_COUNT) - 1

        public fun fromAbsolute(x: Int, z: Int, level: Int): ZoneGrid =
            ZoneGrid(x = x and X_BIT_MASK, z = z and Z_BIT_MASK, level = level and LEVEL_BIT_MASK)

        public fun from(coords: CoordGrid): ZoneGrid =
            fromAbsolute(x = coords.x, z = coords.z, level = coords.level)

        private fun pack(x: Int, z: Int, level: Int): Int {
            require(x in 0..X_BIT_MASK) { "`x` value must be within range [0..$X_BIT_MASK]." }
            require(z in 0..Z_BIT_MASK) { "`z` value must be within range [0..$Z_BIT_MASK]." }
            require(level in 0..LEVEL_BIT_MASK) {
                "`level` value must be within range [0..$LEVEL_BIT_MASK]."
            }
            return ((x and X_BIT_MASK) shl X_BIT_OFFSET) or
                ((z and Z_BIT_MASK) shl Z_BIT_OFFSET) or
                ((level and LEVEL_BIT_MASK) shl LEVEL_BIT_OFFSET)
        }
    }
}
