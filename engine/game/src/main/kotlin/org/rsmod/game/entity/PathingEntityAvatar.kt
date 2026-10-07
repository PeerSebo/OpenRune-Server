package org.rsmod.game.entity

import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds

public sealed class PathingEntityAvatar(
    public val size: Int,
    public var coords: CoordGrid = CoordGrid.ZERO,
    public var previousCoords: CoordGrid = CoordGrid.ZERO,
) {
    public var x: Int
        get() = coords.x
        set(value) {
            coords = coords.copy(x = value)
        }

    public var z: Int
        get() = coords.z
        set(value) {
            coords = coords.copy(z = value)
        }

    public var level: Int
        get() = coords.level
        set(value) {
            coords = coords.copy(level = value)
        }

    public fun bounds(): Bounds = Bounds(coords, size, size)

    /** Cross-plane distance returns Int.MAX_VALUE; check levels before doing arithmetic with it. */
    public fun distanceTo(target: Bounds): Int = bounds().distanceTo(target)

    public fun isWithinDistance(target: Bounds, distance: Int): Boolean =
        level == target.level && bounds().isWithinDistance(target, distance)
}
