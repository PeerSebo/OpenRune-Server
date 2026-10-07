package org.rsmod.map.util

import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/** Local zone index inside a map square: eight zones per side, each containing 8x8 tiles. */
@JvmInline
public value class LocalMapSquareZone(public val packed: Int) {
    public val x: Int
        get() = (packed shr X_BIT_OFFSET) and X_BIT_MASK

    public val z: Int
        get() = (packed shr Z_BIT_OFFSET) and Z_BIT_MASK

    public val level: Int
        get() = (packed shr LEVEL_BIT_OFFSET) and LEVEL_BIT_MASK

    public constructor(x: Int, z: Int, level: Int) : this(pack(x, z, level))

    public fun toCoords(baseX: Int, baseZ: Int, level: Int = this.level): CoordGrid =
        CoordGrid(x = baseX + (x * LENGTH), z = baseZ + (z * LENGTH), level = level)

    public override fun toString(): String = "LocalMapSquareZone(x=$x, z=$z, level=$level)"

    public companion object {
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

        public fun from(zone: ZoneKey): LocalMapSquareZone =
            LocalMapSquareZone(
                x = zone.x and X_BIT_MASK,
                z = zone.z and Z_BIT_MASK,
                level = zone.level and LEVEL_BIT_MASK,
            )

        private fun pack(x: Int, z: Int, level: Int): Int {
            require(x in 0..X_BIT_MASK) { "`x` value must be within range [0..$X_BIT_MASK]." }
            require(z in 0..Z_BIT_MASK) { "`z` value must be within range [0..$Z_BIT_MASK]." }
            require(level in 0..LEVEL_BIT_MASK) {
                "`level` value must be within range [0..$LEVEL_BIT_MASK]."
            }
            return (x shl X_BIT_OFFSET) or (z shl Z_BIT_OFFSET) or (level shl LEVEL_BIT_OFFSET)
        }
    }
}
