package org.rsmod.routefinder.collision

public class CollisionFlagMap(public val flags: Array<IntArray?> = arrayOfNulls(TOTAL_ZONE_COUNT)) {
    public inline val defaultFlag: Int
        inline get() = DEFAULT_COLLISION_FLAG

    public operator fun get(absoluteX: Int, absoluteZ: Int, level: Int): Int {
        val zoneIndex = zoneIndex(absoluteX, absoluteZ, level)
        val tileIndex = tileIndex(absoluteX, absoluteZ)
        return flags[zoneIndex]?.get(tileIndex) ?: DEFAULT_COLLISION_FLAG
    }

    public operator fun set(absoluteX: Int, absoluteZ: Int, level: Int, mask: Int) {
        val tiles =
            flags[zoneIndex(absoluteX, absoluteZ, level)]
                ?: allocateIfAbsent(absoluteX, absoluteZ, level)
        tiles[tileIndex(absoluteX, absoluteZ)] = mask
    }

    public fun add(absoluteX: Int, absoluteZ: Int, level: Int, mask: Int) {
        val zoneIndex = zoneIndex(absoluteX, absoluteZ, level)
        val tileIndex = tileIndex(absoluteX, absoluteZ)
        val currentFlags = flags[zoneIndex]?.get(tileIndex) ?: 0
        this[absoluteX, absoluteZ, level] = currentFlags or mask
    }

    public fun remove(absoluteX: Int, absoluteZ: Int, level: Int, mask: Int) {
        val tiles = flags[zoneIndex(absoluteX, absoluteZ, level)] ?: return
        val tileIndex = tileIndex(absoluteX, absoluteZ)
        tiles[tileIndex] = tiles[tileIndex] and mask.inv()
    }

    /**
     * Allocates and initializes the collision flags for the zone that can be found in coordinates
     * ([absoluteX], [absoluteZ], [level]). If the zone has already been allocated then nothing will
     * be performed.
     *
     * The x and z-coordinate can range anywhere between 0-7 tiles in respect to the zone base
     * coordinates. For example, calling this method with the arguments (3202, 3204, [level]) will
     * have the same results as calling it with (3200, 3200, [level]).
     */
    public fun allocateIfAbsent(absoluteX: Int, absoluteZ: Int, level: Int): IntArray {
        val zoneIndex = zoneIndex(absoluteX, absoluteZ, level)
        val existingFlags = flags[zoneIndex]
        if (existingFlags != null) return existingFlags
        val tileFlags = IntArray(ZONE_TILE_COUNT)
        flags[zoneIndex] = tileFlags
        return tileFlags
    }

    public fun deallocateIfPresent(absoluteX: Int, absoluteZ: Int, level: Int) {
        flags[zoneIndex(absoluteX, absoluteZ, level)] = null
    }

    public fun isZoneAllocated(absoluteX: Int, absoluteZ: Int, level: Int): Boolean {
        return flags[zoneIndex(absoluteX, absoluteZ, level)] != null
    }

    public companion object {
        public const val DEFAULT_COLLISION_FLAG: Int = -1

        private const val TOTAL_ZONE_COUNT: Int = 2048 * 2048 * 4
        private const val ZONE_TILE_COUNT: Int = 8 * 8

        private fun tileIndex(x: Int, z: Int): Int = (x and 0x7) or ((z and 0x7) shl 3)

        private fun zoneIndex(x: Int, z: Int, level: Int): Int =
            ((x shr 3) and 0x7FF) or (((z shr 3) and 0x7FF) shl 11) or ((level and 0x3) shl 22)
    }
}
