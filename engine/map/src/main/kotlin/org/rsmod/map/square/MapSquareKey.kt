package org.rsmod.map.square

import org.rsmod.map.CoordGrid

/** A map-square key identifies the 64x64-tile square, independently of local tile coordinates. */
@JvmInline
public value class MapSquareKey(public val id: Int) {
    public val x: Int
        get() = (id shr X_BIT_OFFSET) and X_BIT_MASK

    public val z: Int
        get() = (id shr Z_BIT_OFFSET) and Z_BIT_MASK

    public constructor(x: Int, z: Int) : this(pack(x, z))

    public fun toCoords(level: Int): CoordGrid =
        CoordGrid(x = x * MapSquareGrid.LENGTH, z = z * MapSquareGrid.LENGTH, level = level)

    public operator fun component1(): Int = x

    public operator fun component2(): Int = z

    public override fun toString(): String = "MapSquareKey(id=$id, x=$x, z=$z)"

    public companion object {
        public const val Z_BIT_COUNT: Int = 8
        public const val X_BIT_COUNT: Int = 8

        public const val Z_BIT_OFFSET: Int = 0
        public const val X_BIT_OFFSET: Int = Z_BIT_OFFSET + Z_BIT_COUNT

        public const val Z_BIT_MASK: Int = (1 shl Z_BIT_COUNT) - 1
        public const val X_BIT_MASK: Int = (1 shl X_BIT_COUNT) - 1

        public fun fromAbsolute(x: Int, z: Int): MapSquareKey =
            MapSquareKey(x = x / MapSquareGrid.LENGTH, z = z / MapSquareGrid.LENGTH)

        public fun from(coords: CoordGrid): MapSquareKey = fromAbsolute(x = coords.x, z = coords.z)

        private fun pack(x: Int, z: Int): Int {
            require(x in 0..X_BIT_MASK) { "`x` value must be within range [0..$X_BIT_MASK]." }
            require(z in 0..Z_BIT_MASK) { "`z` value must be within range [0..$Z_BIT_MASK]." }
            return ((x and X_BIT_MASK) shl X_BIT_OFFSET) or ((z and Z_BIT_MASK) shl Z_BIT_OFFSET)
        }
    }
}
